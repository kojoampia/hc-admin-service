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
 * {@code AccountStatus{accountId, login, email, activated, createdDate, modifiedDate}} from
 * hc-professional's gateway — <b>{@code login} and {@code activated}, never {@code username} and
 * {@code isActivated}</b>, which is an earlier draft's spelling that item 47 revised away precisely so
 * that two names for one field could not coexist across two repositories; the argument is at
 * {@code SiblingEventParser.parseProfessionalEvent}. This is phase 2, from hc-professional's api, and
 * it is
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
 * @param type the producer's own event type. Always {@code ProfileStatus} — the architect's decision 3
 *             of 2026-09-08 accepts nothing else on that topic, so this field is recorded rather than
 *             branched on.
 * @param occurredAt when the producer says it happened. The phase-2 watermark, and never inferred.
 * @param accountId the join key. An event without one cannot be applied to anything and is refused.
 * @param profileId hc-professional's id for the profile, when the payload names one.
 * @param complete phase 2's {@code isComplete}, or null when the payload does not say.
 * @param verified phase 2's {@code isVerified}, or null when the payload does not say.
 * @param createdDate when the profile was created on the far side, or null.
 * @param modifiedDate when it last changed there, or null.
 * @param lastModifiedBy <b>hc-professional's login for whoever last wrote the profile</b>, or their
 *                       {@code system} when nobody was authenticated — <em>not</em> an
 *                       {@code accountId}, and not in {@code accountId}'s identifier space.
 *                       <b>Item 47's contract says it is "an accountId, which IS the gateway's
 *                       User.id", and that is wrong about the code</b>: the value is Spring Data
 *                       auditing's {@code lastModifiedBy} on their {@code Profile}, filled by their
 *                       {@code SpringSecurityAuditorAware}, which returns
 *                       {@code SecurityUtils.getCurrentUserLogin()} — the JWT subject. The
 *                       architect's decision 2 moves their <em>{@code accountId}</em> to a
 *                       {@code User.id} and touches nothing about auditing, so the two are in
 *                       different spaces and will stay there. Read on their {@code origin/main}
 *                       2026-09-08: {@code OnboardingService.publishProfileStatus} passes
 *                       {@code profile.getLastModifiedBy()}.
 *                       <p>Nothing follows for the code — it is still stored and shown verbatim, and
 *                       is still never resolved into a name — but two things follow for a reader.
 *                       It must not be joined to a {@code DirectoryLink.external_key}, an
 *                       {@code AuditLog.userId} or an hc-admin login, because it names an account on
 *                       another stack; and it is the one field on this row that is already legible,
 *                       which is why the console shows it without apology.
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
