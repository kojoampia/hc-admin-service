package net.jojoaddison.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause.ProblemDetailWithCauseBuilder;

/**
 * More than one record answers to the account the caller asked about, so the question has no answer.
 *
 * <h2>Why this is not a 400 and not a 500</h2>
 *
 * <p>The request is well formed and the service is working: what is wrong is the data, and the
 * honest status for that is {@code 409 Conflict}. A 400 would tell a correct caller to fix its
 * request, and a 500 with a stack trace would tell an operator that this service is broken — both
 * send whoever is debugging to the wrong place, and the place they need to be sent is a duplicate
 * row in this database.
 *
 * <p><b>Loud rather than plausible.</b> The alternative — returning the first match, or both — is
 * what makes this class necessary: on a login that decides <em>which vendor the caller is</em>, an
 * arbitrary pick is one supplier reading another's record, with a 200 on it. hc-vendor named this
 * exact outcome, {@code AMBIGUOUS}, as the reason it resolves callers against its own database
 * rather than through this one.
 *
 * <p>It is the enforcement half of a policy whose prevention half is an index:
 * {@link net.jojoaddison.config.VendorAccountIndexes} makes the duplicate unstorable, and — like
 * every index creator in {@code config/} — reports and continues when it cannot create the index, so
 * the service can be running without it. Each is the other's backstop, and both say so.
 *
 * @see net.jojoaddison.web.rest.VendorResource#getAllVendors
 */
@SuppressWarnings("java:S110") // Inheritance tree of classes should not be too deep
public class AmbiguousAccountException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    /**
     * @param defaultMessage what an operator has to know, with no identifier in it — the message
     *                       reaches a client and this one is about somebody's account key.
     * @param entityName the JHipster entity name, so the alert header names the right screen.
     * @param errorKey the client-side message key, prefixed {@code error.} like every other.
     */
    public AmbiguousAccountException(String defaultMessage, String entityName, String errorKey) {
        super(
            HttpStatus.CONFLICT,
            ProblemDetailWithCauseBuilder.instance()
                .withStatus(HttpStatus.CONFLICT.value())
                .withType(ErrorConstants.DEFAULT_TYPE)
                .withTitle(defaultMessage)
                .withProperty("message", "error." + errorKey)
                .withProperty("params", entityName)
                .build(),
            null
        );
    }
}
