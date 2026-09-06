package net.jojoaddison.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.broker.OutboundEventPublisher;
import net.jojoaddison.broker.VerificationEvent;
import net.jojoaddison.config.Constants;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.ProfessionalVerification;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ProfessionalVerificationRepository;
import net.jojoaddison.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Records verification decisions, and is the only thing that writes
 * {@code Professional.verification}.
 *
 * <p>Two writes, one act. The history row is the record; the field on {@code Professional} is a
 * projection of its head, kept so the directory badge and the dashboard's pending count stay a
 * single field read rather than an aggregation on every row of a list.
 *
 * <p><strong>The order matters and is deliberate.</strong> The history row is saved first. If the
 * projection then fails, the console shows a stale badge over a correct history — recoverable, and
 * visible the moment anyone opens the record. The other order loses the decision entirely while
 * showing a badge asserting it, which is the failure that cannot be found later. MongoDB is a single
 * node here with no transaction to make this atomic (see {@code deploy/TODO.md} §4), so the choice
 * is which half survives, not whether to have one.
 */
@Service
public class ProfessionalVerificationService {

    private static final Logger LOG = LoggerFactory.getLogger(ProfessionalVerificationService.class);

    /** The domain topic other services subscribe to. Bound to `professional-verification`. */
    private static final String VERIFICATION_BINDING = "verification-out-0";

    /** The browser fan-out, so an open console updates without a reload. Bound to `sse-topic`. */
    private static final String SSE_BINDING = "binding-out-0";

    private final ProfessionalVerificationRepository verificationRepository;

    private final ProfessionalRepository professionalRepository;

    /** Injected so a test can record a verification at a known instant rather than at "now". */
    private final Clock clock;

    private final OutboundEventPublisher eventPublisher;

    private final ObjectMapper objectMapper;

    public ProfessionalVerificationService(
        ProfessionalVerificationRepository verificationRepository,
        ProfessionalRepository professionalRepository,
        Clock clock,
        OutboundEventPublisher eventPublisher,
        ObjectMapper objectMapper
    ) {
        this.verificationRepository = verificationRepository;
        this.professionalRepository = professionalRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Writes one decision and projects it onto the professional.
     *
     * <p>{@code recordedAt} and {@code recordedBy} are overwritten from the clock and the token,
     * whatever arrived on the wire — the same rule {@code AuditingEntityCallback} applies to the
     * audit fields, and for the same reason: a history whose times and authors the caller supplies
     * is not evidence of anything.
     *
     * @param verification the decision, with its {@code professional} reference already resolved
     * @return the saved row
     */
    public ProfessionalVerification record(ProfessionalVerification verification) {
        // Truncated to milliseconds, which is the resolution MongoDB stores. Without this the
        // instance in memory carries microseconds the database will not return, so a caller
        // comparing the response to a later read sees two different timestamps for one row.
        verification.setRecordedAt(Instant.now(clock).truncatedTo(ChronoUnit.MILLIS));
        verification.setRecordedBy(SecurityUtils.getCurrentUserLogin().orElse(Constants.SYSTEM));

        ProfessionalVerification saved = verificationRepository.save(verification);
        project(saved);
        announce(saved);
        return saved;
    }

    /**
     * Publishes the decision, and never fails the decision because of it.
     *
     * <p>Last of the three steps deliberately. The row and the projection are what the console reads;
     * the event is a notification to other services and to any browser holding the SSE stream open.
     * Unwinding a recorded verification because a broker was briefly unreachable would lose the one
     * thing that cannot be reconstructed, to protect the one thing that can — a consumer that missed
     * an event can re-read the history, which is what a history is for.
     *
     * <p>The warning matters more here than usual. <b>A missing broker is silent</b>: the app starts,
     * serves and reports healthy while everything produced goes nowhere. That log line lives on
     * {@link OutboundEventPublisher} now, along with the reason neither of these two sends touches the
     * request thread any more — an unreachable broker made the first one cost sixty seconds, and both
     * of them went through the same lock (backlog item 39a).
     *
     * <p>Order between the two is preserved because the publisher keeps one thread, which is what makes
     * two decisions about one professional arrive on {@code professional-verification} in the order
     * they were taken.
     */
    private void announce(ProfessionalVerification saved) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(eventFor(saved));
        } catch (JsonProcessingException | RuntimeException e) {
            LOG.warn("Verification {} was recorded but its event could not be serialised", saved.getId(), e);
            return;
        }

        String subject = "Verification " + saved.getId();
        eventPublisher.publish(VERIFICATION_BINDING, payload, subject);
        // Relayed to the SSE fan-out as well, so an open console updates without a reload. A second
        // send rather than a shared binding: the domain topic and the browser channel have different
        // audiences and one should not be able to break the other.
        eventPublisher.publish(SSE_BINDING, payload, subject);
    }

    /**
     * Converts a saved row into the wire event.
     *
     * <p>Here rather than as a factory on {@link VerificationEvent}, because {@code ..broker..} is
     * not a layer {@code TechnicalStructureTest} permits to reach {@code ..domain..}. Null-safe on
     * the professional reference, which a row can outlive.
     */
    private VerificationEvent eventFor(ProfessionalVerification verification) {
        Professional professional = verification.getProfessional();
        return new VerificationEvent(
            verification.getId(),
            professional == null ? null : professional.getId(),
            professional == null ? null : professional.getLicenceNumber(),
            verification.getStatus() == null ? null : verification.getStatus().name(),
            verification.getRecordedAt(),
            verification.getRecordedBy()
        );
    }

    /** One professional's decisions, newest first. */
    public List<ProfessionalVerification> historyFor(String professionalId) {
        return verificationRepository.findByProfessionalIdOrderByRecordedAtDesc(professionalId);
    }

    /**
     * How many professionals reached {@code VERIFIED} since {@code since}.
     *
     * <p>The dashboard's "+N verified this week", and the reason this collection exists rather than
     * a date field on {@code Professional}: a count over a window needs the decisions in that
     * window, and a current-state field knows only the latest one.
     */
    public long verifiedSince(Instant since) {
        return verificationRepository.countByStatusAndRecordedAtGreaterThanEqual(VerificationStatus.VERIFIED, since);
    }

    /**
     * Copies the decision onto the professional it is about.
     *
     * <p>Guarded rather than assumed: the reference is a {@code @DBRef} and a row can outlive the
     * professional it names. A missing professional is logged and skipped — the history row still
     * stands, which is the half worth keeping.
     */
    private void project(ProfessionalVerification verification) {
        Professional referenced = verification.getProfessional();
        if (referenced == null || referenced.getId() == null) {
            LOG.warn("Verification {} names no professional; nothing to project", verification.getId());
            return;
        }

        Optional<Professional> stored = professionalRepository.findById(referenced.getId());
        if (stored.isEmpty()) {
            LOG.warn("Verification {} names professional {} which no longer exists", verification.getId(), referenced.getId());
            return;
        }

        Professional professional = stored.orElseThrow();
        professional.setVerification(verification.getStatus());
        professionalRepository.save(professional);
    }
}
