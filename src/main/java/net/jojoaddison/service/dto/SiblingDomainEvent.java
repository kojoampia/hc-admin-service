package net.jojoaddison.service.dto;

import java.time.Instant;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;

/**
 * One domain event from a sibling stack, reduced to the facts this directory can act on.
 *
 * <h2>Why the two envelopes are flattened into one type here</h2>
 *
 * <p>hc-patient and hc-professional do not share an envelope and there is no reason they should —
 * neither owns the other's schema. hc-patient sends {@code {eventId, type, version, occurredAt,
 * source, subject:{email, login, patientId}, data}}; hc-professional sends {@code {eventId,
 * eventType, occurredAt, source, actor, payload:{…}}}, with the subject inside the payload and the
 * lifecycle state in a {@code state} field rather than in the type. Reading both shapes in the
 * write path would put two vocabularies into the merge rule, which is the one piece of this that has
 * to be obviously correct. The envelope difference is absorbed once, in
 * {@code SiblingEventParser}, and everything downstream sees this.
 *
 * <p><b>What is deliberately not here: anything a person could be identified by beyond an address.</b>
 * No name, no date of birth, no phone number, no document number. That is not an omission to fill in
 * later — hc-patient's {@code PatientEventPublisher} refuses at runtime to publish a payload
 * carrying clinical or identifying content, and hc-professional's envelopes say "identifiers only"
 * in their own javadoc. So this service can learn that somebody exists and cannot learn who they
 * are, and the consequence has to be accepted rather than worked around: a directory row created
 * from an event has no name on it.
 *
 * @param source which stream it came from. Not the {@code source} field of the envelope, which names
 *               the emitting application; see {@link DirectorySource}.
 * @param eventId unique per emission. Recorded on the link for tracing, not used for deduplication —
 *                see {@code DirectoryProjectionService} for why the subject key does that job.
 * @param type the event type, in the producer's own vocabulary.
 * @param occurredAt when the producer says it happened. The watermark, and never inferred — an
 *                   envelope this could not read a timestamp out of does not become an event at all.
 * @param subjectKey the correlation key: lowercased email for a patient, {@code accountId} for a
 *                   professional. An event with no subject key cannot be applied to anything.
 * @param email the subject's email address, when the event carries one.
 * @param login the subject's gateway login, when the event carries one.
 * @param externalId the sibling's own id for the subject, when the event carries one.
 * @param state the lifecycle state to record, already resolved from whichever field held it.
 * @param activated whether the account can sign in, <b>as this event states it</b>, or null when the
 *                  event says nothing about it. Three states rather than two, and the third is the
 *                  point: backlog item 47 makes {@code activated} part of phase 1's
 *                  {@code AccountStatus} and forbids deriving it, so "no event has said" has to be
 *                  representable or the absence would be written as {@code false} and read as a
 *                  deactivated account. On hc-patient's stream only the two account events set it,
 *                  and only true ever moves a local status.
 * @param disposition what this event is allowed to do to this service's records. See
 *                    {@link Disposition} — every type either creates, updates, or is a link and
 *                    nothing more, and there is no default.
 * @param subjectKind what kind of account this event says the subject is, or null when it says
 *                    nothing. Only an event that may open a record declares one.
 * @param accountCreatedDate when the account was created on the far side, or null. Phase 1 of the
 *                           professional contract carries it; hc-patient's stream carries no such
 *                           field and always answers null. <b>Not {@code firstSeenAt}</b>, which is
 *                           when this service first heard about the subject and is a fact about this
 *                           service's consumption rather than about the account.
 * @param accountModifiedDate when the account last changed on the far side, or null. Not
 *                            {@code lastEventAt}, for the same reason.
 * @param erased true for the one event on either stream whose subject <b>no longer exists on the far
 *               side</b>: hc-patient's {@code DeletionRequestChanged} with {@code change=COMPLETED},
 *               published after the profile has already been erased. It must never open a record —
 *               see {@code DirectoryProjectionService} for what it does instead.
 * @param planChoice the membership tier this patient chose, or null on every event that is not
 *                   hc-patient's {@code PlanChosen} — which is every event on either stream except
 *                   that one. See {@link PlanChoice}.
 */
