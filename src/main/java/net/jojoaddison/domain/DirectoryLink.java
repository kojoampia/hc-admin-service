package net.jojoaddison.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One account on a sibling stack, and what this directory has learned about it from the broker.
 *
 * <h2>Why this is a collection of its own rather than three fields on {@code Patient}</h2>
 *
 * <p>The obvious shape is to hang the sibling's identifiers off the local record. It was rejected
 * for two reasons and both are worth keeping.
 *
 * <p>The first is that <b>how hc-admin should model a cross-stack patient identity is an open
 * decision</b> — backlog item 22, which weighs a {@code patientId} field, a read across at use time,
 * and not naming patients here at all. A field added here as a side effect of fixing a consumer
 * would be populated only for accounts registered after this shipped, would look like the answer,
 * and would be exactly the "plausible wrong id" failure that entry exists to prevent. This
 * collection is deliberately <em>beside</em> the domain record, so item 22 is still free to decide.
 *
 * <p>The second is that <b>an event carries far less than a record.</b> {@code Patient} is an
 * administrator's document — plan, hub, clinical lead, case count — and none of that is on the wire.
 * Mixing the two on one document invites the next reader to write a merge that overwrites an edit.
 * The rule instead lives in one place, {@link net.jojoaddison.service.DirectoryProjectionService},
 * and this document is only ever the identity map and the watermark.
 *
 * <h2>Idempotency, and what the atomic upsert does and does not give</h2>
 *
 * <p>{@code (source, externalKey)} is the natural key. The write is {@code findAndModify} with
 * {@code upsert}, which MongoDB serialises per document, so a redelivery of the same event produces
 * one link and not two.
 *
 * <p><b>This paragraph used to say that made a unique index unnecessary, and that was wrong.</b>
 * MongoDB documents the opposite: the operation is atomic per document, and two concurrent upserts
 * that match nothing <em>can both insert</em> — the stated requirement for at-most-one is a unique
 * index on the query field. It also named the wrong race as the one being defended against:
 * {@code reconcile()} never upserts a link, so "a redelivery and the reconciliation racing each
 * other" was not a thing that could happen. The index now exists, created explicitly at startup by
 * {@link net.jojoaddison.config.DirectoryLinkIndexes} because this service creates none by
 * convention, and it is a lookup index as well as a constraint — {@code (source, external_key)} is
 * read on every message and this collection had no index at all.
 *
 * <p>Two things the index does not fix, stated rather than implied:
 *
 * <ul>
 *   <li><b>The local record is a second write with no transaction around it.</b> Saving the
 *       {@code Patient} and setting {@code local_id} on the link are separate operations, and Mongo
 *       runs standalone here, so there is no transaction to hold them together. A crash between them
 *       leaves an orphaned {@code Patient} and a link with no {@code local_id}; the redelivery then
 *       creates a second record and claims the link, and the first is unreachable. The claim is at
 *       least made atomically — see {@code DirectoryProjectionService.createAndClaim} — so two
 *       writers cannot both attach a record to one link, which is the case that would have shown two
 *       people on every tile.</li>
 *   <li><b>"Equal is a redelivery" is equal to the millisecond.</b> MongoDB stores a date as
 *       milliseconds since the epoch, so an {@code Instant} written with more precision comes back
 *       truncated and the watermark comparison is a millisecond comparison. Harmless — two distinct
 *       events about one subject in the same millisecond would have to arrive out of order to matter
 *       — but it is not the nanosecond comparison the Java types suggest.</li>
 * </ul>
 *
 * <h2>A clinician is accepted in two phases, and this document is where they join</h2>
 *
 * <p>Backlog item 47, the architect's decision of 2026-09-07: hc-professional does not publish one
 * event carrying everything a clinician is, because at the moment they register <b>there is nothing
 * to publish</b> — a licence and a discipline are what credentialing collects afterwards. So the
 * contract is two events with two shapes:
 *
 * <ul>
 *   <li><b>Phase 1, on account creation</b> — {@code AccountStatus{accountId, login, email, activated,
 *       createdDate, modifiedDate}}, from hc-professional's <em>gateway</em>, on
 *       {@code hc.professional.registration}. Four of the six land on {@link #externalKey},
 *       {@link #login}, {@link #email} and {@link #activated}, which already existed under exactly
 *       those names — the contract deliberately uses the names both products already use, since three
 *       names for one field across two products is how the {@code admin-service} /
 *       {@code hcadminservice} mismatch cost every entity call a 404. The genuinely new pair is
 *       {@link #accountCreatedDate} and {@link #accountModifiedDate}.</li>
 *   <li><b>Phase 2, basic profile metadata only</b> — {@code ProfileStatus{profileId, accountId,
 *       isComplete, isVerified, createdDate, modifiedDate, lastModifiedBy}}, from hc-professional's
 *       <em>api</em>, on {@code hc.professional.entity}. It lands on the seven {@code profile*}
 *       fields below, none of which existed before.</li>
 * </ul>
 *
 * <p><b>The two phases join on {@code accountId}, which is this document's {@code external_key}</b>,
 * so the join is the upsert rather than a query somebody has to get right: both consumers address
 * {@code (HC_PROFESSIONAL, accountId)} and whichever arrives first opens the row.
 *
 * <p><b>Out of order is the primary path and not an edge case, because the two phases are on two
 * topics.</b> The gateway publishes phase 1 to {@code hc.professional.registration} and the api
 * publishes phase 2 to {@code hc.professional.entity} — each to the topic it already owns — and
 * between two topics there is no ordering whatever. Both consumer groups read from the earliest
 * offset, so a {@code ProfileStatus} landing before its {@code AccountStatus} happens on every
 * backfill and can happen on any restart. It is therefore a state this document represents rather
 * than a case the consumer tolerates: the profile fields are written, the identity fields stay
 * absent, and the console shows an unnamed row with a profile on it, which is the truth.
 *
 * <p><b>Phase 2 carries its own watermark, and that is not tidiness.</b> {@link #lastEventAt} is the
 * watermark of one producer's stream; the two phases come from two applications with two clocks and
 * two topics, whose {@code occurredAt} sequences are independent. Comparing a phase-2 event against a
 * phase-1 watermark would discard it whenever the gateway's clock ran ahead — silently, and
 * indistinguishably from hc-professional not having published. So {@link #profileEventAt} is
 * compared against phase-2 events and nothing else, and neither consumer writes the other's fields.
 *
 * <p><b>Nothing here becomes a {@code Professional}.</b> Neither phase carries a {@code role} or a
 * {@code licenceNumber} — the contract deliberately does not, so that no clinical credential travels
 * on a topic four stacks can read — so {@link #localId} stays null for a clinician however complete
 * the profile status says they are. Backlog items 27(a), 33, 46 and 47 all say this and it is the one
 * rule none of them relaxes.
 */
@Document(collection = "directory_link")
public class DirectoryLink implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("source")
    private DirectorySource source;

    /**
     * The correlation key, and the half of the natural key that varies.
     *
     * <p>Lowercased email for {@link DirectorySource#HC_PATIENT} — hc-patient's own
     * {@code PatientEvent} javadoc explains why it cannot be a patient id: there is no patient until
     * onboarding step 1, and the two account events happen before that. It is also the value
     * hc-patient sets as the Kafka partition key, so every event about one person arrives here in
     * order, on one thread.
     *
     * <p>{@code accountId} for {@link DirectorySource#HC_PROFESSIONAL}, which is what that stream is
     * keyed on throughout — registration and onboarding state alike.
     */
    @Field("external_key")
    private String externalKey;

    /**
     * The sibling's own identifier for the subject, when the stream has published one.
     *
     * <p>For a patient this is the {@code patientId}, and it arrives only with
     * {@code OnboardingStarted} — the one event that binds an email to a patient id. Null before
     * that, which is a real state and not a defect. For a professional it is the {@code accountId},
     * so it equals {@code externalKey}; carrying it anyway keeps a reader from having to know which
     * source stores identity where.
     */
    @Field("external_id")
    private String externalId;

    /**
     * The subject's gateway login, and <b>the only thing the console ever names a clinician by</b>.
     *
     * <p>This is phase 1's {@code login}, under that name on both sides: hc-professional's
     * registration event already emits it and backlog item 47's contract names it the same, having
     * been revised away from an earlier draft that said {@code username}.
     *
     * <p>Null is a real state rather than a gap: an {@code onboarding.state} frame carries neither a
     * login nor an address, so a subject whose only event was one of those cannot be named at all,
     * and the console says so in words instead of printing the {@code accountId}.
     */
    @Field("login")
    private String login;

    /**
     * The subject's email address, held for correlation and <b>deliberately not shown on the
     * clinician panel</b>.
     *
     * <p>For a patient this is the same value as {@link #externalKey} and the patient directory does
     * show it, on the argument set out at length in {@code DirectoryLinkResource}'s javadoc: an
     * administrator's patient directory is where a patient's contact address belongs. For a clinician
     * it is on the wire so the two phases can be correlated by a person, and backlog item 47 names
     * {@link #login} — under that name, the contract having been revised away from an earlier draft's
     * {@code username} — as the field the console renders. Keeping the address off that screen is the
     * same decision item 43 took for logs, one surface along.
     */
    @Field("email")
    private String email;

    /**
     * The last lifecycle state this subject was reported in — an event type for hc-patient, the
     * {@code state} payload field for hc-professional's {@code onboarding.state}.
     *
     * <p><b>Null for a clinician whose only event is a {@code registration.created}</b>, and that is
     * the intended answer rather than a gap: that event carries no {@code state} field, because a
     * registration is not a stage of onboarding. The parser fell back to the event type here until
     * 2026-09-07, which put the string {@code "registration.created"} on the console's
     * awaiting-a-record panel, where this field is rendered verbatim. {@link #lastEventType} is where
     * the type is recorded, and it always was.
     *
     * <p>A free string on purpose. Both producers say plainly that a consumer meeting a type it does
     * not know must ignore it, so binding this to an enum here would turn "they added an event"
     * into "this service refuses a message".
     */
    @Field("state")
    private String state;

    /**
     * What kind of account this is, and therefore whether a local record is kept for it.
     *
     * <p><b>Stored rather than re-derived, and that is the point of it.</b> A care angel and a
     * patient arrive on the same topic under the same {@code AccountCreated} type, distinguishable
     * only by a field in the event's {@code data} — which the reconciliation endpoint does not have
     * in front of it. Without this on the document, a reconciliation would rebuild a {@code Patient}
     * for every angel. See {@link DirectorySubjectKind}.
     *
     * <p>Null on links written before 2026-09-05, which is read as {@link DirectorySubjectKind#PATIENT}
     * for {@link DirectorySource#HC_PATIENT}: those are exactly the rows created back when every
     * patient-stream subject became a patient.
     */
    @Field("subject_kind")
    private DirectorySubjectKind subjectKind;

    /**
     * Whether the account can sign in — <b>the account's own state, never inferred from anything
     * else</b>.
     *
     * <p>This is phase 1's {@code activated} (backlog item 47 — that name, not the earlier draft's
     * {@code isActivated}), and the prohibition on deriving it
     * is worth the words: a clinician can be activated with no profile at all, and can complete a
     * profile on an account somebody later deactivates, so reading it off {@link #profileComplete},
     * off the presence of a profile status, or off {@link #state} would be wrong in both directions.
     * Null means no event has said, which is a third state the console renders as such rather than
     * as "not activated".
     *
     * <p><b>Stored so the reconciliation does not have to re-derive it, which it used to get wrong.</b>
     * It read {@code state != "AccountCreated"} as "activated", so a link last seen in
     * {@code OnboardingStarted}, {@code OnboardingStepCompleted}, {@code CareDelegationChanged},
     * {@code DeletionRequestChanged} or anything it did not know rebuilt as {@code ACTIVE} — while the
     * consumer, for those same events, says {@code activated == false}. Two derivations of one rule,
     * disagreeing, under a javadoc claiming they were the same path.
     *
     * <p>Written monotonically for {@link DirectorySource#HC_PATIENT} — set true and never back to
     * false — which is the link's copy of the {@code PENDING → ACTIVE} rule that governs the
     * {@code Patient} it names. <b>Not monotone for {@link DirectorySource#HC_PROFESSIONAL}</b>,
     * because there the value is the account's own state as phase 1 reports it and an account can be
     * deactivated. The asymmetry is deliberate and is argued at
     * {@code DirectoryProjectionService.recordEvent}: on the patient stream this field defends an
     * administrator's decision against a replay, and on the professional stream it is a fact the far
     * side owns outright.
     */
    @Field("activated")
    private Boolean activated;

    /**
     * When the far side told this service it had erased the subject, and null for everybody else.
     *
     * <p>Set from hc-patient's {@code DeletionRequestChanged} with {@code change=COMPLETED}, which
     * is published <em>after</em> the profile has already gone. It is a marker and not a deletion:
     * this service does not delete the {@code Patient} (an administrator's record with an
     * operational history of its own) and does not delete the link either, because the link holds
     * the watermark — dropping it would let the whole of that subject's retained history replay into
     * a fresh one and undo the marker. What it does is stop the reconciliation rebuilding a record
     * for somebody whose far side is gone, and tell a reader why the row looks the way it does.
     */
    @Field("erased_at")
    private Instant erasedAt;

    /**
     * The local document this subject produced, when it produced one.
     *
     * <p>Null for every {@link DirectorySource#HC_PROFESSIONAL} link, and that is stated rather than
     * pending: {@code Professional} requires a {@code role} and a {@code licenceNumber}, neither of
     * which is on a registration event nor could be — they are what credentialing collects. A row
     * invented with a fabricated licence number in a directory whose whole purpose is verification is
     * worse than no row. Null for a {@link DirectorySubjectKind#CARE_ANGEL} too, for the same shape of
     * reason: hc-admin models an angel as its own entity joined to the patient who nominated them,
     * and an account event carries neither half of that.
     */
    @Field("local_id")
    private String localId;

    /**
     * When <b>this service</b> first saw any event about the subject.
     *
     * <p><b>Not the account's own creation date and must never be rendered as one</b>, which is why
     * {@link #accountCreatedDate} exists beside it. This value is a property of this service's
     * consumption: it moves if the collection is rebuilt from a backfill, it is the date of the
     * oldest frame still inside the broker's retention window rather than the date the account was
     * made, and for a subject learned during a replay it is simply wrong for that purpose. The same
     * distinction applies to {@link #lastEventAt}, which is when this service last heard something
     * and not when the account last changed.
     */
    @Field("first_seen_at")
    private Instant firstSeenAt;

    /**
     * When the account was created on hc-professional — phase 1's {@code createdDate}.
     *
     * <p>New with backlog item 47 and genuinely new storage, unlike the rest of phase 1: this service
     * had {@link #firstSeenAt} and {@link #lastEventAt}, which answer a different question. Null until
     * hc-professional's phase-1 change ships, since {@code registration.created} does not carry it
     * today.
     */
    @Field("account_created_date")
    private Instant accountCreatedDate;

    /** When the account last changed on hc-professional — phase 1's {@code modifiedDate}. */
    @Field("account_modified_date")
    private Instant accountModifiedDate;

    /**
     * The watermark: {@code occurredAt} of the newest event applied to this subject.
     *
     * <p>What makes replay safe in the direction that matters. Delivery is at least once and a
     * consumer group reading from the earliest offset re-reads the whole topic, so an older event
     * arriving after a newer one is normal. Anything strictly older than this is not applied — which
     * also protects an administrator's edit from being undone by a message from before they made it.
     */
    @Field("last_event_at")
    private Instant lastEventAt;

    @Field("last_event_id")
    private String lastEventId;

    @Field("last_event_type")
    private String lastEventType;

    // --- phase 2: the profile status, from hc-professional's api ---------------------------------
    //
    // Seven fields and nothing else, which is the whole of the contract in backlog item 47:
    // identifiers, two booleans and three timestamps. No role, no licence number, no name, no
    // address — so nothing here can build a Professional and nothing here is a clinical credential
    // on a shared topic. Every one of them is null until phase 2 arrives, and null means "not
    // reported" rather than false: a profile nobody has told this service about is not an incomplete
    // profile, and the console says which.

    /** hc-professional's own id for the {@code Profile}. A handle for a person, not a join key. */
    @Field("profile_id")
    private String profileId;

    /** Phase 2's {@code isComplete}: whether the far side considers the profile filled in. */
    @Field("profile_complete")
    private Boolean profileComplete;

    /** Phase 2's {@code isVerified}. Not this service's own verification, which lives on {@code Professional}. */
    @Field("profile_verified")
    private Boolean profileVerified;

    /** When the profile was created on the far side — their clock, not this service's. */
    @Field("profile_created_date")
    private Instant profileCreatedDate;

    /** When the profile last changed on the far side. */
    @Field("profile_modified_date")
    private Instant profileModifiedDate;

    /**
     * Who last changed the profile — <b>hc-professional's login for them</b>, and never a display
     * name.
     *
     * <p><b>Item 47's contract calls this "an accountId, which IS the gateway's {@code User.id}", and
     * that is wrong about the code on the far side.</b> The value is Spring Data auditing's
     * {@code lastModifiedBy} on their {@code Profile}, filled by their
     * {@code SpringSecurityAuditorAware} from {@code SecurityUtils.getCurrentUserLogin()} — the JWT
     * subject, which is a login — or by their {@code Constants.SYSTEM} when nothing was authenticated.
     * The architect's decision 2 of 2026-09-08 moves their {@code accountId} onto a {@code User.id}
     * and changes nothing about auditing, so this field and {@link #externalKey} are in <b>different
     * identifier spaces</b> and will stay that way. Verified against their {@code origin/main}
     * 2026-09-08.
     *
     * <p>What that changes here is a reader's expectations rather than any code. It still needs no
     * resolution and gets none, and the console still shows it verbatim — but it may not be joined to
     * an {@code external_key}, to an {@code AuditLog.userId} or to a login on <em>this</em> gateway,
     * because it names an account on another stack. It is also, incidentally, the one identifier on
     * this row that a person can already read.
     */
    @Field("profile_last_modified_by")
    private String profileLastModifiedBy;

    /**
     * The phase-2 watermark: {@code occurredAt} of the newest profile event applied to this subject.
     *
     * <p>Separate from {@link #lastEventAt} on purpose — see the class javadoc. Two producers, two
     * topics, two clocks; one watermark across both would discard whichever stream ran behind.
     */
    @Field("profile_event_at")
    private Instant profileEventAt;

    /** The id of the newest profile event applied, for tracing. Never used for deduplication. */
    @Field("profile_event_id")
    private String profileEventId;

    // --- the membership tier a patient chose, from hc-patient's PlanChosen ------------------------
    //
    // Backlog item 48. Four fields, which is the whole of that event's payload, and they are written
    // by the patient consumer alone — no clinician has a membership and nothing on either of
    // hc-professional's topics carries one.
    //
    // THEY MOVE AS A GROUP AND NEVER INDIVIDUALLY. All four describe the ONE membership named by
    // plan_membership_id, so a frame about a new membership replaces the set rather than merging into
    // it — DirectoryProjectionService.recordEvent argues it at the write, and it was a live defect
    // until the item 48 review. Three of them are nullable ON THE WIRE: hc-patient's Membership.plan
    // and .name carry no @NotNull and their administrative CRUD path can create a membership with
    // neither, so plan_code and plan_name being absent while plan_status is present is a real stored
    // state and not a partial write. The console renders it as "no tier named" rather than as a blank.
    //
    // WHY THERE IS NO FIFTH FIELD HOLDING A WATERMARK, WHICH PHASE 2 ABOVE DOES HAVE. A plan choice
    // arrives on `patient-events`, keyed on the same lowercased email, as every other event this
    // document already orders by `last_event_at`, so it shares that watermark.
    //
    // THE FIRST VERSION OF THIS COMMENT SAID "one producer, one clock, one partition" AND THAT IS
    // FALSE. `patient-events` has TWO producers: hc-patient's own `PatientEventType` javadoc records
    // that `AccountCreated` and `AccountActivated` are emitted by their GATEWAY, while `PlanChosen`
    // comes from their API. Two applications, two clocks — which is the shape phase 2 opened
    // `profile_event_at` for, so the distinction drawn here is not the one that was claimed.
    //
    // The decision to share the watermark stands anyway, on the true premise: the skew is between
    // two containers on one host, bounded by seconds, against events that are minutes apart in
    // practice. What is ACCEPTED rather than absent is the tail — if the gateway's clock leads the
    // api's by more than the gap between an activation and a plan choice, the `PlanChosen` is
    // discarded as STALE at debug level.
    //
    // That tail costs more here than elsewhere on this stream, and that is the part to weigh if this
    // is ever revisited: every other event is re-carried by a later one, so a discarded frame
    // self-heals. `PlanChosen` is published ONCE — hc-patient announces on POST only — so a frame
    // dropped as stale is a prompt lost for good. Give it its own watermark the day that is observed,
    // or the day the two applications stop sharing a host.
    //
    // AND WHY THERE IS NO "CHOSEN ON" DATE. The event carries no membership creation date — the only
    // timestamp on the frame is the envelope's `occurredAt`, which is when the announcement was
    // published — and putting that under a heading reading "chosen on" is item 47's refused
    // substitution with a different field on it. If a date is wanted on screen it has to be asked of
    // hc-patient and published by them.

    /**
     * hc-patient's own id for the {@code Membership}.
     *
     * <p>A support handle rather than a join key: the exchange with them is keyed on the patient's
     * address, which is this document's {@link #externalKey}, and item 54 settled the return payload
     * at the plan alone for exactly that reason. It is here because it is what an administrator or an
     * engineer quotes when asking hc-patient about a specific subscription.
     */
    @Field("plan_membership_id")
    private String planMembershipId;

    /**
     * Abofonsa's tier code, as hc-patient sent it — {@code PEAR}, {@code PAWPAW}, {@code MELON}.
     *
     * <p><b>Stored raw and resolved at read time, never at consume time.</b> Since backlog item 51
     * this service's {@code ServicePlan.code} is the same vocabulary, synced from the same content
     * API, so the code does resolve — but the catalogue syncs on its own schedule and a tier Abofonsa
     * published this morning may not be here until it next runs. Resolving on the way in would freeze
     * that answer: the row would read "not in this catalogue" for ever, on a code that started
     * matching an hour later. A {@code ServicePlan} is never created from this value either — that
     * would be a fourth restatement of a price list item 51 exists to have stopped.
     *
     * <p>{@code DirectoryProjectionService} announces a code that resolves to nothing as it applies
     * the event, which is the one place a subject can be named beside it.
     */
    @Field("plan_code")
    private String planCode;

    /**
     * The tier's display name as hc-patient holds it — {@code "PAWPAW Plan"}.
     *
     * <p>Carried as well as the code so a row is readable even when {@link #planCode} resolves to
     * nothing here. It is <b>their</b> name for the tier and not this catalogue's, and the two can
     * differ; the console says which it is showing rather than presenting one as the other.
     *
     * <p>Null when the membership names no tier — see the group note above. It is
     * {@code Membership.name} on their side, which is not required either.
     */
    @Field("plan_name")
    private String planName;

    /**
     * The status the membership was created with — {@code PENDING} for anybody but an administrator
     * on hc-patient's side.
     *
     * <p>A free string rather than an enum, following {@link #state} and for the same reason: the
     * vocabulary is hc-patient's, they ship five values today
     * ({@code PENDING, ACTIVE, CANCELLED, EXPIRED, SUSPENDED}), and binding it here would turn "they
     * added a value" into "this service refuses a message".
     *
     * <p><b>It is a fact about the moment of choosing and not a live status.</b> Their
     * {@code MembershipResource} publishes on {@code POST} alone — {@code PUT} and {@code PATCH}
     * write the status and announce nothing — so a membership approved on their side afterwards says
     * so on no topic, and this field goes on reading {@code PENDING}. That is a gap in the contract
     * rather than in this code; item 54's return leg is what closes it from this end. Nothing here
     * may render it as "the plan this patient holds", which is {@code Patient.plan}, an
     * administrator's own field.
     */
    @Field("plan_status")
    private String planStatus;

    /**
     * When <b>this service</b> first saw the two phases meet on this account, and null until they do.
     *
     * <h2>Why it is stored rather than computed from the two watermarks</h2>
     *
     * <p>{@code last_event_at != null && profile_event_at != null} looks like the same question and is
     * not, because a seeded document can satisfy it without any event having been consumed.
     * {@code DirectoryProjectionService.warnIfTheTwoPhasesNeverJoin} guards the one failure in this
     * contract that looks correct on both sides — the two publishers keying their phases on different
     * identifiers, with both consumer groups at lag zero and nothing dead-lettered — and it is
     * suppressed as soon as any account has both phases. The {@code test} fixture seeds exactly such an
     * account (it has to: the console's joined row is otherwise unreachable outside production, which
     * is items 45 and 46's lesson), so on {@code quality/}, on {@code deploy/e2e/} and under
     * {@code ng serve} the guard could never fire — the one machine short of production where somebody
     * would want it, and precisely where a mismatched key would first be seen.
     *
     * <p>This field is written only by the consumer, only on the write that brings the second phase in,
     * and never by the seed. {@code DevelopmentDataInitializerTest} asserts that no seeded link carries
     * it, so re-silencing the guard from the fixture is a failing test rather than a quiet regression.
     *
     * <p><b>This service's own clock, deliberately.</b> Unlike {@link #accountCreatedDate} and
     * {@link #profileCreatedDate}, which are the far side's facts and must never be filled from a
     * frame's arrival, this records an observation <em>by</em> this service — the same kind of value as
     * {@link #firstSeenAt} — so the far side has no clock to lend it and neither phase's
     * {@code occurredAt} would mean what the name says.
     */
    @Field("phases_joined_at")
    private Instant phasesJoinedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DirectorySource getSource() {
        return source;
    }

    public void setSource(DirectorySource source) {
        this.source = source;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public void setExternalKey(String externalKey) {
        this.externalKey = externalKey;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public DirectorySubjectKind getSubjectKind() {
        return subjectKind;
    }

    public void setSubjectKind(DirectorySubjectKind subjectKind) {
        this.subjectKind = subjectKind;
    }

    public Boolean getActivated() {
        return activated;
    }

    public void setActivated(Boolean activated) {
        this.activated = activated;
    }

    public Instant getErasedAt() {
        return erasedAt;
    }

    public void setErasedAt(Instant erasedAt) {
        this.erasedAt = erasedAt;
    }

    public String getLocalId() {
        return localId;
    }

    public void setLocalId(String localId) {
        this.localId = localId;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getAccountCreatedDate() {
        return accountCreatedDate;
    }

    public void setAccountCreatedDate(Instant accountCreatedDate) {
        this.accountCreatedDate = accountCreatedDate;
    }

    public Instant getAccountModifiedDate() {
        return accountModifiedDate;
    }

    public void setAccountModifiedDate(Instant accountModifiedDate) {
        this.accountModifiedDate = accountModifiedDate;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(Instant lastEventAt) {
        this.lastEventAt = lastEventAt;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(String lastEventId) {
        this.lastEventId = lastEventId;
    }

    public String getLastEventType() {
        return lastEventType;
    }

    public void setLastEventType(String lastEventType) {
        this.lastEventType = lastEventType;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public Boolean getProfileComplete() {
        return profileComplete;
    }

    public void setProfileComplete(Boolean profileComplete) {
        this.profileComplete = profileComplete;
    }

    public Boolean getProfileVerified() {
        return profileVerified;
    }

    public void setProfileVerified(Boolean profileVerified) {
        this.profileVerified = profileVerified;
    }

    public Instant getProfileCreatedDate() {
        return profileCreatedDate;
    }

    public void setProfileCreatedDate(Instant profileCreatedDate) {
        this.profileCreatedDate = profileCreatedDate;
    }

    public Instant getProfileModifiedDate() {
        return profileModifiedDate;
    }

    public void setProfileModifiedDate(Instant profileModifiedDate) {
        this.profileModifiedDate = profileModifiedDate;
    }

    public String getProfileLastModifiedBy() {
        return profileLastModifiedBy;
    }

    public void setProfileLastModifiedBy(String profileLastModifiedBy) {
        this.profileLastModifiedBy = profileLastModifiedBy;
    }

    public Instant getProfileEventAt() {
        return profileEventAt;
    }

    public void setProfileEventAt(Instant profileEventAt) {
        this.profileEventAt = profileEventAt;
    }

    public String getProfileEventId() {
        return profileEventId;
    }

    public void setProfileEventId(String profileEventId) {
        this.profileEventId = profileEventId;
    }

    public String getPlanMembershipId() {
        return planMembershipId;
    }

    public void setPlanMembershipId(String planMembershipId) {
        this.planMembershipId = planMembershipId;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getPlanStatus() {
        return planStatus;
    }

    public void setPlanStatus(String planStatus) {
        this.planStatus = planStatus;
    }

    public Instant getPhasesJoinedAt() {
        return phasesJoinedAt;
    }

    public void setPhasesJoinedAt(Instant phasesJoinedAt) {
        this.phasesJoinedAt = phasesJoinedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DirectoryLink)) {
            return false;
        }
        return getId() != null && getId().equals(((DirectoryLink) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DirectoryLink{" +
            "id=" + getId() +
            ", source='" + getSource() + "'" +
            ", externalKey='" + getExternalKey() + "'" +
            ", externalId='" + getExternalId() + "'" +
            ", login='" + getLogin() + "'" +
            ", subjectKind='" + getSubjectKind() + "'" +
            ", state='" + getState() + "'" +
            ", localId='" + getLocalId() + "'" +
            ", lastEventAt='" + getLastEventAt() + "'" +
            ", profileId='" + getProfileId() + "'" +
            ", profileEventAt='" + getProfileEventAt() + "'" +
            ", planCode='" + getPlanCode() + "'" +
            ", planStatus='" + getPlanStatus() + "'" +
            "}";
    }
}
