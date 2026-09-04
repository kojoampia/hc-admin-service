package net.jojoaddison.service.dto;

import java.time.Instant;
import net.jojoaddison.domain.enumeration.DirectorySource;

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
 * @param occurredAt when the producer says it happened. The watermark.
 * @param subjectKey the correlation key: lowercased email for a patient, {@code accountId} for a
 *                   professional. An event with no subject key cannot be applied to anything.
 * @param email the subject's email address, when the event carries one.
 * @param login the subject's gateway login, when the event carries one.
 * @param externalId the sibling's own id for the subject, when the event carries one.
 * @param state the lifecycle state to record, already resolved from whichever field held it.
 * @param activated true when this event is evidence the account can sign in. Only the two account
 *                  events ever set it, and it is the only thing that may move a local status.
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
    boolean activated
) {}
