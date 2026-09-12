package net.jojoaddison.web.rest.errors;

import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause.ProblemDetailWithCauseBuilder;
import tech.jhipster.web.util.HeaderUtil;

/**
 * Controller advice to translate the server side exceptions to client-friendly json structures.
 * The error response follows RFC7807 - Problem Details for HTTP APIs (https://tools.ietf.org/html/rfc7807).
 */
@ControllerAdvice
public class ExceptionTranslator extends ResponseEntityExceptionHandler {

    private static final String FIELD_ERRORS_KEY = "fieldErrors";
    private static final String MESSAGE_KEY = "message";
    private static final String PATH_KEY = "path";
    private static final String PARAMS_KEY = "params";
    private static final String ENTITY_NAME_PARAM = "entityName";
    private static final boolean CASUAL_CHAIN_ENABLED = false;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Environment env;

    public ExceptionTranslator(Environment env) {
        this.env = env;
    }

    @ExceptionHandler
    public ResponseEntity<Object> handleAnyException(Throwable ex, NativeWebRequest request) {
        ProblemDetailWithCause pdCause = wrapAndCustomizeProblem(ex, request);
        return handleExceptionInternal((Exception) ex, pdCause, buildHeaders(ex), HttpStatusCode.valueOf(pdCause.getStatus()), request);
    }

    /**
     * Puts the failure-alert headers back on a {@link BadRequestAlertException}.
     *
     * <h2>Why an override rather than another {@code @ExceptionHandler}</h2>
     *
     * <p>{@link #handleAnyException} calls {@link #buildHeaders} and <b>never runs for this family</b>.
     * {@code BadRequestAlertException} extends {@link ErrorResponseException}, and
     * {@link ResponseEntityExceptionHandler#handleException} declares a handler for that which is
     * <em>more specific</em> than {@code handleAnyException}'s bare {@code Throwable}, so Spring
     * dispatches there and answers with the exception's own — empty — headers. {@code buildHeaders}
     * built a perfectly good {@code HttpHeaders} that nothing ever received. That is backlog item 91,
     * and it was live on every 400 this api has ever refused a write with.
     *
     * <p>The obvious repair is a second {@code @ExceptionHandler(BadRequestAlertException.class)} on
     * this advice, and it would work — a subclass outranks its parent in
     * {@code ExceptionHandlerMethodResolver}. It is <b>not</b> what this does, because <em>competing
     * for dispatch is what broke this in the first place</em>: the advice claimed
     * {@code Throwable} and quietly lost. {@link ResponseEntityExceptionHandler#handleException} is
     * {@code final}, so overriding this protected seam is the framework's own answer, there is one
     * dispatch path rather than two, and any future {@code ErrorResponseException} that
     * {@code buildHeaders} learns about is covered without a third handler.
     *
     * <h2>Two things this deliberately does not do</h2>
     *
     * <p><b>It does not touch the body.</b> {@code params} stays the bare entity name on a 400, which
     * is item 89's contract and is pinned by
     * {@code ExceptionTranslatorIT.testBadRequestBodyParamsStaysBare}. Once the headers arrive the
     * console takes its {@code errorKey} branch and builds {@code { entityName }} itself from the
     * {@code -params} header, translating it through {@code global.menu.entities.<param>} — which the
     * server cannot do — so the body's {@code params} goes back to being unread there. That is the
     * intended outcome, not a regression.
     *
     * <p><b>It does not merge into a header the exception set itself.</b> The alert headers win on a
     * key collision, because {@code buildHeaders} is the only thing in this repository that writes
     * them and an {@code ErrorResponseException} carrying its own {@code X-<app>-error} would be
     * asserting something this advice has no way to reconcile.
     *
     * <h2>⚠ Restoring these headers is necessary and is not sufficient</h2>
     *
     * <p>{@code HeaderUtil} names them from {@code jhipster.clientApp.name}, which is
     * {@code hcAdminServiceApp} here — derived from this repo's {@code baseName}, {@code hcAdminService}.
     * The console reads {@code x-hcadminapp-error} / {@code x-hcadminapp-params}
     * ({@code app/src/main/webapp/app/shared/jhipster/constants.ts}), derived from <em>its</em>
     * {@code baseName}, {@code hcAdmin}. <b>The two have never agreed</b>, no test or configuration on
     * either side pins them, and the same mismatch silences every success alert this api sends. Until
     * that is settled, {@code error.idexists}'s {@code {{ entityName }}} still renames nothing on
     * screen. It is reported rather than fixed here: it is one name in one of two repositories and the
     * choice of which is not this change's to make.
     *
     * <h2>One visible side effect</h2>
     *
     * <p>{@code HeaderUtil.createFailureAlert} opens with {@code log.error("Entity processing failed,
     * {}", defaultMessage)}, so every refused write now logs at ERROR where it logged nothing before.
     * All eighteen {@code BadRequestAlertException} messages in this api were read before accepting
     * that: every one is a constant, or a constant concatenated with a filter <em>parameter name</em>
     * ({@code DirectoryLinkResource.rejectBlank}) or a bound ({@code MAX_LOCAL_IDS}). None interpolates
     * a correlation key, an address or an id, so item 43's rule is not breached — but note that this
     * log statement lives in the jhipster-framework jar, where {@code LogPseudonymTest}'s sweep cannot
     * see it. A future message that interpolated a subject would be leaked by a line no sweep reads.
     */
    @Override
    protected ResponseEntity<Object> handleErrorResponseException(
        ErrorResponseException ex,
        HttpHeaders headers,
        HttpStatusCode statusCode,
        WebRequest request
    ) {
        HttpHeaders alertHeaders = buildHeaders(ex);
        if (alertHeaders == null) return super.handleErrorResponseException(ex, headers, statusCode, request);

        HttpHeaders merged = new HttpHeaders();
        merged.putAll(headers);
        merged.putAll(alertHeaders);
        return super.handleErrorResponseException(ex, merged, statusCode, request);
    }

