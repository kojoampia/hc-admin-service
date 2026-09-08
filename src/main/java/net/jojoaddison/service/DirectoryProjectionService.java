package net.jojoaddison.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.repository.DirectoryLinkRepository;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.repository.ServicePlanRepository;
import net.jojoaddison.service.dto.DirectoryReconciliationDTO;
import net.jojoaddison.service.dto.ProfileStatusEvent;
import net.jojoaddison.service.dto.SiblingDomainEvent;
import net.jojoaddison.service.dto.SiblingDomainEvent.Disposition;
import net.jojoaddison.service.dto.SiblingDomainEvent.PlanChoice;
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
 * <h2>Not every subject on a stream is one of this service's people</h2>
 *
 * <p><b>Which events may open a record is decided by the event type, and nothing here defaults.</b>
 * Every event arrives carrying a {@link Disposition}, resolved in {@code SiblingEventParser} from the
 * producer's own published vocabulary; a type neither producer has published never reaches this
 * class at all. The rule this encodes:
 *
 * <ul>
 *   <li>{@link Disposition#CREATE} — {@code AccountCreated} for a registration, and
 *       {@code OnboardingStarted}. These may bring a subject into existence here.</li>
 *   <li>{@link Disposition#UPDATE_ONLY} — everything else on the patient stream. It updates a
 *       subject this service already knows and, for one this service does not, <b>writes
 *       nothing at all</b>.</li>
 *   <li>{@link Disposition#LINK_ONLY} — a care angel, and every clinician. A link, deliberately no
 *       local record.</li>
 * </ul>
 *
 * <p><b>{@code PlanChosen} is an {@link Disposition#UPDATE_ONLY} of the strictest kind</b>, and the
 * reason is worth having beside the rule rather than only in the parser. hc-patient writes a
 * {@code Membership} only for a patient whose {@code AccountCreated} and {@code OnboardingStarted}
 * were published earlier on the same key, so a plan choice for a subject this service has never seen
 * does not mean a new person — it means those earlier events were missed. Opening a record from a
 * tier code would put a patient on every tile whose only content is which plan they picked.
 *
 * <p>Two of those exist because of what happened without them, and both are worth keeping named.
 * <b>A care-angel nomination publishes {@code AccountCreated} keyed on the angel's own address</b>,
 * so a creation path that read only the type made every nomination an {@code ACTIVE} patient — on
 * the count, in the directory, on the account-mix chart and on the weekly-joins tile. And
 * <b>{@code DeletionRequestChanged} with {@code change=COMPLETED} is published after the profile has
 * been erased</b>: for a subject with no link, the event whose entire meaning is "erase this person"
 * was what made this service start storing their address. It now marks a link it already has and
 * opens nothing.
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
 *
 * <p><b>Under concurrency it needs the index as well as the atomic write, and that claim was too
 * strong until 2026-09-05.</b> {@code findAndModify(upsert)} is atomic per document; it does not stop
 * two upserts that match nothing from both inserting, for which MongoDB documents a unique index as
 * the requirement. {@link net.jojoaddison.config.DirectoryLinkIndexes} now creates one. The
 * <em>record</em> is claimed by a compare-and-set for the same reason — see {@code createAndClaim},
 * which also names the one window neither closes, the two writes that have no transaction around
 * them.
 *
 * <h2>The reconciliation runs the same rule, and it used to run a different one</h2>
 *
 * <p>{@link #reconcile()} rebuilds a local record for a link that has lost one. Its javadoc has
 * always said it goes through the same idempotent path a live message does; it did not. It re-derived
 * "is this account activated" as <em>any state other than the literal {@code AccountCreated}</em>, so
 * a link last seen in {@code OnboardingStarted}, {@code OnboardingStepCompleted},
 * {@code CareDelegationChanged}, {@code DeletionRequestChanged} or any type it did not know rebuilt
 * as {@code ACTIVE} — while the consumer, on those same events, says the account is not activated at
 * all. Two derivations of one rule, disagreeing, and the test that should have caught it hard-coded
 * {@code state = "AccountActivated"} in its fixture.
 *
 * <p>It no longer derives anything: {@code DirectoryLink.activated} is written by the consumer as it
 * applies each event, monotonically, and the reconciliation <b>reads</b> it. Both paths then go
 * through one {@code createAndClaim}. That is as close to "the same path" as the two can honestly be
 * — one is applying an event and the other has none — and it is what the sentence now claims.
 *
 * <h2>The subject key is never logged, at any level</h2>
 *
 * <p><b>{@code subjectKey} is a patient's email address</b> — {@link SiblingDomainEvent}'s own
 * javadoc says so — and <b>every</b> statement in this class that names a subject goes through
 * {@link LogPseudonym#subject(String)}, which is where the argument for a digest is kept. A
 * clinician's key is an {@code accountId} rather than an address and goes through the same call,
 * because a per-source exception is a rule nobody can apply.
 *
 * <p><b>This sentence used to carry a count and the count was wrong.</b> It said five, then six, and
 * was six when this class had ten such statements — item 47 added three and item 48 two more, and
 * neither moved the number, because nothing fails when a figure in a comment goes stale. It is
 * "every" now, which is the claim that was always meant and the only one that stays true; what
 * enforces it is {@code LogPseudonymTest}, which sweeps the source for an unwrapped key and pins its
 * own patterns so it cannot pass vacuously.
 *
 * <p><b>Every one, not just the ones that are live.</b> Most are {@code debug} and production
 * runs this package at {@code INFO}, so it is tempting to leave them and call them unreachable.
 * They are not unreachable: JHipster ships {@code POST /management/loggers/{name}}, the console has
 * a screen that drives it, and {@code administration.cy.ts} exercises exactly that. So the level is
 * one authenticated request away from {@code DEBUG}, with no deploy and no restart — and the person
 * raising it is by definition someone debugging why a subject did not appear, which is the moment
 * every one of these fires, for every subject on the topic rather than only the new ones. A rule
 * that holds at one level and quietly fails at another is not a rule; the guard in
 * {@code DirectoryEventConsumptionIT} therefore asserts it with this package's logger turned all
 * the way up. Backlog item 43.
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
        /**
         * A subject this service had never seen is now linked, and <b>deliberately has no local
         * record</b> — a care angel, or a clinician. Distinct from {@link #IGNORED}, which wrote
         * nothing at all: something did happen here, and the link is what stops a later event on the
         * same subject mistaking them for a patient.
         */
        LINKED,
        /** The subject was already known; the link and any shared fields were brought up to date. */
        UPDATED,
        /** Older than what has already been applied to this subject. Nothing was written. */
        STALE,
        /**
         * Nothing was written. No subject key, or an {@link Disposition#UPDATE_ONLY} event for a
         * subject this service has never seen — which is a fact about somebody whose arrival was
         * missed, and not a licence to invent them.
         */
        IGNORED,
    }

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryProjectionService.class);

    private final MongoTemplate mongoTemplate;
    private final DirectoryLinkRepository directoryLinkRepository;
    private final PatientRepository patientRepository;

    /**
     * Read for one purpose only: to say out loud when a plan choice names a tier this catalogue does
     * not hold. Nothing here ever writes a {@code ServicePlan} — see {@link #announcePlanChoice}.
     */
    private final ServicePlanRepository servicePlanRepository;

    public DirectoryProjectionService(
        MongoTemplate mongoTemplate,
        DirectoryLinkRepository directoryLinkRepository,
        PatientRepository patientRepository,
        ServicePlanRepository servicePlanRepository
    ) {
        this.mongoTemplate = mongoTemplate;
        this.directoryLinkRepository = directoryLinkRepository;
        this.patientRepository = patientRepository;
        this.servicePlanRepository = servicePlanRepository;
    }

    /**
     * Applies one event, and answers what it did.
     *
     * <p>Never throws for a message it cannot use. The caller is a Kafka consumer, and an exception
     * out of one stalls a partition — see {@code SiblingEventParser} for the full argument.
     */
    public Outcome apply(SiblingDomainEvent event) {
        if (event == null || event.subjectKey() == null || event.subjectKey().isBlank()) {
            // Deliberately silent, and the only one of the three early returns that is.
            // SiblingEventParser refuses a keyless frame on both topics and WARNs as it does, so
            // nothing arriving from a consumer reaches this line — it guards a caller other than
            // the consumer, and a second warning would be the same fact twice. It also cannot go
            // through announce(): that method's first act is to digest event.subjectKey(), which is
            // the value this branch exists because it has not got.
            return Outcome.IGNORED;
        }

        DirectoryLink previous;
        if (event.disposition() == Disposition.UPDATE_ONLY) {
            // An UPDATE_ONLY event may not open a record, so the link is READ rather than upserted:
            // an erasure, a delegation change or an onboarding step for somebody this service has
            // never seen is a fact about a subject whose arrival was missed, and a link written from
            // it would be a row with an address on it that nothing can ever complete.
            //
            // Reading where the other branch upserts is not a race worth closing. Every event about
            // one subject carries the same partition key, so the broker delivers them in order on
            // one thread; the only way a CREATE and an UPDATE_ONLY for one subject run concurrently
            // is a rebalance mid-partition, where the loser is redelivered anyway.
            previous = directoryLinkRepository.findSubject(event.source(), event.subjectKey()).orElse(null);
            if (previous == null) {
                LOG.debug(
                    "Ignoring {} for {} — this service has no link for that subject and {} does not open one",
                    event.type(),
                    LogPseudonym.subject(event.subjectKey()),
                    event.type()
                );
                return Outcome.IGNORED;
            }
        } else {
            // The upsert and the read of the previous state are one operation, deliberately.
            // `returnNew(false)` hands back the document as it was *before* this call, or null when
            // this call is what created it — so the watermark being compared against is the one that
            // was stored, and no second reader can slip an insert in between.
            previous = upsertLink(event);
        }
        boolean firstSighting = previous == null;

        if (!firstSighting && isStale(previous, event)) {
            LOG.debug(
                "Ignoring {} for {} — occurred {} which is before the applied watermark {}",
                event.type(),
                LogPseudonym.subject(event.subjectKey()),
                event.occurredAt(),
                previous.getLastEventAt()
            );
            return Outcome.STALE;
        }

        boolean hadRecord = !firstSighting && previous.getLocalId() != null;
        String localId = ensureLocalRecord(event, firstSighting ? null : previous.getLocalId());

        recordEvent(event, localId, joinsThePhases(event.source(), firstSighting ? null : previous, DirectoryLink::getProfileEventAt));

        // After the write and only when one was made — a stale or unknown-subject plan choice returns
        // above, so this line means "the console will show this row" rather than "a frame arrived".
        //
        // The membershipId condition mirrors `recordEvent`'s exactly, and must keep doing so. That
        // method writes the plan group only for a frame carrying a usable membershipId, because the
        // group is written wholesale and keyed on it; announcing on the weaker `planChoice() != null`
        // meant a malformed frame logged "the choice is stored and shown" about a write that was
        // refused a few lines earlier. Unreachable from either of hc-patient's clients today, which
        // is exactly why it would have gone unnoticed — the log was the only thing that would ever
        // have said so, and it was saying the opposite.
        PlanChoice announced = event.planChoice();
        if (announced != null && announced.membershipId() != null && !announced.membershipId().isBlank()) {
            announcePlanChoice(event);
        }

        // A first sighting that keeps no local record is still something happening — a care angel or
        // a clinician is now known, and the link is what stops the next event on them being read as
        // a patient arriving.
        Outcome outcome = localId != null && !hadRecord ? Outcome.CREATED : firstSighting ? Outcome.LINKED : Outcome.UPDATED;
        announce(event, outcome, localId);
        if (firstSighting && event.source() == DirectorySource.HC_PROFESSIONAL) {
            warnIfTheTwoPhasesNeverJoin(event.subjectKey());
        }
        return outcome;
    }

    /**
     * Applies one <b>phase-2</b> profile status, and answers what it did.
     *
     * <h2>The two phases join on {@code accountId}, and the join is this upsert</h2>
     *
     * <p>Backlog item 47 accepts a clinician in two phases: an account first, a profile afterwards.
     * Both address the same document — {@code (HC_PROFESSIONAL, accountId)} — so pairing them is not
     * a query anybody has to write correctly, it is the write itself. Two consequences follow, and
     * they are the two properties item 47 asks for by name:
     *
     * <ul>
     *   <li><b>Either phase may arrive first, or alone.</b> A profile status for an account no
     *       registration has been seen for opens the link and writes the profile half of it. Nothing
     *       is dropped and no name is invented — {@code login} and {@code email} stay absent, and the
     *       console renders that row as "identity not on file, and here is what its profile says",
     *       which is exactly true.</li>
     *   <li><b>They cannot disagree, only be incomplete.</b> The two halves are disjoint sets of
     *       fields on one document: phase 1 writes identity and activation, phase 2 writes the seven
     *       profile fields, and neither touches the other's. There is no merge rule to get wrong,
     *       which is deliberate — {@code Patient}'s merge rule is the hardest thing in this class and
     *       nothing here needed a second one.</li>
     * </ul>
     *
     * <h2>Its own watermark, compared against nothing else</h2>
     *
     * <p>{@code profile_event_at}, not {@code last_event_at}. The two phases are published by two
     * applications with two clocks onto two topics, so their {@code occurredAt} sequences are
     * independent; comparing a profile event against a registration's watermark would discard it
     * whenever the gateway ran ahead — silently, and looking exactly like hc-professional not having
     * published. Within phase 2 the watermark does its usual job: a replayed older status does not
     * wind {@code isComplete} back.
     *
     * <h2>What it may not do</h2>
     *
     * <p>It may not create a {@code Professional}, at any level of completeness. Neither phase carries
     * a {@code role} or a {@code licenceNumber} — the contract deliberately does not — so
     * {@code local_id} stays null and the clinician stays in the awaiting-a-record panel with real
     * information on the row instead of no row at all. A fabricated licence number in the directory
     * that exists to verify licences is the worst available outcome and is what items 27(a), 33, 46
     * and 47 each refuse in turn.
     *
     * <p>Never throws for a message it cannot use, for the reason {@code apply} gives.
     */
    public Outcome applyProfileStatus(ProfileStatusEvent event) {
        if (event == null || event.accountId() == null || event.accountId().isBlank()) {
            // Silent for the same reason apply's first branch is: SiblingEventParser refuses a
            // keyless profile frame and warns as it does, so nothing from a consumer reaches here.
            return Outcome.IGNORED;
        }

        DirectoryLink previous = upsertProfileLink(event);
        boolean firstSighting = previous == null;

        if (!firstSighting && previous.getProfileEventAt() != null && event.occurredAt().isBefore(previous.getProfileEventAt())) {
            LOG.debug(
                "Ignoring the profile status {} for {} — occurred {} which is before the applied profile watermark {}",
                event.type(),
                LogPseudonym.subject(event.accountId()),
                event.occurredAt(),
                previous.getProfileEventAt()
            );
            return Outcome.STALE;
        }

        recordProfileStatus(event, joinsThePhases(DirectorySource.HC_PROFESSIONAL, previous, DirectoryLink::getLastEventAt));

        // LINKED rather than CREATED, and the distinction is the one Outcome already draws: a local
        // record was not created, because for a clinician none ever is. UPDATED when the account was
        // already known, whether from phase 1, from an earlier phase 2, or from both.
        Outcome outcome = firstSighting ? Outcome.LINKED : Outcome.UPDATED;
        announceProfileStatus(event, outcome, firstSighting);
        if (firstSighting) {
            warnIfTheTwoPhasesNeverJoin(event.accountId());
        }
        return outcome;
    }

    /**
     * Says what a profile status did, at the level the fact deserves.
     *
     * <p>{@code INFO} for a first sighting, {@code debug} for an update, which is
     * {@link #announce}'s split and is here for the same reason: a replay re-reads a whole topic and
     * one {@code INFO} per frame buries the lines that say something happened.
     *
     * <p><b>It names which phase this was, in words.</b> The reported failure behind item 46 was an
     * operator unable to tell "the event never arrived" from "the event arrived and was deliberately
     * stored as a link", and phase 2 adds a third state to that question — the account is known and
     * its profile is not, or the reverse. The line says which, so
     * {@code grep 'Directory learned'} still finds every kind and a reader chasing a missing clinician
     * can see how far the two phases got.
     */
    private void announceProfileStatus(ProfileStatusEvent event, Outcome outcome, boolean firstSighting) {
        String subject = LogPseudonym.subject(event.accountId());
        if (outcome == Outcome.LINKED) {
            LOG.info(
                "Directory learned a PROFILE from HC_PROFESSIONAL ({}): {} — phase 2 arrived before phase 1, so this account " +
                "has a profile status and no registration yet, and no local record is created for a clinician either way",
                event.type(),
                subject
            );
        } else {
            LOG.debug(
                "Applied a profile status {} for {} from HC_PROFESSIONAL — {} (first sighting: {})",
                event.type(),
                subject,
                outcome,
                firstSighting
            );
        }
    }

    /**
     * <b>The one failure that looks correct on both sides.</b>
     *
     * <p>If hc-professional's two publishers ever key their phases on different identifiers — the
     * gateway's {@code User.id} against a profile-local id, say — then every event of both kinds is
     * published, delivered, parsed and stored, both consumer groups sit at lag zero, nothing is
     * dead-lettered, and this service fills up with rows that can never be paired: half of them named
     * with no profile, half of them with a profile and no name. Item 47 names it as the failure to
     * guard, and it is invisible to every check either product can run on itself.
     *
     * <p>So it is checked where it becomes detectable — a first sighting on the professional source,
     * which is the moment a new unpaired row is created — and the condition is deliberately narrow:
     * <b>no account anywhere has ever had both phases, and there is at least one of each kind waiting
     * on its own.</b> Either half alone is the ordinary early state of a stack that has consumed one
     * phase and not the other, and warning about that would be an alarm that is always on.
     *
     * <p>It is not perfectly precise and does not need to be. There is a window — the first profile to
     * arrive out of order, before any account has both — in which it fires and nothing is wrong; that
     * window closes the moment one account pairs, and the line says what to compare rather than
     * asserting a fault. A guard that occasionally asks a question beats one that is silent through
     * the failure it exists for.
     *
     * <p>Two counts on a first sighting only, so this costs nothing on the ordinary path.
     *
     * <h2>What suppresses it is a join this service <em>observed</em>, and that changed on 2026-09-08</h2>
     *
     * <p>It used to be suppressed by any link holding both watermarks, which the {@code test} fixture
     * seeds — deliberately, since the console's joined row is otherwise unreachable outside production.
     * So the guard was silent by construction on {@code quality/}, on {@code deploy/e2e/} and under
     * {@code ng serve}: on every machine short of production, which is where a key mismatch would
     * first show up and where this warning was the whole point. The suppressor is now
     * {@link DirectoryLink#phasesJoinedAt}, which only the two write paths above set and which no
     * seeded document carries — pinned by {@code DevelopmentDataInitializerTest}, so re-silencing the
     * guard from a fixture is a failing test.
     *
     * <p><b>It does not cry wolf on a healthy empty system</b>, and there are two of those to keep
     * apart. A stack that has consumed nothing never reaches this method at all — it runs on a first
     * sighting, and there are none. A stack consuming one phase and not the other reaches it and stays
     * silent, because the warning needs at least one row of each kind waiting alone. What is left is
     * the window this method has always had, which is unchanged: the first profile to arrive out of
     * order, before any account has paired. It closes on the first pairing, permanently, and the line
     * asks a question rather than asserting a fault.
     */
    private void warnIfTheTwoPhasesNeverJoin(String accountId) {
        Criteria professional = Criteria.where("source").is(DirectorySource.HC_PROFESSIONAL.name());
        long joined = mongoTemplate.count(
            Query.query(new Criteria().andOperator(professional, Criteria.where("phases_joined_at").ne(null))),
            DirectoryLink.class
        );
        if (joined > 0) {
            return;
        }
        long registrationOnly = mongoTemplate.count(
            Query.query(new Criteria().andOperator(professional, Criteria.where("last_event_at").ne(null), profileUnseen())),
            DirectoryLink.class
        );
        long profileOnly = mongoTemplate.count(
            Query.query(new Criteria().andOperator(professional, Criteria.where("last_event_at").is(null), profileSeen())),
            DirectoryLink.class
        );
        if (registrationOnly > 0 && profileOnly > 0) {
            LOG.warn(
                "No hc-professional account has both phases: {} registrations have no profile status and {} profile statuses " +
                "have no registration, including {}. The two phases join on accountId and must match exactly — compare the " +
                "accountId hc-professional's gateway publishes on hc.professional.registration with the one its api publishes " +
                "on hc.professional.entity. Both streams being healthy is what this failure looks like.",
                registrationOnly,
                profileOnly,
                LogPseudonym.subject(accountId)
            );
        }
    }

    /**
     * Whether this write is the one that brings the second phase onto a link that already had the
     * first — the moment {@link DirectoryLink#phasesJoinedAt} records.
     *
     * <p>Answered from the document as it stood <em>before</em> the write, which both callers already
     * hold from their {@code findAndModify}, so no extra read is made. The
     * {@code phasesJoinedAt == null} test is what keeps it a first-join stamp rather than a
     * last-touched one, and it is also what lets a link written before this field existed acquire it
     * on the next event of either phase instead of never.
     *
     * @param otherPhase the watermark of the phase this write is <em>not</em>, read off the previous
     *                   document: {@code profileEventAt} when phase 1 is being written and
     *                   {@code lastEventAt} when phase 2 is.
     */
    private static boolean joinsThePhases(DirectorySource source, DirectoryLink previous, Function<DirectoryLink, Instant> otherPhase) {
        return (
            source == DirectorySource.HC_PROFESSIONAL &&
            previous != null &&
            previous.getPhasesJoinedAt() == null &&
            otherPhase.apply(previous) != null
        );
    }

    /** A link that phase 2 has written to. The watermark, because it is the field phase 2 always sets. */
    private static Criteria profileSeen() {
        return Criteria.where("profile_event_at").ne(null);
    }

    /** And its complement, which matches a missing field as well as an explicitly null one. */
    private static Criteria profileUnseen() {
        return Criteria.where("profile_event_at").is(null);
    }

    /**
     * Inserts the link if phase 1 has not already, and answers the state it was in before this call.
     *
     * <p><b>Identity fields on insert, and deliberately none of phase 1's content.</b> The subject
     * kind is {@code PROFESSIONAL} because a profile status on hc-professional's entity topic is by
     * definition about a clinician; the {@code external_id} is the {@code accountId}, which is what
     * that field holds for this source. What is <em>not</em> written is a login, an email or an
     * activation state — phase 2 carries none of them, and defaulting any of them would put a name or
     * a status on a row that nobody has told this service anything about.
     */
    private DirectoryLink upsertProfileLink(ProfileStatusEvent event) {
        Query query = Query.query(
            Criteria.where("source").is(DirectorySource.HC_PROFESSIONAL.name()).and("external_key").is(event.accountId())
        );
        Update onInsert = new Update()
            .setOnInsert("source", DirectorySource.HC_PROFESSIONAL)
            .setOnInsert("external_key", event.accountId())
            .setOnInsert("external_id", event.accountId())
            .setOnInsert("subject_kind", DirectorySubjectKind.PROFESSIONAL)
            .setOnInsert("first_seen_at", event.occurredAt());

        return mongoTemplate.findAndModify(
            query,
            onInsert,
            FindAndModifyOptions.options().upsert(true).returnNew(false),
            DirectoryLink.class
        );
    }

    /**
     * Writes the seven profile fields, field by field, after the watermark has had its say.
     *
     * <p>Each is written only when the event carries it, and a missing one leaves the stored value
     * alone rather than clearing it — the rule {@link #recordEvent} follows for identity, for the
     * same reason: a later frame that omits a field is not a frame saying the field is now empty.
     *
     * <p><b>The two booleans are written when they are non-null, including when they are false.</b>
     * That is the difference between "not reported" and "reported as incomplete", and it is the
     * whole reason they are {@code Boolean} rather than {@code boolean} from the wire down: a
     * profile going from complete back to incomplete is a real change the console has to be able to
     * show, and an absent field is not it.
     */
    private void recordProfileStatus(ProfileStatusEvent event, boolean joinsThePhases) {
        Update update = new Update().set("profile_event_at", event.occurredAt());
        if (joinsThePhases) {
            update.set("phases_joined_at", Instant.now());
        }

        setIfPresent(update, "profile_event_id", event.eventId());
        setIfPresent(update, "profile_id", event.profileId());
        setIfPresent(update, "profile_last_modified_by", event.lastModifiedBy());
        if (event.complete() != null) {
            update.set("profile_complete", event.complete());
        }
        if (event.verified() != null) {
            update.set("profile_verified", event.verified());
        }
        if (event.createdDate() != null) {
            update.set("profile_created_date", event.createdDate());
        }
        if (event.modifiedDate() != null) {
            update.set("profile_modified_date", event.modifiedDate());
        }

        mongoTemplate.updateFirst(
            Query.query(Criteria.where("source").is(DirectorySource.HC_PROFESSIONAL.name()).and("external_key").is(event.accountId())),
            update,
            DirectoryLink.class
        );
    }

    /**
     * Says what this event did, once, where the outcome is decided.
     *
     * <h2>Why this is not a log line on the creating branch</h2>
     *
     * <p>It was, and the asymmetry was a reported defect. {@code ensureLocalRecord} logged
     * <em>"Directory learned a patient"</em> at {@code INFO} when it created a record, and nothing
     * logged anything when a {@link Disposition#LINK_ONLY} event stored a link and deliberately
     * created none. So on production, on a service that had been consuming
     * {@code hc.professional.registration} since the 2026-09-06 deploy, <b>zero log lines mentioned
     * {@code HC_PROFESSIONAL} at all</b> — while the consumer group's offset moved 28 → 32 across a
     * clinician's registration with no lag and nothing dead-lettered. Backlog item 46.
     *
     * <p>That left an operator unable to tell <b>"the event never arrived"</b> from <b>"the event
     * arrived and was deliberately stored as a link"</b>, and the two demand opposite responses: the
     * first is a broker or a routing problem on another stack, the second is this service working as
     * designed. Both are now one {@code INFO} line naming the source, the type and the kind, so the
     * question is answered by reading rather than by inferring from an absence.
     *
     * <p><b>Stated on the outcome rather than added to a second branch</b>, which is the part worth
     * keeping — but it covers less than this javadoc claimed until 2026-09-07, and the difference is
     * the whole defect being fixed, so it is spelled out rather than glossed:
     *
     * <ul>
     *   <li><b>A new subject kind is covered by construction.</b> The kind is read off the event by
     *       {@code subjectKindOf} inside the two {@code INFO} branches, so a fourth one is named
     *       without touching this method at all.</li>
     *   <li><b>A new <em>disposition</em> is covered only if it reaches here.</b> {@code apply} has
     *       three early returns that never call this method: a null or keyless event answers
     *       {@link Outcome#IGNORED} silently, an {@code UPDATE_ONLY} event for an unknown subject
     *       logs its own {@code debug} at the point it decides, and a stale frame does the same. So
     *       {@code default} below is reached for {@link Outcome#UPDATED} and nothing else. A
     *       disposition added with an early return of its own would be exactly as silent as
     *       {@link Disposition#LINK_ONLY} was, which is the defect this method exists to fix — the
     *       mechanism to copy is the two lines above it, not this method's existence.</li>
     * </ul>
     *
     * <p>The three early returns were left where they are rather than routed through here on purpose:
     * each says <em>why</em> it declined, naming the type, the subject and the watermark it compared
     * against, and the generic line below would replace that with the word {@code IGNORED}. Two
     * {@code debug} lines that diagnose beat one {@code debug} line that is uniform.
     *
     * <p>{@link Outcome#UPDATED}, {@link Outcome#STALE} and {@link Outcome#IGNORED} are all at
     * {@code debug} wherever they are written — a replay re-reads a whole topic, and one {@code INFO}
     * per frame would bury the two lines that say something happened.
     *
     * <p><b>The subject is a digest and never the key</b>, at every level, for the reason
     * {@link LogPseudonym} gives at length: {@code subjectKey} is a patient's email address, these
     * lines reach an unauthenticated estate-wide Loki, and an operator holding an address can still
     * reproduce the digest in one line of shell. Backlog item 43. A clinician's key is an
     * {@code accountId} rather than an address and is hashed under the same rule — one shape of line,
     * no per-source exception for somebody to reason about later.
     *
     * <p><b>The creation line's wording changed here and nothing outside this repository read it</b>
     * — checked rather than assumed. It said "Directory learned a patient from HC_PATIENT" and now
     * names the stored {@code subject_kind}, so {@code grep 'Directory learned'} finds every kind and
     * {@code grep HC_PROFESSIONAL} finds the stream that had nothing to find.
     */
    private void announce(SiblingDomainEvent event, Outcome outcome, String localId) {
        String subject = LogPseudonym.subject(event.subjectKey());
        switch (outcome) {
            case CREATED -> LOG.info(
                "Directory learned a {} from {} ({}): {} -> {}",
                subjectKindOf(event),
                event.source(),
                event.type(),
                subject,
                localId
            );
            // Deliberately says only that no record was created, and not what the console does with
            // it: that differs by kind — a clinician is counted and listed as awaiting a record, a
            // care angel is neither — and a line that generalised would be wrong for one of them.
            case LINKED -> LOG.info(
                "Directory linked a {} from {} ({}): {} — {}, so no local record is created for this kind",
                subjectKindOf(event),
                event.source(),
                event.type(),
                subject,
                event.disposition()
            );
            // UPDATED, and today nothing else — STALE and IGNORED return before this method is
            // called and log at the point they decide, which is where the reason is known. A new
            // Outcome that also returns early lands nowhere: see the javadoc's second bullet.
            default -> LOG.debug("Applied {} for {} from {} — {}", event.type(), subject, event.source(), outcome);
        }
    }

    /**
     * Says that a patient chose a tier, and says loudly when this catalogue has never heard of it.
     *
     * <h2>The failure item 51 predicted, made audible</h2>
     *
     * <p>Backlog item 51 named this exactly, before either half was built: hc-patient publishes
     * {@code PEAR} / {@code PAWPAW} / {@code MELON}, and while this service's catalogue held
     * {@code Bridge Essential} / {@code Plus} / {@code Family} <em>"the event will arrive, parse, and
     * resolve to nothing — and nothing will say so. That is the same shape as item 26's wrong join
     * key and item 25's non-consumption: a healthy service, a consumer group with no lag, and a
     * screen that is simply wrong."</em> Item 51 has since put the codes into
     * {@code ServicePlan.code}, so the ordinary case now resolves; this line exists for the case that
     * still does not, which is a tier Abofonsa has published and
     * {@code ServicePlanCatalogueSyncService} has not yet brought across.
     *
     * <p><b>A warn, and deliberately not a refusal.</b> The frame is about a patient this service
     * holds and the choice is real whether or not the catalogue knows the tier; discarding it would
     * lose the back-office prompt the whole item exists to deliver. The row is written, the console
     * renders the code and hc-patient's own name for it, and says in words that it is not in this
     * catalogue. <b>No {@code ServicePlan} is created</b> — that would be a fourth restatement of a
     * price list, which is the thing item 51 closed.
     *
     * <p>The subject is a digest, at both levels, for the reason {@link LogPseudonym} gives: it is a
     * patient's email address and these lines reach an unauthenticated estate-wide Loki (item 43).
     * <b>The tier code is not</b>, and that is not an oversight: {@code PAWPAW} names a product, not a
     * person, and a digest of it would make the one line an operator reads to find out which tier is
     * missing unable to say which tier is missing.
     */
    private void announcePlanChoice(SiblingDomainEvent event) {
        PlanChoice plan = event.planChoice();
        String subject = LogPseudonym.subject(event.subjectKey());
        // findOneByCode over a uniquely-indexed field — ServicePlanIndexes creates it, so more than
        // one match is a data fault rather than something to tolerate here. A null code cannot be
        // looked up at all and takes the same branch as an unmatched one, which is right: an event
        // naming no tier is as unresolvable as one naming an unknown tier, and both need saying.
        boolean known = plan.code() != null && servicePlanRepository.findOneByCode(plan.code()).isPresent();
        if (known) {
            LOG.info(
                "Directory learned a plan choice from HC_PATIENT ({}): {} chose {} — reported {}",
                event.type(),
                subject,
                plan.code(),
                plan.status()
            );
        } else {
            LOG.warn(
                "Directory learned a plan choice from HC_PATIENT ({}): {} chose '{}' ({}), which is not a code in this catalogue — " +
                "the choice is stored and shown, but no ServicePlan matches it, so no price is resolvable. Either Abofonsa has " +
                "published a tier ServicePlanCatalogueSyncService has not brought across yet, or the two vocabularies have parted.",
                event.type(),
                subject,
                plan.code(),
                plan.name()
            );
        }
    }

    /**
     * What the link says this subject is, for the two lines above.
     *
     * <p>Never null in practice on either of them — a {@link Disposition#CREATE} carries
     * {@code PATIENT} and a {@link Disposition#LINK_ONLY} carries {@code CARE_ANGEL} or
     * {@code PROFESSIONAL} — but a log statement is the wrong place to depend on that, and "subject"
     * reads better than the word {@code null} in the one line an operator is reading because
     * something has gone wrong.
     */
    private String subjectKindOf(SiblingDomainEvent event) {
        return event.subjectKind() == null ? "subject" : event.subjectKind().name();
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
        int skipped = 0;

        for (DirectoryLink link : links) {
            boolean present = link.getLocalId() != null && patientRepository.findById(link.getLocalId()).isPresent();
            if (present) {
                alreadyPresent++;
                continue;
            }
            if (!keepsAPatientRecord(link)) {
                // A care angel, or somebody hc-patient has already erased. Both are links with no
                // local record BY DESIGN, so "the record is missing" is their normal state and
                // rebuilding it here would recreate exactly what the consumer refuses to create.
                // This loop is the reason the kind is stored on the document at all: there is no
                // event in front of it to re-read the authorities out of.
                skipped++;
                continue;
            }
            // The SAME rule the consumer applied, read back off the link, rather than a second
            // derivation of it. This used to read `state != "AccountCreated"` as activated, which
            // made every link last seen in any other state rebuild as ACTIVE — while the consumer,
            // for those same events, says activated is false. Two derivations of one rule,
            // disagreeing, under a javadoc claiming they were the same path.
            if (
                createAndClaim(
                    DirectorySource.HC_PATIENT,
                    link.getExternalKey(),
                    Boolean.TRUE.equals(link.getActivated()),
                    link.getFirstSeenAt() == null ? Instant.now() : link.getFirstSeenAt(),
                    link.getLocalId()
                ) !=
                null
            ) {
                created++;
            }
        }

        LOG.info(
            "Directory reconciliation examined {} links, created {}, left {} alone, skipped {} that keep no record",
            links.size(),
            created,
            alreadyPresent,
            skipped
        );
        return new DirectoryReconciliationDTO(links.size(), created, alreadyPresent, skipped);
    }

    /**
     * Whether this link is one this service keeps a {@code Patient} for.
     *
     * <p>A null {@code subjectKind} reads as {@code PATIENT}: links written before 2026-09-05 carry
     * no kind, and every one of them was created by the path that turned any patient-stream subject
     * into a patient. Defaulting the other way would make the reconciliation stop rebuilding the
     * records it exists to rebuild.
     */
    private boolean keepsAPatientRecord(DirectoryLink link) {
        if (link.getErasedAt() != null) {
            return false;
        }
        return link.getSubjectKind() == null || link.getSubjectKind() == DirectorySubjectKind.PATIENT;
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
            .setOnInsert("first_seen_at", event.occurredAt())
            // On insert only. A CREATE event promotes an existing CARE_ANGEL link to PATIENT in
            // recordEvent below — an angel who later registers in their own right is a patient — and
            // the reverse must never happen, so LINK_ONLY writes its kind here and not there.
            .setOnInsert("subject_kind", event.subjectKind());

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
     * <p>Identity fields are only ever set when the event carries them, never cleared — with one
     * exception, below. hc-patient's stream is explicit that a {@code patientId} does not exist until
     * onboarding step 1, so a later account event with no patient id must not erase the one
     * {@code OnboardingStarted} published.
     *
     * <p><b>The exception is the erasure.</b> {@code DeletionRequestChanged/COMPLETED} says the far
     * side has already deleted the subject's profile, and it carries their address and login in order
     * to say so. What this service can drop, it drops: the {@code login} and the {@code patientId},
     * the latter being a handle into a record that no longer exists. What it cannot drop is the
     * address, because the address <em>is</em> {@code external_key} — the correlation key, and the
     * key the watermark hangs on. Clearing the {@code email} field while the same string sits in
     * {@code external_key} would be theatre, so it is not done, and whether hc-admin should erase its
     * own copy when hc-patient erases theirs is a retention decision with an owner rather than a line
     * of code: backlog item 28.
     */
    private void recordEvent(SiblingDomainEvent event, String localId, boolean joinsThePhases) {
        Update update = new Update().set("last_event_at", event.occurredAt()).set("last_event_type", event.type());
        if (joinsThePhases) {
            update.set("phases_joined_at", Instant.now());
        }

        // State is written when the event carries one and LEFT ALONE when it does not, which changed
        // on 2026-09-08 with the account events. It used to be an unconditional `set`, and that was
        // safe only while every frame that reached here had one: hc-patient's stream puts the type in
        // this field, and of hc-professional's two original types `onboarding.state` carries a state
        // and `registration.created` arrives before it. `AccountCreated` and `AccountActivated` carry
        // none and arrive AFTER `onboarding.state` — the three frames of one registration are keyed
        // identically, so they are ordered — so an unconditional set would have cleared IN_PROGRESS
        // off the console the moment the third frame landed. An event that says nothing about
        // onboarding is not an event saying onboarding has been undone.
        setIfPresent(update, "state", event.state());
        setIfPresent(update, "last_event_id", event.eventId());
        setIfPresent(update, "email", event.email());
        if (event.erased()) {
            update.set("erased_at", event.occurredAt()).unset("login").unset("external_id");
        } else {
            setIfPresent(update, "login", event.login());
            setIfPresent(update, "external_id", event.externalId());
        }
        if (event.disposition() == Disposition.CREATE) {
            // The only promotion there is: a link first seen as a care angel becomes a patient when
            // that person registers or begins onboarding in their own right. Never the other way —
            // upsertLink writes the kind on insert only.
            update.set("subject_kind", event.subjectKind());
        }
        // Activation, and the one rule in this method that differs by source.
        //
        // HC_PATIENT is monotone — set true, never back to false. That is the link's copy of the
        // PENDING -> ACTIVE rule governing the Patient it names, and it is here so the reconciliation
        // can READ the answer the consumer reached rather than derive its own from the last event
        // type. The stream never says "deactivated" on that side, and an administrator's SUSPENDED
        // is this service's own decision, which a replay must not undo.
        //
        // HC_PROFESSIONAL writes what the event says, INCLUDING false. There the field is phase 1's
        // `activated` (backlog item 47, under that name): the account's own state, owned outright by
        // and an account that is deactivated after its profile is complete is a state the contract
        // names explicitly. Nothing local hangs off it — no Professional exists to be promoted — so
        // there is no administrator's decision to defend, only a fact to record.
        //
        // Null is neither, on both sources: no event has said, and the field is left exactly as it
        // was rather than written false. That is what keeps "not reported" distinguishable from
        // "deactivated" on the console, and it is why SiblingDomainEvent.activated is a Boolean.
        if (Boolean.TRUE.equals(event.activated())) {
            update.set("activated", true);
        } else if (Boolean.FALSE.equals(event.activated()) && event.source() == DirectorySource.HC_PROFESSIONAL) {
            update.set("activated", false);
        }
        // The account's own dates, from phase 1. Written only when the event carries them, and
        // deliberately NOT defaulted to occurredAt or to now: `first_seen_at` and `last_event_at`
        // already record when this service heard something, and the whole reason this pair exists is
        // that those two answer a different question. A createdDate filled in from a frame's arrival
        // would be a plausible wrong date on a screen headed "created", which is item 45's defect
        // with a timestamp instead of an id.
        if (event.accountCreatedDate() != null) {
            update.set("account_created_date", event.accountCreatedDate());
        }
        if (event.accountModifiedDate() != null) {
            update.set("account_modified_date", event.accountModifiedDate());
        }
        // The membership tier, from hc-patient's PlanChosen and from nothing else — backlog item 48.
        //
        // WRITTEN AS A GROUP, NOT FIELD BY FIELD, AND THAT IS THE ONE RULE HERE THAT DIFFERS FROM
        // EVERY FIELD ABOVE. The identity fields use setIfPresent because they arrive from eight
        // different event types that each carry a different subset, so "this frame omits a login" is
        // not "this subject has no login". These four are the opposite case: they are one membership's
        // facts, built at one call site in hc-patient's announceChosenPlan, and they describe the
        // membership named by plan_membership_id. Mixing a new membership's id with an old
        // membership's tier produces a row asserting a tier nobody chose for it — item 45's
        // plausible-wrong-answer defect, on the one field this panel exists to show.
        //
        // THIS WAS A DEFECT UNTIL THE ITEM 48 REVIEW, and the comment that stood here was the cause:
        // it claimed "all four arrive together or none does", citing their containsOnlyKeys test. That
        // test pins the KEY SET, not the values. `Membership.plan` carries no @NotNull and
        // `data.put("planCode", membership.getPlan())` puts a null straight on the wire, so an
        // administrator creating a membership through their CRUD path with no tier named publishes
        // planCode: null and planName: null with a real membershipId and status. Their own javadoc
        // warns about exactly this path by name — "the administrative CRUD path is the exception, and
        // a consumer should not generalise from the sentence above" — and the sentence I generalised
        // from was the one it was warning about. Read the producer's nullability, not its test.
        //
        // Keyed on the membership rather than on the tier: a frame naming no membership describes
        // nothing and is left to touch nothing, where one naming a membership replaces the group
        // wholesale. membershipId is the reliable field — it is read off the saved document, after
        // the save — so this is a guard against a malformed frame rather than against their CRUD path.
        //
        // NOTHING IS RESOLVED HERE AND NO ServicePlan IS CREATED. The code is stored as sent and
        // matched against this catalogue at read time, so a tier the sync has not brought across yet
        // starts resolving when it does rather than being frozen as unknown on the way in. See
        // DirectoryLink.planCode and announcePlanChoice.
        PlanChoice plan = event.planChoice();
        if (plan != null && plan.membershipId() != null && !plan.membershipId().isBlank()) {
            setOrUnset(update, "plan_membership_id", plan.membershipId());
            setOrUnset(update, "plan_code", plan.code());
            setOrUnset(update, "plan_name", plan.name());
            setOrUnset(update, "plan_status", plan.status());
        }
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
     * Writes the value, or <b>removes the field</b> when the event does not carry one.
     *
     * <p>The counterpart of {@link #setIfPresent} and the deliberate opposite of it. That one is for a
     * field several event types each say something about, where an omission is silence; this is for a
     * group of fields one event type writes together, where an omission is the answer. Only the plan
     * choice uses it, and {@code recordEvent} argues why at the call site.
     *
     * <p>{@code unset} rather than {@code set(field, null)}: an unset field and an explicitly null one
     * read identically through the mapped type, but nothing else in this collection writes an explicit
     * null and {@code Criteria.is(null)} matches both — so removing it keeps the documents uniform and
     * keeps a {@code planStatus} filter from ever matching on an absent value.
     */
    private void setOrUnset(Update update, String field, String value) {
        if (value != null && !value.isBlank()) {
            update.set(field, value);
        } else {
            update.unset(field);
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
        if (existing != null) {
            merge(existing, event);
            return knownLocalId;
        }
        if (event.disposition() != Disposition.CREATE) {
            // A care angel's nomination, and every event that only updates. Neither may bring a
            // patient into existence, and returning the id unchanged rather than null leaves a
            // dangling reference for the reconciliation to rebuild instead of silently dropping it.
            return knownLocalId;
        }
        // What this created — or did not — is announced by `apply`, not here. The line that stood on
        // this branch was the whole of the service's visibility, so a LINK_ONLY event wrote nothing
        // at all and an operator could not tell it from an event that never arrived. See `announce`.
        // An event that says nothing about activation is not an event saying the account is inactive:
        // a new Patient created from one starts PENDING, which is what it started as before this
        // field became a Boolean and is the honest reading of "nobody has said".
        return createAndClaim(event.source(), event.subjectKey(), Boolean.TRUE.equals(event.activated()), event.occurredAt(), knownLocalId);
    }

    /**
     * Creates the local record for a link that has none, and attaches it <b>atomically</b>.
     *
     * <p>Shared by the consumer and the reconciliation, and that sharing is the point rather than
     * tidiness. Both paths could create a {@code Patient} for the same link, unserialised: a
     * reconciliation run while a first sighting is being applied, or two instances briefly on one
     * partition during a rebalance. Two records, one link, and an orphan counted on every dashboard
     * tile with nothing pointing at it.
     *
     * <p>The claim is therefore a compare-and-set on the value the caller <em>saw</em>: null where the
     * link had no record, and the stale id where the reconciliation found one naming a document that
     * has gone. A writer that modifies nothing has lost, deletes the record it had just made, and
     * answers null — so the winner's is the one the link names. {@code null} also means "nothing was
     * created" to the caller, which is what keeps the reconciliation's {@code created} count honest.
     *
     * <p><b>What this does not fix, and cannot here.</b> Saving the {@code Patient} and setting
     * {@code local_id} are two writes, Mongo runs standalone, and there is no transaction to hold them
     * together. A crash between them leaves an orphaned record and a link with no {@code local_id};
     * the redelivery makes a second record and claims the link, and the first is unreachable for ever.
     * That window is one Mongo round trip wide and the alternative is a replica set, which is a
     * deployment decision rather than a code one — backlog item 28.
     *
     * <p>Two required fields on the record and nothing else. {@code caseCount} is seeded at zero
     * because the console renders it as a number and an absent one reads as unknown rather than as
     * none; everything else is left null for an administrator to fill in, including the
     * {@code Profile} — which cannot be created at all, since it requires a name, a date of birth, a
     * phone number and a document number, and the patient stream carries none of them by design.
     *
     * @param staleLocalId the {@code local_id} the caller read off the link — null when there was
     *                     none, or the id of a document that has since gone. The claim only succeeds
     *                     against that value, which is what makes it a compare-and-set rather than a
     *                     blind write.
     * @return the id of the record this call created, or null when it created none.
     */
    private String createAndClaim(DirectorySource source, String subjectKey, boolean activated, Instant at, String staleLocalId) {
        LocalDate day = LocalDate.ofInstant(at, ZoneOffset.UTC);
        Patient patient = patientRepository.save(
            new Patient().status(activated ? AccountStatus.ACTIVE : AccountStatus.PENDING).joinedOn(day).lastActiveOn(day).caseCount(0)
        );

        long claimed = mongoTemplate
            .updateFirst(
                // `local_id: null` matches a missing field as well as a null one, which is what an
                // unclaimed link actually looks like — setIfPresent never writes the field at all.
                Query.query(Criteria.where("source").is(source.name()).and("external_key").is(subjectKey).and("local_id").is(staleLocalId)),
                new Update().set("local_id", patient.getId()),
                DirectoryLink.class
            )
            .getModifiedCount();

        if (claimed == 0) {
            LOG.debug(
                "Another writer claimed the link for {} first — dropping the record this call made",
                LogPseudonym.subject(subjectKey)
            );
            patientRepository.deleteById(patient.getId());
            return null;
        }
        return patient.getId();
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
        if (Boolean.TRUE.equals(event.activated()) && patient.getStatus() == AccountStatus.PENDING) {
            patient.setStatus(AccountStatus.ACTIVE);
            changed = true;
        }

        if (changed) {
            patientRepository.save(patient);
        }
    }
}
