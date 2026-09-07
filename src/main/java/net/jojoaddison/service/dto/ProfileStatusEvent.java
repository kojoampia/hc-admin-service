package net.jojoaddison.service.dto;

import java.time.Instant;

/**
 * Phase 2 of the two-phase professional contract: basic profile metadata, and nothing else.
 *
 * <h2>The contract, and why it is a second type rather than more fields on the first</h2>
 *
 * <p>Backlog item 47, the architect's decision of 2026-09-07. A clinician is accepted here in two
 * phases because that is the order the facts exist in on the far side: an account is created before
 * there is a discipline, a licence or a profile to describe. Phase 1 is
 * {@code AccountStatus{accountId, username, email, isActivated}} from hc-professional's gateway;
 * this is phase 2, from hc-professional's api, and it is
 *
 * <pre>
 *   ProfileStatus { profileId, accountId, isComplete, isVerified,
 *                   createdDate, modifiedDate, lastModifiedBy }
 * </pre>
 *
 * <p><b>Identifiers, two booleans and three timestamps.</b> No role, no licence number, no name, no
 * address. That is what closes the question items 33, 35 and 36 were circling: the estate rule is
 * that a payload carries identifiers only, and a licence number is a real professional credential on
 * a topic four stacks can read. There is no credential in this payload, so there is nothing to keep
 * off the broker and nothing this service could fabricate a {@code Professional} from — which it must
 * not, and does not.
 *
 * <p>It is a separate record from {@link SiblingDomainEvent} rather than eight nullable fields on it
 * because the two are not the same kind of statement. A {@code SiblingDomainEvent} carries a
 * {@link SiblingDomainEvent.Disposition} — what it is permitted to do to a local record — and phase 2
 * has no such choice to make: it may never open a {@code Professional}, and the one thing it can do
 * to a link is write the profile half of it. Folding them together would put a disposition on a type
 * that has none and invite a {@code default} branch on somebody else's vocabulary, which is the
 * mistake {@code Disposition}'s own javadoc records.
 *
 * <h2>{@code accountId} is the join, and it is compared exactly</h2>
 *
 * <p>The two phases meet on {@code accountId}, which is this service's
 * {@code DirectoryLink.external_key} for {@link net.jojoaddison.domain.enumeration.DirectorySource#HC_PROFESSIONAL}.
 * It is trimmed and otherwise untouched: lower-casing it, as the patient key is lower-cased, would be
 * a normalisation that could <em>hide</em> a producer sending a differently-cased identifier, and the
 * one failure this contract has that looks correct on both sides is exactly that — two phases keyed
 * differently, each writing rows nobody can pair, and a console that is permanently half empty.
 * {@code DirectoryProjectionService} warns when no account has ever had both.
 *
 * @param eventId unique per emission, recorded for tracing and never used to deduplicate.
 * @param type the producer's own event type — {@code entity.created} or {@code entity.updated}.
 * @param occurredAt when the producer says it happened. The phase-2 watermark, and never inferred.
 * @param accountId the join key. An event without one cannot be applied to anything and is refused.
 * @param profileId hc-professional's id for the profile, when the payload names one.
 * @param complete phase 2's {@code isComplete}, or null when the payload does not say.
 * @param verified phase 2's {@code isVerified}, or null when the payload does not say.
 * @param createdDate when the profile was created on the far side, or null.
 * @param modifiedDate when it last changed there, or null.
 * @param lastModifiedBy an <b>accountId</b>, which is the gateway's {@code User.id} — the same
 *                       identifier space this service stamps as the {@code uid} claim and audits
 *                       against, so it needs no resolution and is given none. Never a display name.
 */
public record ProfileStatusEvent(
    String eventId,
    String type,
    Instant occurredAt,
    String accountId,
    String profileId,
    Boolean complete,
    Boolean verified,
    Instant createdDate,
    Instant modifiedDate,
    String lastModifiedBy
) {}