    @Nullable
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception ex,
        @Nullable Object body,
        HttpHeaders headers,
        HttpStatusCode statusCode,
        WebRequest request
    ) {
        body = body == null ? wrapAndCustomizeProblem((Throwable) ex, (NativeWebRequest) request) : body;
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    protected ProblemDetailWithCause wrapAndCustomizeProblem(Throwable ex, NativeWebRequest request) {
        return customizeProblem(getProblemDetailWithCause(ex), ex, request);
    }

    private ProblemDetailWithCause getProblemDetailWithCause(Throwable ex) {
        if (
            ex instanceof ErrorResponseException exp && exp.getBody() instanceof ProblemDetailWithCause problemDetailWithCause
        ) return problemDetailWithCause;
        return ProblemDetailWithCauseBuilder.instance().withStatus(toStatus(ex).value()).build();
    }

    protected ProblemDetailWithCause customizeProblem(ProblemDetailWithCause problem, Throwable err, NativeWebRequest request) {
        if (problem.getStatus() <= 0) problem.setStatus(toStatus(err));

        if (problem.getType() == null || problem.getType().equals(URI.create("about:blank"))) problem.setType(getMappedType(err));

        // higher precedence to Custom/ResponseStatus types
        String title = extractTitle(err, problem.getStatus());
        String problemTitle = problem.getTitle();
        if (problemTitle == null || !problemTitle.equals(title)) {
            problem.setTitle(title);
        }

        if (problem.getDetail() == null) {
            // higher precedence to cause
            problem.setDetail(getCustomizedErrorDetails(err));
        }

        Map<String, Object> problemProperties = problem.getProperties();
        if (problemProperties == null || !problemProperties.containsKey(MESSAGE_KEY)) problem.setProperty(
            MESSAGE_KEY,
            getMappedMessageKey(err) != null ? getMappedMessageKey(err) : "error.http." + problem.getStatus()
        );

        if (problemProperties == null || !problemProperties.containsKey(PATH_KEY)) problem.setProperty(PATH_KEY, getPathValue(request));

        if (
            err instanceof MethodArgumentNotValidException fieldException &&
            (problemProperties == null || !problemProperties.containsKey(FIELD_ERRORS_KEY))
        ) problem.setProperty(FIELD_ERRORS_KEY, getFieldErrors(fieldException));

        buildInterpolationParams(problem);

        problem.setCause(buildCause(err.getCause(), request).orElse(null));

        return problem;
    }

    /**
     * Makes {@code params} an interpolation map on every status the console does not read the alert
     * headers for, so a {@code {{ … }}} token in the message bundle resolves there too.
     *
     * <h2>The two branches that disagreed</h2>
     *
     * <p>{@code app/shared/alert/alert-error.ts} splits on the status. {@code handleBadRequest} —
     * <b>400 only</b> — ignores the body and builds its own object from the failure-alert headers,
     * {@code { entityName }}. {@code handleDefaultError} — everything else, so 401, 403, 405, 409 and
     * 500 — hands {@code error.params} <em>verbatim</em> to ngx-translate as the interpolation
     * argument.
     *
     * <p>A bare string is not a parameter map. {@link AmbiguousAccountException} set
     * {@code .withProperty("params", entityName)}, so {@code params} reached the console as
     * {@code "directoryVendor"} and any token in {@code error.accountidambiguous} would have rendered
     * literally. <b>Nothing reports that</b>: the bundle looks identical either way, so the next
     * person writing a 409 or 500 message has no way to learn the rule. That is what made it a trap
     * rather than a bug, and it is why the fix is here rather than on the one exception that hit it.
     *
     * <h2>Why {@code entityName}, and why 400 is left alone</h2>
     *
     * <p>The name is <b>derived from the 400 branch, not invented</b> — {@code entityName} is the
     * only interpolation name the console has ever built and therefore the only one the bundle
     * already spells (see {@code global.json}'s {@code error.idexists}). Two dialects would be worse
     * than the wart.
     *
     * <p>400 is deliberately untouched, and the body shape it has today is pinned by
     * {@code ExceptionTranslatorIT.testBadRequestBodyParamsStaysBare} rather than left to trust. The
     * obvious way to write this method normalises every status; that is the regression, because the
     * 400 branch is the one the console already has its own answer for.
     *
     * <p><b>⚠ "400 is left alone" has meant two different things, and only one of them is still
     * true.</b> When this method was written, a 400 reached the console with <em>no</em>
     * {@code X-<app>-error} and no {@code X-<app>-params} at all — {@link #buildHeaders} was dead
     * code — so the console's header branch never fired and its fallback read the body's bare-string
     * {@code params} exactly as the default path used to. That was backlog item 91, a separate defect
     * with a separate cause, and it is fixed: see {@link #handleErrorResponseException}. What is
     * unchanged is this method's guard. The 400 <b>body</b> keeps its bare {@code params}, because the
     * console now genuinely does build its own object from the headers — and it translates the entity
     * name through {@code global.menu.entities.<param>}, which the server cannot do.
     *
     * <p>A {@code params} that is <b>already a Map is left exactly as it is</b>: an exception that
     * has gone to the trouble of naming its own placeholders knows better than this method does.
     */
    private void buildInterpolationParams(ProblemDetailWithCause problem) {
        if (problem.getStatus() == HttpStatus.BAD_REQUEST.value()) return;

        Map<String, Object> problemProperties = problem.getProperties();
        if (problemProperties == null) return;

        Object params = problemProperties.get(PARAMS_KEY);
        if (params == null || params instanceof Map) return;

        problem.setProperty(PARAMS_KEY, Map.of(ENTITY_NAME_PARAM, params));
    }

    private String extractTitle(Throwable err, int statusCode) {
        return getCustomizedTitle(err) != null ? getCustomizedTitle(err) : extractTitleForResponseStatus(err, statusCode);
    }

    private List<FieldErrorVM> getFieldErrors(MethodArgumentNotValidException ex) {
        return ex
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .map(f ->
                new FieldErrorVM(
                    f.getObjectName().replaceFirst("DTO$", ""),
                    f.getField(),
                    StringUtils.isNotBlank(f.getDefaultMessage()) ? f.getDefaultMessage() : f.getCode()
                )
            )
            .toList();
    }

    private String extractTitleForResponseStatus(Throwable err, int statusCode) {
        ResponseStatus specialStatus = extractResponseStatus(err);
        return specialStatus == null ? HttpStatus.valueOf(statusCode).getReasonPhrase() : specialStatus.reason();
    }

    private String extractURI(NativeWebRequest request) {
        HttpServletRequest nativeRequest = request.getNativeRequest(HttpServletRequest.class);
        return nativeRequest != null ? nativeRequest.getRequestURI() : StringUtils.EMPTY;
    }

    private HttpStatus toStatus(final Throwable throwable) {
        // Let the ErrorResponse take this responsibility
        if (throwable instanceof ErrorResponse err) return HttpStatus.valueOf(err.getBody().getStatus());

        return Optional.ofNullable(getMappedStatus(throwable)).orElse(
            Optional.ofNullable(resolveResponseStatus(throwable)).map(ResponseStatus::value).orElse(HttpStatus.INTERNAL_SERVER_ERROR)
        );
    }

    private ResponseStatus extractResponseStatus(final Throwable throwable) {
        return Optional.ofNullable(resolveResponseStatus(throwable)).orElse(null);
    }

    private ResponseStatus resolveResponseStatus(final Throwable type) {
        final ResponseStatus candidate = findMergedAnnotation(type.getClass(), ResponseStatus.class);
        return candidate == null && type.getCause() != null ? resolveResponseStatus(type.getCause()) : candidate;
    }

    private URI getMappedType(Throwable err) {
        if (err instanceof MethodArgumentNotValidException) return ErrorConstants.CONSTRAINT_VIOLATION_TYPE;
        return ErrorConstants.DEFAULT_TYPE;
    }

    private String getMappedMessageKey(Throwable err) {
        if (err instanceof MethodArgumentNotValidException) {
            return ErrorConstants.ERR_VALIDATION;
        } else if (err instanceof ConcurrencyFailureException || err.getCause() instanceof ConcurrencyFailureException) {
            return ErrorConstants.ERR_CONCURRENCY_FAILURE;
        }
        return null;
    }

    private String getCustomizedTitle(Throwable err) {
        if (err instanceof MethodArgumentNotValidException) return "Method argument not valid";
        return null;
    }

    private String getCustomizedErrorDetails(Throwable err) {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
            if (err instanceof HttpMessageConversionException) return "Unable to convert http message";
            if (err instanceof DataAccessException) return "Failure during data access";
            if (containsPackageName(err.getMessage())) return "Unexpected runtime exception";
        }
        return err.getCause() != null ? err.getCause().getMessage() : err.getMessage();
    }

    private HttpStatus getMappedStatus(Throwable err) {
        // Where we disagree with Spring defaults
        if (err instanceof AccessDeniedException) return HttpStatus.FORBIDDEN;
        if (err instanceof ConcurrencyFailureException) return HttpStatus.CONFLICT;
        if (err instanceof BadCredentialsException) return HttpStatus.UNAUTHORIZED;
        return null;
    }

    private URI getPathValue(NativeWebRequest request) {
        if (request == null) return URI.create("about:blank");
        return URI.create(extractURI(request));
    }

    private HttpHeaders buildHeaders(Throwable err) {
        return err instanceof BadRequestAlertException badRequestAlertException
            ? HeaderUtil.createFailureAlert(
                  applicationName,
                  true,
                  badRequestAlertException.getEntityName(),
                  badRequestAlertException.getErrorKey(),
                  badRequestAlertException.getMessage()
              )
            : null;
    }

    public Optional<ProblemDetailWithCause> buildCause(final Throwable throwable, NativeWebRequest request) {
        if (throwable != null && isCasualChainEnabled()) {
            return Optional.of(customizeProblem(getProblemDetailWithCause(throwable), throwable, request));
        }
        return Optional.ofNullable(null);
    }

    private boolean isCasualChainEnabled() {
        // Customize as per the needs
        return CASUAL_CHAIN_ENABLED;
    }

    private boolean containsPackageName(String message) {
        // This list is for sure not complete
        return StringUtils.containsAny(message, "org.", "java.", "net.", "jakarta.", "javax.", "com.", "io.", "de.", "net.jojoaddison");
    }
}
