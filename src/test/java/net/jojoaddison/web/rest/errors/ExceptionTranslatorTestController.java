package net.jojoaddison.web.rest.errors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;

@RestController
@RequestMapping("/api/exception-translator-test")
public class ExceptionTranslatorTestController {

    /**
     * Injected exactly as every generated resource injects it, so {@link #successAlert} names its
     * headers the way a real 201 does rather than from a constant this fixture chose.
     */
    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    @GetMapping("/concurrency-failure")
    public void concurrencyFailure() {
        throw new ConcurrencyFailureException("test concurrency failure");
    }

    @PostMapping("/method-argument")
    public void methodArgument(@Valid @RequestBody TestDTO testDTO) {}

    @GetMapping("/missing-servlet-request-part")
    public void missingServletRequestPartException(@RequestPart("part") String part) {}

    @GetMapping("/missing-servlet-request-parameter")
    public void missingServletRequestParameterException(@RequestParam("param") String param) {}

    @GetMapping("/access-denied")
    public void accessdenied() {
        throw new AccessDeniedException("test access denied!");
    }

    @GetMapping("/unauthorized")
    public void unauthorized() {
        throw new BadCredentialsException("test authentication failed!");
    }

    @GetMapping("/response-status")
    public void exceptionWithResponseStatus() {
        throw new TestResponseStatusException();
    }

    @GetMapping("/internal-server-error")
    public void internalServerError() {
        throw new RuntimeException();
    }

    /**
     * A default-path (non-400) error that carries {@code params}. The only one in the api today, and
     * the shape every future 409 or 500 with a placeholder will have.
     */
    @GetMapping("/ambiguous-account")
    public void ambiguousAccount() {
        throw new AmbiguousAccountException("test ambiguous account", "directoryVendor", "accountidambiguous");
    }

    /**
     * The 400 branch, which builds its own params from the failure-alert headers and must stay
     * exactly as it was.
     */
    @GetMapping("/bad-request-alert")
    public void badRequestAlert() {
        throw new BadRequestAlertException("test bad request alert", "directoryVendor", "idexists");
    }

    /**
     * A <b>successful</b> write's alert headers, which is the half of backlog item 95 that has no
     * other reachable route in this suite.
     *
     * <p>Item 95 is not only the error path: {@code notificationInterceptor} reads
     * {@code MESSAGE_ALERT_HEADER_NAME} through the same three constants, so while the names
     * disagreed <b>no create, update or delete confirmation this api has ever sent was displayed</b>.
     * The failure headers are asserted on a real refusal; the success header had nothing asserting
     * it anywhere, because every {@code *ResourceIT} is generated and a hand-added assertion in one
     * would be erased by the next regeneration.
     *
     * <p>So this reproduces exactly what a generated resource does on its 201 — {@code HubResource}
     * line for line, {@code HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME,
     * id)} with {@code applicationName} injected from {@code jhipster.clientApp.name} — rather than
     * asserting anything about {@code HeaderUtil} directly. It sits on this controller because this
     * is the IT's own fixture controller and item 95's two halves belong in one place; the class
     * name is narrower than what it now serves, and that is the trade.
     *
     * <p>{@code platformHub} is deliberately a real entity name rather than an invented one: the
     * alert header's <em>value</em> is a translation key, {@code hcAdminApp.platformHub.created},
     * and that key exists in {@code app/src/main/webapp/i18n/en/platformHub.json}. A made-up entity
     * would pin the header and prove nothing about the message resolving.
     */
    @GetMapping("/success-alert")
    public ResponseEntity<Void> successAlert() {
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, "platformHub", "1"))
            .build();
    }

    public static class TestDTO {

        @NotNull
        private String test;

        public String getTest() {
            return test;
        }

        public void setTest(String test) {
            this.test = test;
        }
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "test response status")
    @SuppressWarnings("serial")
    public static class TestResponseStatusException extends RuntimeException {}
}
