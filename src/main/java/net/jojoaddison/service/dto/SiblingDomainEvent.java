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
    boolean erased
) {
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