public record SiblingDomainEvent(
    DirectorySource source,
    String eventId,
    String type,
    Instant occurredAt,
    String subjectKey,
    String email,
    String login,
    String externalId,
    String state,
    Boolean activated,
    Disposition disposition,
    DirectorySubjectKind subjectKind,
    Instant accountCreatedDate,
    Instant accountModifiedDate,
    boolean erased,
    PlanChoice planChoice
) {
    /**
     * What hc-patient's {@code PlanChosen} carries, and nothing else — backlog item 48.
     *
     * <h2>One component rather than four, and the reason is this record's size</h2>
     *
     * <p>{@link SiblingDomainEvent} is already fifteen components wide and every construction site
     * has to name all of them, so four flat additions would be four {@code null}s at each of the
     * three sites in {@code SiblingEventParser} and four more at every site added later. Grouping
     * them costs one {@code null} instead, and it also makes "this event is not a plan choice" a
     * single value a reader can test rather than four fields that have to agree with each other.
     *
     * <p>It is a nested record on this type rather than a second top-level one because it arrives on
     * the <b>same topic, from the same producer, keyed the same way</b> as every other patient event.
     * That is the difference from {@link ProfileStatusEvent}, which is its own type precisely because
     * it comes from a different application on a different topic with its own watermark.
     *
     * <h2>The contract, read from the producer and not from the backlog entry</h2>
     *
     * <p>hc-patient's {@code PatientEventType.PLAN_CHOSEN} — their {@code origin/main} {@code 5a90145},
     * 2026-09-08 — states it: the type string is {@code "PlanChosen"} and the payload is
     * {@code membershipId}, {@code planCode}, {@code planName}, {@code status}, keyed on the
     * lowercased email like every other event on that stream. Their {@code MembershipPlanEventTest}
     * pins all five strings as literals rather than as constants, in their own words because
     * <em>"hc-admin's SiblingEventParser dispatches on the literal and reads the payload keys by name;
     * neither is a compile error there if this repository renames one"</em>. {@code SiblingEventParserTest}
     * does the same on this side, which is the whole of the enforcement between the two repositories.
     *
     * <p><b>{@code planCode} is their {@code Membership.plan} and {@code planName} is their
     * {@code Membership.name}.</b> Their document has no {@code code} field at all — both of their
     * clients' {@code choosePlan} writes the tier's code into {@code plan} and its display name into
     * {@code name} — so the wire names are honest about the values and misleading about the fields
     * they came from. Nothing here has to care, but a reader diffing the two schemas will.
     *
     * <p><b>{@code description} is deliberately not on the wire</b>, being free text a client
     * supplies; and a membership created through either of their clients carries no
     * {@code memberNumber} and no {@code renewalDate}, which their item 17 records as a fact rather
     * than an omission. Their administrative CRUD path can carry both, so neither absence generalises.
     *
     * @param membershipId hc-patient's own id for the {@code Membership}. A support handle, not a join
     *                     key: the exchange with them is keyed on the patient's address, and item 54
     *                     settled its return payload at the plan alone for exactly that reason.
     * @param code the tier code — {@code PEAR}, {@code PAWPAW}, {@code MELON}. Since item 51 this
     *             service's own {@code ServicePlan.code} is the same vocabulary, so it resolves; a
     *             code that matches nothing is a tier Abofonsa has published and this catalogue has
     *             not synced, and is announced rather than defaulted.
     * @param name the tier's display name as hc-patient holds it, carried so a row can be read even
     *             when {@link #code} resolves to nothing here.
     * @param status <b>the status the membership was created with, at the moment it was created</b> —
     *               {@code PENDING} for anybody but an administrator on their side. A free string and
     *               not an enum, following {@code DirectoryLink.state}: the vocabulary is theirs, they
     *               ship five values today ({@code PENDING, ACTIVE, CANCELLED, EXPIRED, SUSPENDED}),
     *               and binding it here would turn "they added a value" into "this service refuses a
     *               message".
     *
     *               <p><b>It is not a live status and must never be rendered as one.</b> Their
     *               {@code MembershipResource} publishes on {@code POST} alone — {@code PUT} and
     *               {@code PATCH} write {@code status} and announce nothing — so a membership approved
     *               on their side after the fact says so on no topic, and this field goes on reading
     *               {@code PENDING} for ever. That is a gap in the contract rather than in this code,
     *               and it is what item 54's return leg exists to close from this end.
     */
    public record PlanChoice(String membershipId, String code, String name, String status) {}

    /**
     * What an event of this type is permitted to do to this service's records.
     *
     * <h2>Why a type this service does not recognise is not a value here</h2>
     *
     * <p>It is {@code Optional.empty()} from the parser instead, which is the contract both producers
     * publish in their own javadoc: <em>a consumer meeting something it does not recognise must
     * ignore it.</em> Naming a fourth value would invite a {@code default} branch, and a
     * {@code default} branch on somebody else's vocabulary is how {@code DeletionRequestChanged} —
     * the event whose entire meaning is "erase this person" — came to be the thing that made this
     * service start storing them.
     */
    public enum Disposition {
        /**
         * May open a record for a subject this service has never seen: a link, and for a patient a
         * local {@code Patient} row.
         */
        CREATE,

        /**
         * Updates the link and any local record that already exists, and <b>never opens one</b>. An
         * event of this kind arriving for an unknown subject is not applied at all — it is a fact
         * about somebody whose arrival this service missed, and inventing them from it produces a
         * row nothing can complete.
         */
        UPDATE_ONLY,

        /**
         * Records the subject as a link and deliberately keeps no local record for it — a care
         * angel, or a clinician. The link is what stops a later event mistaking them for a patient,
         * which is why this is not simply "ignore".
         */
        LINK_ONLY,
    }
}
