package net.jojoaddison.domain.enumeration;

/**
 * What happened when this service asked a sibling stack to name somebody — backlog item 50.
 *
 * <h2>It is never stored, and it is in this package anyway</h2>
 *
 * <p>Every other enum here is a persisted field. This one rides on {@code DirectoryLink}'s transient
 * {@code nameResolution} and is written to no document, because a resolution is a fact about one
 * lookup at one moment and not about the person: hc-patient may answer today and be down tomorrow,
 * and a stored outcome would be a second copy of an answer that has already changed. It lives here
 * because the layered architecture ({@code TechnicalStructureTest}) forbids {@code ..domain..} from
 * reaching {@code ..service..}, and the field that carries it is on a domain document.
 *
 * <h2>Three values, because three is what this service genuinely knows</h2>
 *
 * <p>The temptation is a fourth — "a name exists but you may not see it" — and it cannot be
 * supplied honestly. hc-patient's {@code ProfileResource.getProfileByEmail} returns the <b>same
 * 404</b> for a refusal as for an absence: the scope guard answers {@code notFound()} and
 * {@code wrapOrNotFound} answers {@code notFound()}, and nothing on the wire tells them apart.
 * Inferring it from the caller's authorities would copy their {@code PatientScope.isUnrestricted()}
 * into a fourth repository to drift on its own, which this estate already refuses for
 * {@code /professionals/me/**}.
 *
 * <p>The values that are here are separated for the opposite reason — collapsing
 * {@link #UNAVAILABLE} into {@link #NOT_FOUND} would say "hc-patient does not know this person"
 * when the truth is "nobody asked them", which is backlog item 46's silent-{@code LINK_ONLY}
 * lesson one stack along.
 */
public enum NameResolution {
    /**
     * hc-patient answered about this address.
     *
     * <p><b>It does not promise a name.</b> Their {@code Profile} carries no {@code @NotNull} on
     * {@code firstName} or {@code lastName}, so a profile with neither is storable and this outcome
     * can arrive with a null name — the console then falls back to the address, which is the right
     * rendering and needs no fourth outcome to reach. What this value asserts is that the question
     * was asked and answered.
     */
    RESOLVED,

    /**
     * hc-patient will not name this address: either they hold no profile for it, or the caller may
     * not see the one they hold.
     *
     * <p>The two are one value because their endpoint gives them one status code. Do not add a
     * screen sentence that picks between them.
     */
    NOT_FOUND,

    /**
     * The question was not answered — the far service could not be reached, refused, answered with
     * something unreadable, or was never dialled at all (this deployment is not configured for the
     * lookup, the request carries no token to relay, or the per-request budget was spent).
     *
     * <p><b>"Never dialled" is deliberately not a fourth value.</b> It is a distinction the api's
     * log makes and the screen cannot act on: an administrator reading a directory row can do
     * nothing different about a missing environment variable than about a stack that is down. See
     * {@code PatientServiceClient}, which logs which it was.
     *
     * <p><b>Which is why the sentence on the row names nobody</b> — "a name could not be looked up
     * for this account", not "the patient app could not be reached". Three of the four causes are on
     * <em>this</em> side: a base url pointing at a container that does not exist here, a deployment
     * configured off, a request with no token to relay. A row blaming the sibling stack for this
     * service's own configuration is item 24's wrong-machine pointer in miniature — a screen sending
     * an operator to go and look at a system that is working — and it is the state a stack would be
     * in most often, because the default base url is the production container's name.
     */
    UNAVAILABLE,
}
