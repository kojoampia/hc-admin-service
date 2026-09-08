package net.jojoaddison.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.service.dto.ProfileStatusEvent;
import net.jojoaddison.service.dto.SiblingDomainEvent;
import net.jojoaddison.service.dto.SiblingDomainEvent.Disposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads the two sibling envelopes off the wire and answers with a {@link SiblingDomainEvent}.
 *
 * <h2>Every type each producer publishes is named here, and anything else is ignored</h2>
 *
 * <p>Both producers say the same thing in their own javadoc — <em>a consumer meeting a type it does
 * not recognise must ignore it</em> — and this class is where that is kept. The constants below are
 * the complete published set on both streams, read from
 * {@code hc-patient/api/.../service/event/PatientEventType.java} and from hc-professional's two
 * publishers rather than inferred from the frames that happen to be in a topic:
 *
 * <table>
 *   <caption>The thirteen types on the three subscribed topics, and what each one may do here</caption>
 *   <tr><th>Topic</th><th>Type</th><th>Disposition</th></tr>
 *   <tr><td>{@code patient-events}</td><td>{@code AccountCreated}</td>
 *       <td>{@link Disposition#CREATE}, or {@link Disposition#LINK_ONLY} for a care angel</td></tr>
 *   <tr><td></td><td>{@code AccountActivated}</td><td>{@link Disposition#UPDATE_ONLY}</td></tr>
 *   <tr><td></td><td>{@code OnboardingStarted}</td><td>{@link Disposition#CREATE}</td></tr>
 *   <tr><td></td><td>{@code OnboardingStepCompleted}</td><td>{@link Disposition#UPDATE_ONLY}</td></tr>
 *   <tr><td></td><td>{@code OnboardingCompleted}</td><td>{@link Disposition#UPDATE_ONLY}</td></tr>
 *   <tr><td></td><td>{@code CareDelegationChanged}</td><td>{@link Disposition#UPDATE_ONLY}</td></tr>
 *   <tr><td></td><td>{@code DeletionRequestChanged}</td><td>{@link Disposition#UPDATE_ONLY}, and
 *       {@code change=COMPLETED} marks the link erased</td></tr>
 *   <tr><td></td><td>{@code PlanChosen}</td><td>{@link Disposition#UPDATE_ONLY}, and carries a
 *       {@link SiblingDomainEvent.PlanChoice}</td></tr>
 *   <tr><td>{@code hc.professional.registration}</td><td>{@code registration.created}</td>
 *       <td>{@link Disposition#LINK_ONLY} — <b>phase 1</b>, the original envelope</td></tr>
 *   <tr><td></td><td>{@code onboarding.state}</td><td>{@link Disposition#LINK_ONLY}</td></tr>
 *   <tr><td></td><td>{@code AccountCreated}</td>
 *       <td>{@link Disposition#LINK_ONLY} — <b>phase 1</b>, and the only frame carrying
 *       {@code activated}</td></tr>
 *   <tr><td></td><td>{@code AccountActivated}</td><td>{@link Disposition#LINK_ONLY}</td></tr>
 *   <tr><td>{@code hc.professional.entity}</td><td>{@code ProfileStatus}</td>
 *       <td><b>phase 2</b>, and no disposition at all — see below</td></tr>
 * </table>
 *
 * <h2>hc-professional puts two envelopes on one topic, and the discriminator is which key is present</h2>
 *
 * <p>{@code hc.professional.registration} carries both of that product's shapes at once and will go on
 * doing so — their {@code RegistrationEventPublisher} says in its own javadoc that
 * {@code registration.created} and {@code onboarding.state} are <em>"not replaced and not
 * deprecated"</em>, because hc-admin is their one live consumer. Their {@code ProfessionalEvent}
 * javadoc names the rule for reading them together: <em>"a reader of either topic dispatches on which
 * of {@code type} and {@code eventType} is present"</em>, and {@link #parseProfessionalEvent} does
 * exactly that and nothing cleverer.
 *
 * <table>
 *   <caption>The two hc-professional envelopes, side by side</caption>
 *   <tr><th></th><th>{@code DomainEventEnvelope}</th><th>{@code ProfessionalEvent}</th></tr>
 *   <tr><td>discriminator</td><td>{@code eventType}</td><td>{@code type}</td></tr>
 *   <tr><td>body</td><td>{@code payload}</td><td>{@code data}</td></tr>
 *   <tr><td>subject</td><td>inside {@code payload}</td>
 *       <td>{@code subject:{email, login, accountId}}</td></tr>
 *   <tr><td>who sends it</td><td>gateway and api</td><td>gateway and api</td></tr>
 * </table>
 *
 * <p><b>Phase 2 is the {@code ProfessionalEvent} shape and only ever that shape.</b> It was read as
 * {@code eventType}/{@code payload} until 2026-09-08, against a producer that has never sent one on
 * that topic for a profile status — the whole of the phase-2 path was therefore unreachable, on a
 * healthy consumer group at lag zero. That is recorded rather than quietly fixed because the cause is
 * worth more than the correction: the shape was written from item 47's prose and from one grepped-to
 * method, not from hc-professional's producer. <b>Read the whole producer.</b>
 *
 * <p><b>Phase 2 has no {@link Disposition} and the omission is the contract.</b> The three
 * dispositions answer "may this event open a local record?", and for a profile status the answer is
 * not merely no but <em>never</em>: neither phase carries a {@code role} or a {@code licenceNumber},
 * so a {@code Professional} cannot be constructed from any number of these frames, however complete
 * the profile says it is. {@link net.jojoaddison.service.dto.ProfileStatusEvent} is therefore its own
 * type with no such field, rather than a fourth value nobody could apply. Backlog item 47.
 *
 * <p><b>Two of those rows were the whole of a defect.</b> Until 2026-09-05 this class named two types
 * and used them only to decide {@code activated}, and there was no type filter on the creation path
 * at all — so every one of the other five, and every type either product adds next, opened a
 * {@code Patient}. The two that mattered: a care-angel nomination publishes {@code AccountCreated}
 * keyed on the <em>angel's</em> address, which made every nomination a patient; and
 * {@code DeletionRequestChanged/COMPLETED} is published <em>after</em> the profile has been erased,
 * so the event whose entire meaning is "erase this person" was what made this service start storing
 * their address.
 *
 * <h2>Nothing here throws</h2>
 *
 * <p>Every method answers {@link Optional#empty()} for anything it cannot make sense of, and the
 * consumers treat empty as "not for us" rather than as an error. That is the whole reason this is a
 * separate class with its own tests: <b>an exception thrown out of a Spring Cloud Stream consumer is
 * redelivered, and while that is happening the partition it arrived on makes no progress</b> — so
 * one message this service cannot parse would hold up every subject whose key hashes there. These
 * topics are shared and neither producer has promised this service a schema. Refusing loudly is the
 * right behaviour for a producer validating its own payload and the wrong behaviour for a consumer
 * reading somebody else's.
 *
 * <p>That leniency stops at the parse. A failure <em>downstream</em> of here — a Mongo write that
 * could not be made — is not a bad message and is deliberately not swallowed; see
 * {@link net.jojoaddison.config.DirectoryEventConsumers}.
 *
 * <p>The counterpart of the leniency is that a message which is genuinely for this service and
 * genuinely malformed is dropped with a {@code warn} and nothing else. That is a deliberate trade,
 * because "the directory did not update" has no other evidence behind it. An unrecognised
 * <em>type</em> is logged at {@code debug} instead: it is the normal, expected condition on somebody
 * else's topic, and a warn per frame would fill the log during a backfill with something nobody
 * should act on.
 *
 * <h3>That warn names a fingerprint of the frame, and no longer an excerpt of it</h3>
 *
 * <p>It logged the first hundred characters of the payload until 2026-09-07, and that was <b>the one
 * statement in this service whose content nobody on this side had vetted</b>: the frame is by
 * definition one this parser could not read, on a topic this service does not own, so what those
 * hundred characters hold is whatever a sibling — or anything else with write access to the topic —
 * put there. Everything else in the item 43 pass is about keeping a <em>known</em> identifier out of
 * an estate-wide log store; leaving open a channel that copies <em>unknown</em> content into the
 * same place would have been the same defect with the blame moved.
 *
 * <p><b>It is not a loss of diagnosability, and on the question actually asked it is a gain.</b>
 * What this line has to answer is "is this one frame being redelivered, or many different ones?" —
 * and two frames that differ after the hundredth character are indistinguishable under truncation
 * and distinct under a digest. The topic and the parser's own complaint are still named, so the
 * remaining question is what the bytes say; that is answered deliberately, by reading the frame off
 * the broker with a console consumer, rather than by mirroring every such frame into a fourteen-day
 * queryable store shared with five other products on the chance that somebody looks.
 *
 * <p>One thing this does <b>not</b> control and should not be assumed to: {@code e.toString()} is
 * the parser's own exception message, and Jackson historically quoted the offending input in it.
 * {@code StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION} has defaulted to disabled since Jackson 2.16
 * and this service is well past that, so the message reads {@code REDACTED} where the source used to
 * be — but that is a library default rather than something this code asserts, so
 * {@code SiblingEventParserTest} pins the whole line rather than the argument, and would fail if the
 * default were ever turned back on.
 *
 * <h2>Timestamps are read from both forms, and a frame without one is not an event</h2>
 *
 * <p>Both products put a real {@code Instant} on a record in one place and an
 * {@code Instant.now().toString()} into a {@code Map} in another — hc-professional's api publishes
 * the {@code DomainEventEnvelope} record while its gateway builds a {@code LinkedHashMap}, and
 * hc-patient publishes the {@code PatientEvent} record from both of its applications. So
 * {@code occurredAt} arrives as an ISO-8601 string from some producers and as whatever that
 * application's ObjectMapper makes of an {@code Instant} — a string, or a numeric epoch — from
 * others, and that is a setting in repositories this one does not own. Both forms are read rather
 * than one being assumed.
 *
 * <p><b>A frame whose {@code occurredAt} cannot be read at all is ignored, and until 2026-09-05 it
 * fell back to {@code Instant.now()}.</b> That fallback read as the cautious choice and was the
 * opposite of one. {@code occurredAt} is the watermark, so stamping an unreadable frame with the
 * present <b>freezes its subject against every event older than the moment it arrived</b> — and
 * these consumer groups start at the earliest offset, so on the first run "now" is later than every
 * frame in the topic. One frame with an unreadable timestamp would therefore discard the rest of
 * that subject's history as stale, during exactly the backfill that history is being read for.
 * Silent, and indistinguishable from the consumer having stopped.
 *
 * <p>Ignoring the frame loses one event. The fallback lost every event about that person that had
 * not yet been applied, which is the strictly worse of the two, and it is a case neither producer can
 * reach today: both always set the field.
 *
 * <h2>Why this is in {@code ..service..} rather than {@code ..broker..}</h2>
 *
 * <p>It reads wire formats, so {@code ..broker..} is where it looks as though it should live. That
 * package is in no layer as far as {@code TechnicalStructureTest} is concerned, which means a class
 * in it may reference neither {@code ..service..} nor {@code ..domain..} — and this one returns a
 * {@code service.dto} type carrying a {@code domain.enumeration} value. See
 * {@link net.jojoaddison.config.DirectoryEventConsumers} for the fuller note, including why
 * {@code KafkaConsumer} appears to get away with it and does not.
 */
@Component
public class SiblingEventParser {

    // --- hc-patient's published set, all eight of it ----------------------------------------------
    //
    // Seven until 2026-09-08, when PlanChosen was added at the bottom. The count is kept because the
    // point of it is "this is the complete set, read from their PatientEventType" rather than "these
    // are the ones somebody happened to need" — and it moves when the set does.

    /** Registration on hc-patient's gateway — <b>and</b> a care-angel nomination, which is the trap. */
    private static final String PATIENT_ACCOUNT_CREATED = "AccountCreated";

    /** Activation, and the second of the two frames a care-angel nomination emits. */
    private static final String PATIENT_ACCOUNT_ACTIVATED = "AccountActivated";

    /** The patient's record now exists on the far side. The one event binding an email to a patientId. */
    private static final String PATIENT_ONBOARDING_STARTED = "OnboardingStarted";

    private static final String PATIENT_ONBOARDING_STEP_COMPLETED = "OnboardingStepCompleted";
    private static final String PATIENT_ONBOARDING_COMPLETED = "OnboardingCompleted";

    /** Keyed on the <b>patient's</b> address, with the angel's carried in {@code data.angelEmail}. */
    private static final String PATIENT_CARE_DELEGATION_CHANGED = "CareDelegationChanged";

    /** {@code RAISED}, {@code CANCELLED}, {@code REJECTED} — or {@code COMPLETED}, after the erasure. */
    private static final String PATIENT_DELETION_REQUEST_CHANGED = "DeletionRequestChanged";

    /**
     * A patient chose a membership tier — backlog item 48, and hc-patient's item 18.
     *
     * <p><b>The string is the contract and a rename is a two-repository change.</b> Their
     * {@code PatientEventType.PLAN_CHOSEN} says so in those terms: <em>"a rename here is not a compile
     * error there, it is an event their {@code switch} silently ignores"</em>. It is pinned as a
     * literal on both sides — their {@code MembershipPlanEventTest} and this repository's
     * {@code SiblingEventParserTest} — because nothing else in the estate enforces a contract between
     * two repositories that cannot be compiled against each other.
     *
     * <p>{@link Disposition#UPDATE_ONLY}: a plan choice is a fact about a patient, not a subject in
     * its own right. hc-patient publishes it only after a {@code Membership} is written, and a
     * membership is written only for a patient whose account and onboarding events came first on this
     * same key and therefore this same partition — so a plan choice for a subject this service has
     * never seen means the earlier events were missed, and inventing a patient from a tier code is the
     * fabrication items 27(a), 33, 46 and 47 each refuse in turn.
     */
    private static final String PATIENT_PLAN_CHOSEN = "PlanChosen";

    // --- the four keys hc-patient's PlanChosen carries in `data`, and there are exactly four ------
    //
    // Named as constants rather than written inline because they are the other half of the contract
    // above: the type string decides whether the frame is read at all, and these decide whether
    // anything is read out of it. Their MembershipPlanEventTest asserts `containsOnlyKeys` over
    // exactly this set, "a key quietly added here is a key hc-admin has not agreed to, and one
    // quietly dropped is one they are still reading".
    //
    // PLAN_CODE and PLAN_NAME are their `Membership.plan` and `Membership.name` — their document has
    // no `code` field, and both of their clients' choosePlan writes the tier's code into `plan`.

    private static final String PLAN_MEMBERSHIP_ID = "membershipId";
    private static final String PLAN_CODE = "planCode";
    private static final String PLAN_NAME = "planName";
    private static final String PLAN_STATUS = "status";

    /** The {@code data} discriminator that says the far side has already erased the subject. */
    private static final String DELETION_COMPLETED = "COMPLETED";

    /**
     * What a care-angel nomination puts in {@code data.authorities}, and the reason it gives.
     *
     * <p>Either alone is enough. The authority is the fact; {@code reason} is hc-patient's own label
     * for why the account was made, and is checked as well because the two are set at one call site
     * and a change to that site is more likely to keep one than both.
     */
    private static final String CARE_ANGEL_AUTHORITY = "ROLE_ANGEL";
    private static final String CARE_ANGEL_REASON = "careAngelNomination";

    // --- hc-professional's original envelope on the registration topic ---------------------------

    private static final String PROFESSIONAL_REGISTRATION_CREATED = "registration.created";
    private static final String PROFESSIONAL_ONBOARDING_STATE = "onboarding.state";

    // --- hc-professional's estate-shaped account events, on the same topic ------------------------

    /**
     * The gateway's {@code AccountCreated}, and <b>the only frame in this product that carries
     * {@code activated}</b>.
     *
     * <p>It is on {@code hc.professional.registration} beside {@code registration.created} rather than
     * instead of it — their publisher's class javadoc says so, and it means one registration puts
     * three frames on the topic under two envelopes, all keyed identically. Reading only the older
     * envelope, which this class did until 2026-09-08, therefore discarded the one frame that answers
     * the question the console's {@code Activated} column asks.
     */
    private static final String PROFESSIONAL_ACCOUNT_CREATED = "AccountCreated";

    /**
     * The gateway's {@code AccountActivated}: the activation link was followed and the key consumed.
     *
     * <p>Published nowhere before their item 47 work, so <em>"the one point at which a clinician's
     * account stops being a pending registration was invisible to the estate"</em>, in their own
     * words. This is the frame that moves {@code activated} to true after the fact.
     */
    private static final String PROFESSIONAL_ACCOUNT_ACTIVATED = "AccountActivated";

    // --- hc-professional's published set on the entity topic, which is phase 2 --------------------

    /**
     * <b>The only type accepted on {@code hc.professional.entity}</b>, per the architect's decision 3
     * of 2026-09-08.
     *
     * <p>That topic already carries {@code entity.created} frames for {@code Profile} — the older
     * {@code DomainEventEnvelope} shape, keyed on the login rather than on a {@code User.id} — and this
     * consumer group reads from the earliest offset. Accepting them as profile statuses would
     * manufacture roughly thirty phantom "identity not on file" rows on the day of deploy and roughly
     * double the console's awaiting count, which is the moved-figure failure item 46 § 2 forbids. They
     * are also not the contract: an {@code entity.created} carries {@code entityType},
     * {@code entityId} and {@code accountId} and none of {@code isComplete}, {@code isVerified} or
     * {@code lastModifiedBy}, so every column this table exists for would read "not reported".
     *
     * <p>So the {@code entityType == "Profile"} filter that used to stand here is gone rather than
     * loosened. It was reached only by frames in the older envelope, and those are now refused one
     * step earlier, by type — which is the rule this class already applies to every other unmodelled
     * type and is what makes hc-professional free to add to their own topic without breaking this one.
     */
    private static final String PROFESSIONAL_PROFILE_STATUS = "ProfileStatus";

    private static final Logger LOG = LoggerFactory.getLogger(SiblingEventParser.class);

    private final ObjectMapper objectMapper;

    public SiblingEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * A frame from {@code patient-events}.
     *
     * @param headerKey the {@code patientKey} header hc-patient sets as the Kafka partition key, or
     *                  null. Preferred over the payload's own copy because it is what the broker
     *                  actually partitioned on, so keying on it is keying on the thing that
     *                  guarantees this subject's events arrive in order on one thread. The payload
     *                  field is the same value and is the fallback for a frame whose headers were
     *                  not carried across — a header is metadata and can be dropped by a bridge, a
     *                  mirror or a test harness, where the payload cannot.
     */
    public Optional<SiblingDomainEvent> parsePatientEvent(byte[] payload, String headerKey) {
        JsonNode node = read(payload, "patient-events");
        if (node == null) {
            return Optional.empty();
        }
        JsonNode subject = node.path("subject");
        JsonNode data = node.path("data");

        String type = text(node, "type");
        String key = normaliseKey(headerKey != null && !headerKey.isBlank() ? headerKey : text(subject, "email"));
        if (type == null || key == null) {
            LOG.warn("Ignoring a patient-events frame with no {}", type == null ? "type" : "subject key");
            return Optional.empty();
        }

        Instant occurredAt = occurredAt(node, type);
        if (occurredAt == null) {
            return Optional.empty();
        }

        boolean careAngel = isCareAngelNomination(data);
        Disposition disposition = patientDisposition(type, careAngel);
        if (disposition == null) {
            LOG.debug("Ignoring a patient-events frame of type {}, which this service does not model", type);
            return Optional.empty();
        }

        // Only the two account events say anything about signing in, and a nomination's do not count:
        // an angel's account being activated is not a patient becoming active. It cannot reach a
        // Patient anyway — the merge rule only ever reads this for a subject that has a local record,
        // and an angel has none — but leaving it true would put a fact about the wrong person one
        // refactor away from being acted on.
        boolean activated =
            !careAngel &&
            (PATIENT_ACCOUNT_ACTIVATED.equals(type) || (PATIENT_ACCOUNT_CREATED.equals(type) && data.path("activated").asBoolean(false)));

        return Optional.of(
            new SiblingDomainEvent(
                DirectorySource.HC_PATIENT,
                text(node, "eventId"),
                type,
                occurredAt,
                key,
                text(subject, "email"),
                text(subject, "login"),
                text(subject, "patientId"),
                type,
                activated,
                disposition,
                subjectKindFor(disposition, careAngel),
                // hc-patient's envelope carries no account dates and is not asked to grow any: the
                // pair exists for the professional contract's phase 1, and the patient directory has
                // never shown them. Null here rather than firstSeenAt/lastEventAt, which answer a
                // different question and would be wrong under a heading saying "created".
                null,
                null,
                PATIENT_DELETION_REQUEST_CHANGED.equals(type) && DELETION_COMPLETED.equals(text(data, "change")),
                planChoice(type, data)
            )
        );
    }

    /**
     * A frame from {@code hc.professional.registration} — <b>phase 1</b>, in either of the two
     * envelopes that topic carries.
     *
     * <p><b>The dispatch is on which discriminator the frame has</b>, which is the rule
     * hc-professional's own {@code ProfessionalEvent} states: a {@code type} means the estate-shaped
     * envelope and its {@code AccountCreated} / {@code AccountActivated}; an {@code eventType} means
     * their original {@code DomainEventEnvelope} and its {@code registration.created} /
     * {@code onboarding.state}. Both are live, both are keyed on the same {@code accountId}, and both
     * write the same link — see {@link #parseProfessionalAccountEvent} for what only the newer pair
     * can say.
     *
     * <p>The subject key is {@code payload.accountId} rather than the email: that is what both
     * producers key the topic on, and {@code onboarding.state} carries no email at all. Taking the
     * email where it is available and the accountId where it is not would give one clinician two
     * links.
     *
     * <p>Both types are {@link Disposition#LINK_ONLY}. No local row is created for a clinician at
     * all — {@code Professional} requires a {@code role} and a {@code licenceNumber}, and neither is
     * on the wire — so the distinction {@code CREATE} draws does not arise on this stream.
     *
     * <p>The header is not read here. hc-professional sets {@code KafkaHeaders.KEY} directly, which
     * the binder consumes as the record key rather than exposing under a name of its own — the
     * payload is the reliable copy on this stream, unlike hc-patient's.
     *
     * <h2>This is <b>phase 1</b>, and on this side it is mostly a mapping exercise</h2>
     *
     * <p>Backlog item 47 specifies phase 1 as
     * {@code AccountStatus{accountId, login, email, activated, createdDate, modifiedDate}}, from
     * hc-professional's <b>gateway</b>, on this topic. Four of the six already arrive under exactly
     * that name and two are new:
     *
     * <table>
     *   <caption>The phase-1 contract against what {@code registration.created} publishes today</caption>
     *   <tr><th>Contract</th><th>On the wire today</th><th>Stored as</th></tr>
     *   <tr><td>{@code accountId}</td><td>yes</td>
     *       <td>{@code external_key} and {@code external_id}</td></tr>
     *   <tr><td>{@code login}</td><td>yes</td><td>{@code login}</td></tr>
     *   <tr><td>{@code email}</td><td>yes</td><td>{@code email}</td></tr>
     *   <tr><td>{@code activated}</td><td>on {@code AccountCreated}, not here</td>
     *       <td>{@code activated}</td></tr>
     *   <tr><td>{@code createdDate}</td><td><b>no — on no frame at all</b></td>
     *       <td>{@code account_created_date}</td></tr>
     *   <tr><td>{@code modifiedDate}</td><td><b>no — on no frame at all</b></td>
     *       <td>{@code account_modified_date}</td></tr>
     * </table>
     *
     * <p><b>The two dates are on nothing hc-professional's gateway publishes</b>, measured on their
     * {@code origin/main} 2026-09-08: {@code AccountCreated} carries {@code authorities},
     * {@code langKey} and {@code activated}, and {@code AccountActivated} carries {@code activatedAt}.
     * They are read here under the contract's names so they light up the day that changes, and
     * nothing is substituted for them in the meantime. The obvious substitute is the envelope's own
     * {@code occurredAt}, and it is refused: for {@code AccountCreated} it is within milliseconds of
     * the truth and for every other frame it is not, so accepting it would put a date that is
     * sometimes the account's and sometimes the frame's under a heading that reads "created" — item
     * 45's defect with a timestamp instead of an id. The column says "not reported", which is what it
     * is, and what hc-professional has to add is now nameable to the field.
     *
     * <p><b>The names are read exactly as the contract states them, and no alias is accepted.</b> An
     * earlier draft of item 47 said {@code username} and {@code isActivated}; the contract was revised
     * onto the names both products already use, precisely so that there would not be two spellings of
     * one field across two repositories. Reading both would reintroduce the ambiguity the revision
     * removed — the {@code admin-service} / {@code hcadminservice} mismatch is what that costs, and it
     * cost every entity call a 404.
     *
     * <p>The fields not on the wire read as null, and null travels all the way to the screen as "not
     * reported". None of them is defaulted.
     *
     * <p><b>{@code activated} is not derived, and until 2026-09-07 it was.</b> This method answered
     * {@code PROFESSIONAL_REGISTRATION_CREATED.equals(type)} — "a registration is an account that can
     * sign in" — which is an inference about somebody else's account made from the fact that a frame
     * arrived. Item 47 forbids it in those terms: activation is the account's own state, an account
     * can be deactivated afterwards, and this service would never hear about it. So the field is read
     * or it is null, and null is carried through as "no event has said" rather than flattened to
     * false. A console that has never shown this at all loses nothing by saying so.
     *
     * <p><b>De-inferring it was right and reading nothing in its place was the defect.</b> The value
     * has been on this topic since hc-professional's {@code cdbbbf4}, on {@code AccountCreated}, in an
     * envelope this method dropped with a {@code warn} — so the column would have read "not reported"
     * for ever while the fact sat one frame away. {@link #parseProfessionalAccountEvent} is where it
     * is read now.
     */
    public Optional<SiblingDomainEvent> parseProfessionalEvent(byte[] payload) {
        JsonNode node = read(payload, "hc.professional.registration");
        if (node == null) {
            return Optional.empty();
        }

        // Which envelope this is, decided the way hc-professional's own ProfessionalEvent javadoc
        // says to decide it: on which discriminator is present, never on guessing at the body.
        String estateType = text(node, "type");
        if (estateType != null) {
            return parseProfessionalAccountEvent(node, estateType);
        }

        JsonNode content = node.path("payload");

        String type = text(node, "eventType");
        // Trimmed exactly as phase 2 trims it, and case-preserved exactly as phase 2 preserves it.
        // The two phases join on this string and the join is an equality test; any normalisation
        // applied to one side and not the other is a permanently unpaired row.
        String accountId = trimmed(text(content, "accountId"));
        if (type == null || accountId == null) {
            LOG.warn("Ignoring an hc.professional.registration frame with no {}", type == null ? "eventType" : "accountId");
            return Optional.empty();
        }
        if (!PROFESSIONAL_REGISTRATION_CREATED.equals(type) && !PROFESSIONAL_ONBOARDING_STATE.equals(type)) {
            // The topic also carries nothing else today, but hc-professional owns it and may add to
            // it, and hc.professional.entity's three types show what that looks like when it happens.
            LOG.debug("Ignoring an hc.professional.registration frame of type {}, which this service does not model", type);
            return Optional.empty();
        }

        Instant occurredAt = occurredAt(node, type);
        if (occurredAt == null) {
            return Optional.empty();
        }

        // The far side's own word for how far onboarding has got, or nothing at all. Only
        // `onboarding.state` carries one; `registration.created` has no `state` field, because a
        // registration is not a stage of onboarding.
        //
        // THIS FELL BACK TO THE EVENT TYPE until 2026-09-07, on the reasoning that the link should
        // "always record something a reader can act on rather than sometimes recording null". It
        // recorded the wrong thing instead: `state` is the one field the console renders verbatim
        // on the awaiting-a-record panel, so a freshly registered clinician — the exact case backlog
        // item 46 was reported for — read "no record in this directory · registration.created", a
        // wire identifier printed as if it were a status. Nothing is lost by dropping it: the type
        // is already stored in its own right on `last_event_type`, and the panel renders no suffix
        // at all when there is no state, which is the honest answer for somebody who has just
        // registered and started nothing.
        //
        // hc-patient's half of this parser deliberately does the opposite and stores the type here.
        // That is not an inconsistency to tidy away: on that stream the type IS the lifecycle
        // (`AccountCreated`, `OnboardingStarted`), where on this one the two are separate fields
        // published by a producer that models them separately.
        String state = text(content, "state");

        return Optional.of(
            new SiblingDomainEvent(
                DirectorySource.HC_PROFESSIONAL,
                text(node, "eventId"),
                type,
                occurredAt,
                accountId,
                text(content, "email"),
                text(content, "login"),
                accountId,
                state,
                // Read, never inferred — see this method's javadoc. Null when the frame says nothing,
                // which is every onboarding.state frame and every registration published before
                // hc-professional's phase-1 change ships.
                bool(content, "activated"),
                Disposition.LINK_ONLY,
                DirectorySubjectKind.PROFESSIONAL,
                // The account's own dates, which this service had no field for until item 47. Absent
                // from registration.created today, so null until hc-professional's phase-1 change
                // ships — and null is what the console renders as "not reported", never as an epoch.
                timestamp(content, "createdDate"),
                timestamp(content, "modifiedDate"),
                false,
                // Plan choice is hc-patient's alone: a clinician has no membership tier, and nothing
                // on either of hc-professional's topics carries one.
                null
            )
        );
    }

    /**
     * The estate-shaped half of <b>phase 1</b>: {@code AccountCreated} and {@code AccountActivated},
     * from hc-professional's gateway, on {@code hc.professional.registration}.
     *
     * <p>These are the frames that carry {@code activated}, and they are the reason this method exists
     * rather than a branch inside the one above. Everything else about the account —
     * {@code accountId}, {@code login}, {@code email} — is already on {@code registration.created},
     * so on its own this is a second copy of facts this service holds; what only this pair can say is
     * whether the account can be signed into, which is a column on the console and was reading "not
     * reported" for every clinician on every stack.
     *
     * <p><b>The subject is the envelope's own {@code subject}, not a field in {@code data}.</b> That is
     * the shape difference between the two envelopes and the whole reason they are parsed apart: the
     * older one puts the subject inside {@code payload}, this one names it beside the body. Both
     * carry the same {@code accountId} and the topic is keyed on it, so a clinician has one link
     * however many of the three frames arrive.
     *
     * <h2>Activation, and why {@code AccountActivated} setting it true is a reading rather than an
     * inference</h2>
     *
     * <p>The prohibition item 47 states is on inferring an account's state from <em>a frame having
     * arrived</em> — the discarded "a registration is an account that can sign in". This is not that.
     * {@code AccountActivated}'s entire content is that the activation link was followed and the key
     * consumed; taking it as {@code activated = true} is reading the event, and refusing to would mean
     * holding "not reported" for an account whose activation is the only thing the frame is about.
     * hc-patient's half of this parser has always read its identically-named event the same way, and
     * the two agreeing is worth more than a rule applied by its letter.
     *
     * <p><b>{@code AccountCreated} is read, never assumed.</b> Its {@code data.activated} is false for
     * a self-service registration awaiting its email link and may be true for an
     * administrator-created account, which is precisely the distinction the column exists to draw, so
     * an absent field stays null rather than becoming either.
     *
     * <p>No {@code state}: neither frame carries one, and neither is a stage of onboarding.
     * {@code DirectoryProjectionService.recordEvent} therefore leaves a state an earlier
     * {@code onboarding.state} wrote alone rather than clearing it — the three frames of one
     * registration are keyed identically and arrive in order, so an {@code AccountCreated} landing
     * after {@code onboarding.state} is the ordinary case and not an edge one.
     */
    private Optional<SiblingDomainEvent> parseProfessionalAccountEvent(JsonNode node, String type) {
        if (!PROFESSIONAL_ACCOUNT_CREATED.equals(type) && !PROFESSIONAL_ACCOUNT_ACTIVATED.equals(type)) {
            LOG.debug("Ignoring an hc.professional.registration frame of type {}, which this service does not model", type);
            return Optional.empty();
        }

        JsonNode subject = node.path("subject");
        JsonNode data = node.path("data");

        // Trimmed and case-preserved exactly as the other envelope and as phase 2 — the three of them
        // join on this string by equality, and a normalisation applied to one is an unpaired row.
        String accountId = trimmed(text(subject, "accountId"));
        if (accountId == null) {
            LOG.warn("Ignoring an hc.professional.registration {} frame with no subject accountId", type);
            return Optional.empty();
        }

        Instant occurredAt = occurredAt(node, type);
        if (occurredAt == null) {
            return Optional.empty();
        }

        Boolean activated = PROFESSIONAL_ACCOUNT_ACTIVATED.equals(type) ? Boolean.TRUE : bool(data, "activated");

        return Optional.of(
            new SiblingDomainEvent(
                DirectorySource.HC_PROFESSIONAL,
                text(node, "eventId"),
                type,
                occurredAt,
                accountId,
                text(subject, "email"),
                text(subject, "login"),
                accountId,
                // No state on either frame, and nothing is invented for the field: the console renders
                // it verbatim, which is what made "registration.created" appear there as a status.
                null,
                activated,
                Disposition.LINK_ONLY,
                DirectorySubjectKind.PROFESSIONAL,
                // Under the contract's names, and absent from both frames today — see this class's
                // phase-1 table for what their gateway actually sends and why occurredAt is not
                // substituted.
                timestamp(data, "createdDate"),
                timestamp(data, "modifiedDate"),
                false,
                // As above: no membership tier exists on this stream in any envelope.
                null
            )
        );
    }

    /**
     * A frame from {@code hc.professional.entity} — <b>phase 2</b>, the profile status.
     *
     * <p>The contract, from backlog item 47:
     * {@code ProfileStatus{profileId, accountId, isComplete, isVerified, createdDate, modifiedDate,
     * lastModifiedBy}}. Identifiers, two booleans and three timestamps, and deliberately nothing
     * else — no role, no licence, no name, no address — so this topic stays free of anything a
     * clinician could be identified or credentialed by, and this service still cannot build a
     * {@code Professional} out of it. That is the answer rather than an omission to fill in later.
     *
     * <h2>The envelope is {@code ProfessionalEvent}, and the fields are read off it directly</h2>
     *
     * <p>{@code type}, and the body is {@code data} — not {@code eventType} and {@code payload},
     * which is their <em>other</em> envelope and is what this method read until 2026-09-08. See the
     * class javadoc: that error made the whole phase-2 path unreachable while every check stayed
     * green, and it came of writing the reader from a specification rather than from the producer.
     *
     * <h2>The filter is here, and it is most of the value of this method</h2>
     *
     * <p>This topic is hc-professional's general entity stream: {@code entity.created} for every
     * entity type they model, plus {@code message.created} and {@code compliance.alert}. Item 35
     * measured it at <b>82% {@code Task}</b>, which this service has no collection for. <b>One test
     * refuses all of it</b> — the type must be {@code ProfileStatus} — and that is the architect's
     * decision 3 of 2026-09-08 rather than a simplification taken here.
     *
     * <p>The {@code entityType == "Profile"} check that stood beside it is <b>gone</b>, and its
     * absence is the decision. It exists on no {@code ProfileStatus}; it exists only on the older
     * {@code entity.created} frames, which are now refused a step earlier for carrying none of the
     * fields this contract is made of. Reinstating it would only ever admit those frames back.
     *
     * <h2>An accepted frame with no {@code accountId} is refused loudly</h2>
     *
     * <p>{@code accountId} is the join to phase 1 and the key of the row this would be written to.
     * A profile frame that has passed the filter and carries none is not a message for somebody else
     * — it is a message for this service that can never be paired with an account, which is the exact
     * failure item 47 says looks correct on both sides. So it is a {@code warn} naming the profile,
     * not a {@code debug} naming the type.
     *
     * <p>Nothing here throws, like everything else in this class: a phase-2 frame this service cannot
     * use answers {@link Optional#empty()} and the offset moves on.
     */
    public Optional<ProfileStatusEvent> parseProfessionalProfileEvent(byte[] payload) {
        JsonNode node = read(payload, "hc.professional.entity");
        if (node == null) {
            return Optional.empty();
        }
        String type = text(node, "type");
        if (!PROFESSIONAL_PROFILE_STATUS.equals(type)) {
            // The 82%, plus every legacy entity.created for a Profile. At debug, because it is the
            // normal condition on a topic this service shares with entities it does not model, and a
            // warn per frame would fill the log during the backfill with something nobody should act
            // on. `type` is null for a frame in their older envelope, and the line says so rather
            // than printing "null", because "an eventType-shaped frame" is the useful fact.
            LOG.debug(
                "Ignoring an hc.professional.entity frame of type {}, which this service does not model",
                type == null ? "none — an eventType-shaped envelope" : type
            );
            return Optional.empty();
        }

        JsonNode content = node.path("data");

        // profileId, under the name the contract gives it, and no alias — the same rule phase 1
        // follows and for the same reason. Their older envelope's `entityId` is the same identifier
        // and is deliberately not read; those frames no longer reach this line at all.
        String profileId = text(content, "profileId");

        // From `data`, and deliberately not falling back to the envelope's `subject.accountId`, which
        // their publisher sets to the same value. One field is read in one place, so a producer that
        // started filling the two differently would show up as an unpaired row and a warning rather
        // than being silently absorbed by whichever copy happened to be right. Their own comment on
        // that subject calls it a repeat of what `data` carries, which names which is the original.
        String accountId = trimmed(text(content, "accountId"));
        if (accountId == null) {
            LOG.warn(
                "Ignoring an hc.professional.entity {} for a profile with no accountId — there is nothing to join it to, " +
                "and a profile status that can never be paired with an account is invisible on the console rather than wrong",
                type
            );
            return Optional.empty();
        }

        Instant occurredAt = occurredAt(node, type);
        if (occurredAt == null) {
            return Optional.empty();
        }

        return Optional.of(
            new ProfileStatusEvent(
                text(node, "eventId"),
                type,
                occurredAt,
                accountId,
                profileId,
                bool(content, "isComplete"),
                bool(content, "isVerified"),
                timestamp(content, "createdDate"),
                timestamp(content, "modifiedDate"),
                // hc-professional's LOGIN, not an accountId and not this service's — see
                // ProfileStatusEvent.lastModifiedBy, which reads their auditor rather than the
                // contract's prose. Trimmed for the same reason accountId is, and otherwise stored
                // exactly as sent.
                trimmed(text(content, "lastModifiedBy"))
            )
        );
    }

    /**
     * What an hc-patient event of this type may do here, or null for a type this service does not
     * model.
     *
     * <p>Exhaustive over {@code PatientEventType} by design, and the {@code default} answers null
     * rather than guessing. The two lines that carry the weight are the first — a nomination is a
     * link and never a patient — and the last, where an erasure may update somebody already known
     * and may not introduce them.
     */
    private Disposition patientDisposition(String type, boolean careAngel) {
        return switch (type) {
            case PATIENT_ACCOUNT_CREATED -> careAngel ? Disposition.LINK_ONLY : Disposition.CREATE;
            // The one event that proves a Profile exists on the far side, and the only one carrying
            // the patientId. It creates as well as AccountCreated does, which is also what lets a
            // care angel who later registers as a patient in their own right become one here: their
            // account already exists, so hc-patient publishes no second AccountCreated for them.
            case PATIENT_ONBOARDING_STARTED -> Disposition.CREATE;
            case PATIENT_ACCOUNT_ACTIVATED,
                PATIENT_ONBOARDING_STEP_COMPLETED,
                PATIENT_ONBOARDING_COMPLETED,
                PATIENT_CARE_DELEGATION_CHANGED,
                PATIENT_DELETION_REQUEST_CHANGED,
                // A plan choice too, and for the strongest form of the reason: the record it is
                // about must already exist here, because hc-patient writes the Membership only for
                // a patient whose AccountCreated and OnboardingStarted went out earlier on this
                // same key. See PATIENT_PLAN_CHOSEN.
                PATIENT_PLAN_CHOSEN -> Disposition.UPDATE_ONLY;
            default -> null;
        };
    }

    /**
     * The tier a {@code PlanChosen} names, or null on every other type.
     *
     * <p><b>Nothing is defaulted and nothing is validated.</b> An absent {@code planCode} stays null
     * rather than becoming the empty string, and a code this service's catalogue does not hold is
     * carried through unchanged — resolving it is {@code DirectoryProjectionService}'s job, at the
     * point where a mismatch can be announced with a subject beside it, and it is deliberately not
     * done here: a parser that dropped an unresolvable tier would make the one failure item 51
     * predicted — <em>"the event will arrive, parse, and resolve to nothing, and nothing will say
     * so"</em> — silent in the one class that could have said it.
     *
     * <p>Answers null for a frame of any other type, so the field is absent on every event that is
     * not a plan choice rather than being an empty {@code PlanChoice} nothing can tell from one.
     */
    private SiblingDomainEvent.PlanChoice planChoice(String type, JsonNode data) {
        if (!PATIENT_PLAN_CHOSEN.equals(type)) {
            return null;
        }
        return new SiblingDomainEvent.PlanChoice(
            text(data, PLAN_MEMBERSHIP_ID),
            text(data, PLAN_CODE),
            text(data, PLAN_NAME),
            text(data, PLAN_STATUS)
        );
    }

    /** What this event says the subject is, or null when it says nothing and must not overwrite. */
    private DirectorySubjectKind subjectKindFor(Disposition disposition, boolean careAngel) {
        if (disposition == Disposition.CREATE) {
            return DirectorySubjectKind.PATIENT;
        }
        return careAngel ? DirectorySubjectKind.CARE_ANGEL : null;
    }

    /**
     * Whether an {@code AccountCreated} is a care-angel nomination rather than a registration.
     *
     * <p>{@code data.authorities} is a comma-joined string on both call sites, so this is a substring
     * test bounded by the separator rather than an exact match on the whole field. A role named as a
     * prefix of another would be a false positive and there is none — but the split is done properly
     * anyway, because "no such role today" is not a property this file can keep true.
     */
    private boolean isCareAngelNomination(JsonNode data) {
        if (CARE_ANGEL_REASON.equals(text(data, "reason"))) {
            return true;
        }
        String authorities = text(data, "authorities");
        if (authorities == null) {
            return false;
        }
        for (String authority : authorities.split(",")) {
            if (CARE_ANGEL_AUTHORITY.equals(authority.trim())) {
                return true;
            }
        }
        return false;
    }

    /** Lowercased and trimmed, matching what both hc-patient publishers do to the key before sending. */
    private String normaliseKey(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** The envelope, or null for anything that is not one. Null rather than an Optional because
     * every caller immediately branches on it and an Optional would only be unwrapped. */
    private JsonNode read(byte[] payload, String topic) {
        if (payload == null || payload.length == 0) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (!node.isObject()) {
                LOG.warn("Ignoring a non-object frame on {}", topic);
                return null;
            }
            return node;
        } catch (Exception e) {
            // The exception's message, not the exception. A topic this service does not own can
            // carry frames it will never parse, and one stack trace per message would bury the log
            // in the shape of a stack trace that is not a failure.
            //
            // A FINGERPRINT, not an excerpt of the bytes — see the class javadoc for the argument.
            LOG.warn("Ignoring an unreadable frame on {} ({}): {}", topic, e.toString(), LogPseudonym.frame(payload));
            return null;
        }
    }

    /**
     * {@code occurredAt} as an instant, from a string or a numeric epoch — or null, which discards
     * the frame.
     *
     * <p>Null rather than {@code Instant.now()}, and the class javadoc gives the reason at length:
     * this value is the watermark, and stamping an unreadable frame with the present freezes its
     * subject against every event older than the moment it arrived. On a group reading from the
     * earliest offset that is the rest of that subject's history.
     */
    private Instant occurredAt(JsonNode node, String type) {
        JsonNode value = node.path("occurredAt");
        if (value.isNumber()) {
            // Seconds with a fractional part is what Jackson writes for an Instant with
            // WRITE_DATES_AS_TIMESTAMPS enabled and JavaTimeModule registered.
            double seconds = value.asDouble();
            return Instant.ofEpochMilli(Math.round(seconds * 1000));
        }
        String text = value.asText(null);
        if (text != null && !text.isBlank()) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException e) {
                LOG.warn("Ignoring a {} frame: occurredAt '{}' is not a timestamp this service can read", type, text);
                return null;
            }
        }
        LOG.warn("Ignoring a {} frame with no occurredAt — there is no watermark to apply it against", type);
        return null;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * A boolean field, or <b>null when the payload does not carry it</b>.
     *
     * <p>Null rather than false is the whole point, and it is why this is not
     * {@code node.path(field).asBoolean(false)}. An absent {@code activated} means no producer has
     * said whether the account can sign in; an {@code activated: false} means one has said it cannot.
     * Those need different answers on screen — "not reported" against "deactivated" — and defaulting
     * would make the first indistinguishable from the second on every frame published before
     * hc-professional's own change ships. The same holds for {@code isComplete} and
     * {@code isVerified}: a profile status nobody has sent is not an incomplete profile, and backlog
     * item 47 refuses that reading in the same terms items 27(a) and 46 refuse a fabricated name.
     *
     * <p>A field present but not boolean — a string {@code "true"}, which is what a hand-built
     * {@code LinkedHashMap} payload can produce — is read as a boolean rather than refused:
     * {@code JsonNode.asBoolean} does that conversion, and this is somebody else's schema.
     */
    private Boolean bool(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asBoolean();
    }

    /**
     * An optional timestamp inside a payload, in either of the two forms both products emit, or null.
     *
     * <p>Distinct from {@link #occurredAt} deliberately: that one is the <b>watermark</b>, whose
     * absence discards the frame, and it warns as it does so. These three are content — the profile's
     * own created and modified dates — and their absence is a field the console leaves blank, not a
     * reason to drop the event. Sharing one method would mean either warning about a blank cell or
     * silently discarding a frame over one.
     */
    private Instant timestamp(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return Instant.ofEpochMilli(Math.round(value.asDouble() * 1000));
        }
        String text = value.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            LOG.debug("Ignoring the {} on a profile status: '{}' is not a timestamp this service can read", field, text);
            return null;
        }
    }

    /**
     * Trimmed, and null for a value that was only whitespace. <b>Never lower-cased.</b>
     *
     * <p>{@link #normaliseKey} lower-cases, because a patient's key is an email address and both
     * hc-patient publishers lower-case it before sending — matching them is what makes one person one
     * link. An {@code accountId} is a UUID minted by a gateway, and the two phases of the professional
     * contract have to key on it <em>identically</em>; folding case here would make this side tolerant
     * of a producer that had started sending it differently, which is precisely the divergence that
     * must be visible rather than absorbed. Trimming is applied to both phases and to nothing else.
     */
    private String trimmed(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
