package net.jojoaddison.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.repository.DirectoryLinkRepository;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.service.dto.DirectoryReconciliationDTO;
import net.jojoaddison.service.dto.SiblingDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Applies a sibling stack's domain events to this service's directories.
 *
 * <h2>The decision this class exists to encode: projection, or record?</h2>
 *
 * <p>The question is whether hc-admin's {@code Patient} is a <b>projection</b> it may overwrite
 * freely from events, or a <b>record an administrator also edits</b> — because if it is the second,
 * a later event overwriting an edit is data loss, and the merge rule has to be stated rather than
 * assumed. It was answered by reading what the console actually does with the entity, not by
 * preference.
 *
 * <p><b>It is a record, and it is jointly owned.</b> The console's patient screens are reachable and
 * write real fields: an administrator sets the service plan, the hub, the clinical lead, the case
 * count, the sponsoring angel, the documents, and archives the row. None of those appear on any
 * event, and none could — hc-patient does not know this service's plans or hubs exist. At the same
 * time the console deliberately has <em>no</em> create button for a patient (removed 2026-08-28: a
 * patient registers on hc-patient and arrives here with an account already, so a record made here
 * counts as a patient on every tile and cannot sign in). So the two halves are cleanly split, and
 * the rule follows from the split rather than from a policy:
 *
 * <ul>
 *   <li><b>hc-patient owns existence and identity.</b> This service may create a {@code Patient}
 *       from an event and may never delete one — a deletion on the far side is a record with a
 *       history here, and archiving is an administrator's decision.</li>
 *   <li><b>hc-admin owns operations.</b> {@code plan}, {@code hub}, {@code clinicalLead},
 *       {@code angel}, {@code documents}, {@code caseCount}, {@code isArchived}, {@code profile} are
 *       never written by an event, at creation or afterwards. Not "not written yet" — no event
 *       carries them, so any value this class invented for them would be fiction that outranks an
 *       administrator.</li>
 *   <li><b>Two fields are shared, and each has a rule.</b> {@code joinedOn} is written once, at
 *       creation, from the first event seen. {@code lastActiveOn} moves forward only, so a replayed
 *       old event cannot wind it back. {@code status} may only go {@code PENDING → ACTIVE}, and only
 *       on an account event: an administrator who has suspended somebody must not have that undone
 *       by a redelivery, and no other transition is something the stream knows about.</li>
 * </ul>
 *
 * <p>The same reasoning would apply to {@code Professional}, and there is no code for it because
 * <b>a clinician's registration cannot be made into a valid {@code Professional}</b>: that entity
 * requires a {@code role} and a {@code licenceNumber}, {@code ValidatingMongoEventListener} enforces
 * both on save, and neither is on the wire — they are what credentialing collects. The registration
 * is recorded as a {@link DirectoryLink} and the local row is not invented. A fabricated licence
 * number in the directory that exists to verify licences would be the worst possible kind of
 * plausible.
 *
 * <h2>Idempotency, and why it is not keyed on {@code eventId}</h2>
 *
 * <p>Delivery is at least once and the consumer groups read from the earliest offset, so both a
 * redelivery and a full replay are normal rather than exceptional. Deduplicating on {@code eventId}
 * would need a ledger of every event ever seen, which grows without bound and answers nothing for
 * the reconciliation path, which has no event ids at all.
 *
 * <p>So the write is idempotent by <b>construction on the subject key</b>: every step is an upsert
 * or a field-scoped comparison, and applying the same event twice produces the same document. That
 * is the property the dashboard needs — "create a patient per message" would count a redelivery as a
 * second person on every tile — and it is why {@code apply} never inserts without first looking the
 * subject up atomically.
 */
@Service
public class DirectoryProjectionService {

    /**
     * What happened to a subject, for the log and for the tests.
     *
     * <p>{@code STALE} is not a failure. It is the watermark doing its job on a replay, and it is
     * the expected answer for most of a topic re-read.
     */
    public enum Outcome {
        /** A local record was created for a subject this service had never seen. */
        CREATED,
        /** The subject was already known; the link and any shared fields were brought up to date. */
        UPDATED,
        /** Older than what has already been applied to this subject. Nothing was written. */
        STALE,
        /** Not addressable — no subject key, or a source with no local record to keep. */
        IGNORED,
    }

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryProjectionService.class);

    private final MongoTemplate mongoTemplate;
    private final DirectoryLinkRepository directoryLinkRepository;
    private final PatientRepository patientRepository;

    public DirectoryProjectionService(
        MongoTemplate mongoTemplate,
        DirectoryLinkRepository directoryLinkRepository,
        PatientRepository patientRepository
    ) {
        this.mongoTemplate = mongoTemplate;
        this.directoryLinkRepository = directoryLinkRepository;
        this.patientRepository = patientRepository;
    }

    /**
     * Applies one event, and answers what it did.
     *
     * <p>Never throws for a message it cannot use. The caller is a Kafka consumer, and an exception
     * out of one stalls a partition — see {@code SiblingEventParser} for the full argument.
     */
    public Outcome apply(SiblingDomainEvent event) {
        if (event == null || event.subjectKey() == null || event.subjectKey().isBlank()) {
            return Outcome.IGNORED;
        }

        // The upsert and the read of the previous state are one operation, deliberately.
        // `returnNew(false)` hands back the document as it was *before* this call, or null when this
        // call is what created it — so the watermark being compared against is the one that was
        // stored, and no second reader can slip an insert in between. Two consumers, or a consumer
        // and the reconciliation endpoint, cannot both create a link for the same subject.
        DirectoryLink previous = upsertLink(event);
        boolean firstSighting = previous == null;

        if (!firstSighting && isStale(previous, event)) {
            LOG.debug(
                "Ignoring {} for {} — occurred {} which is before the applied watermark {}",
                event.type(),
                event.subjectKey(),
                event.occurredAt(),
                previous.getLastEventAt()
            );
            return Outcome.STALE;
        }

        String localId = ensureLocalRecord(event, firstSighting ? null : previous.getLocalId());
        boolean created = firstSighting && localId != null;

        recordEvent(event, localId);

        LOG.debug("Applied {} for {} from {}", event.type(), event.subjectKey(), event.source());
        if (event.source() == DirectorySource.HC_PROFESSIONAL) {
            // The link is kept and the local row deliberately is not; see the class javadoc.
            return firstSighting ? Outcome.IGNORED : Outcome.UPDATED;
        }
        return created ? Outcome.CREATED : Outcome.UPDATED;
    }

    /**
     * Re-derives every local record its link says should exist.
     *
     * <p><b>This is the backfill, and it is a second half rather than the whole of one.</b> The
     * first half is the consumer groups' {@code startOffset: earliest}: on their first run they read
     * the topics from the beginning, so every event still inside the broker's retention window is
     * applied through exactly the code path a live message takes. That is what makes the two the
     * same work, which is why they were built together.
     *
     * <p>What this adds is recovery from the case the offsets cannot help with — a link that exists
     * and whose local record does not, because the database was restored from a backup older than
     * the offsets, or because somebody deleted the row. Re-reading the topic would not fix that: the
     * group's offsets are already past those messages, and moving them is an operation on the broker
     * rather than on this service.
     *
     * <p>What neither half reaches is stated plainly in the backlog: an account registered before
     * these publishers existed, or older than the topic's retention, is in no message and in no link.
     * Recovering those needs a read across to the sibling, and the only whole-directory read
     * hc-patient offers is {@code GET /api/profiles} — the clinical record, guarded by
     * {@code PatientScope}. Consuming it would copy into this service precisely the data the event
     * stream is designed never to carry, which is a worse outcome than an incomplete directory.
     */
    public DirectoryReconciliationDTO reconcile() {
        List<DirectoryLink> links = directoryLinkRepository.findBySource(DirectorySource.HC_PATIENT);
        int created = 0;
        int alreadyPresent = 0;

        for (DirectoryLink link : links) {
            boolean present = link.getLocalId() != null && patientRepository.findById(link.getLocalId()).isPresent();
            if (present) {
                alreadyPresent++;
                continue;
            }
            Patient patient = createPatient(
                link.getState() != null && !link.getState().equals("AccountCreated"),
                link.getFirstSeenAt() == null ? Instant.now() : link.getFirstSeenAt()
            );
            mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(link.getId())),
                new Update().set("local_id", patient.getId()),
                DirectoryLink.class
            );
            created++;
        }

        LOG.info("Directory reconciliation examined {} links, created {}, left {} alone", links.size(), created, alreadyPresent);
        return new DirectoryReconciliationDTO(links.size(), created, alreadyPresent);
    }

    /**
     * Inserts the subject's link if this service has never seen it, and answers the state it was in
     * before this call — null when this call created it.
     *
     * <p>Only the identity fields are written here. Everything the event says goes on in
     * {@link #recordEvent}, after the watermark has had its say, so a stale message cannot roll a
     * login or a state backwards on its way to being ignored.
     */
    private DirectoryLink upsertLink(SiblingDomainEvent event) {
        Query query = Query.query(Criteria.where("source").is(event.source().name()).and("external_key").is(event.subjectKey()));
        Update onInsert = new Update()
            .setOnInsert("source", event.source())
            .setOnInsert("external_key", event.subjectKey())
            .setOnInsert("first_seen_at", event.occurredAt());

        return mongoTemplate.findAndModify(
            query,
            onInsert,
            FindAndModifyOptions.options().upsert(true).returnNew(false),
            DirectoryLink.class
        );
    }

    /** Strictly older than what has already been applied. Equal is not stale — that is a redelivery. */
    private boolean isStale(DirectoryLink previous, SiblingDomainEvent event) {
        return previous.getLastEventAt() != null && event.occurredAt().isBefore(previous.getLastEventAt());
    }

    /**
     * Writes what this event said about the subject.
     *
     * <p>Field by field rather than by saving a whole document: the reconciliation endpoint and a
     * second partition's consumer may be writing the same link, and a whole-document save would
     * carry back whatever this thread happened to read.
     *
     * <p>Identity fields are only ever set when the event carries them, never cleared. hc-patient's
     * stream is explicit that a {@code patientId} does not exist until onboarding step 1, so a later
     * account event with no patient id must not erase the one {@code OnboardingStarted} published.
     */
    private void recordEvent(SiblingDomainEvent event, String localId) {
        Update update = new Update()
            .set("last_event_at", event.occurredAt())
            .set("last_event_type", event.type())
            .set("state", event.state());

        setIfPresent(update, "last_event_id", event.eventId());
        setIfPresent(update, "email", event.email());
        setIfPresent(update, "login", event.login());
        setIfPresent(update, "external_id", event.externalId());
        setIfPresent(update, "local_id", localId);

        mongoTemplate.updateFirst(
            Query.query(Criteria.where("source").is(event.source().name()).and("external_key").is(event.subjectKey())),
            update,
            DirectoryLink.class
        );
    }

    private void setIfPresent(Update update, String field, String value) {
        if (value != null && !value.isBlank()) {
            update.set(field, value);
        }
    }

    /**
     * The local row for this subject, created or brought up to date under the merge rule.
     *
     * @return the local document id, or null when this source keeps no local row.
     */
    private String ensureLocalRecord(SiblingDomainEvent event, String knownLocalId) {
        if (event.source() != DirectorySource.HC_PATIENT) {
            return null;
        }
        Patient existing = knownLocalId == null ? null : patientRepository.findById(knownLocalId).orElse(null);
        if (existing == null) {
            Patient patient = createPatient(event.activated(), event.occurredAt());
            LOG.info("Directory learned a patient from {}: {} -> {}", event.source(), event.subjectKey(), patient.getId());
            return patient.getId();
        }
        merge(existing, event);
        return knownLocalId;
    }

    /**
     * A patient this service has just learned exists.
     *
     * <p>Two required fields and nothing else. {@code caseCount} is seeded at zero because the
     * console renders it as a number and an absent one reads as unknown rather than as none;
     * everything else is left null for an administrator to fill in, including the {@code Profile} —
     * which cannot be created at all, since it requires a name, a date of birth, a phone number and
     * a document number, and the patient stream carries none of them by design.
     */
    private Patient createPatient(boolean activated, Instant at) {
        LocalDate day = LocalDate.ofInstant(at, ZoneOffset.UTC);
        return patientRepository.save(
            new Patient().status(activated ? AccountStatus.ACTIVE : AccountStatus.PENDING).joinedOn(day).lastActiveOn(day).caseCount(0)
        );
    }

    /** The merge rule from the class javadoc, and nothing beyond it. */
    private void merge(Patient patient, SiblingDomainEvent event) {
        LocalDate day = LocalDate.ofInstant(event.occurredAt(), ZoneOffset.UTC);
        boolean changed = false;

        if (patient.getLastActiveOn() == null || patient.getLastActiveOn().isBefore(day)) {
            patient.setLastActiveOn(day);
            changed = true;
        }
        // PENDING to ACTIVE only. An administrator's SUSPENDED, ON_LEAVE or UNDER_REVIEW is a
        // decision taken here about a person this stream knows nothing about, and a replay of the
        // activation that preceded it must not read as a reinstatement.
        if (event.activated() && patient.getStatus() == AccountStatus.PENDING) {
            patient.setStatus(AccountStatus.ACTIVE);
            changed = true;
        }

        if (changed) {
            patientRepository.save(patient);
        }
    }
}
