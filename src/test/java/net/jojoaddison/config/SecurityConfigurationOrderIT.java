package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.Filter;
import java.lang.reflect.Field;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.security.AuthoritiesConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcherEntry;
import org.springframework.util.ReflectionUtils;

/**
 * <b>Where the rules sit in the chain, not merely that they are written down.</b>
 *
 * <h2>Why position needs a test of its own</h2>
 *
 * <p>{@link SecurityConfiguration}'s matchers are evaluated in order and the <em>first</em> one that
 * matches decides the request. So a carve-out placed below a blanket rule that already matches its
 * path is never evaluated at all: the endpoint keeps the blanket rule's answer while the carve-out
 * goes on reading, in a file people trust, like a grant that has been made. Nothing fails, and a
 * test asserting that the rule exists passes with the same green as a test asserting it works.
 *
 * <p>This repository has been bitten by the shape twice — the patient rule and
 * {@code /api/professionals/me/**} both had to precede the blanket rules, and both say so at the
 * matcher — and hc-admin-gateway carries a source-reading guard for the same reason
 * ({@code SecurityConfigurationOrderTest}, backlog items 75 and 78).
 *
 * <p><b>This one reads the chain Spring built rather than the source that asked for it.</b> The
 * gateway's guard parses Java because its own ordering is otherwise unobservable; here the
 * application context is already being started by the suite, so the stronger measurement is
 * available for nothing: it grades the object graph the requests actually traverse, so it cannot be
 * fooled by a comment quoting a matcher, by a formatter wrapping one, or by a second
 * {@code authorizeHttpRequests} block somebody adds elsewhere.
 *
 * <p><b>It fails closed.</b> Every step that could stop the chain being readable — no
 * {@link AuthorizationFilter}, a manager that is not the delegating one, no {@code mappings} field —
 * is an assertion with a message naming what broke, because a reflective reader that quietly finds
 * nothing is a guard that passes by returning an empty list. Spring Security's internals are not
 * API; when this class goes red after an upgrade, fix the reading and keep the assertions.
 *
 * @see net.jojoaddison.web.rest.ApiAuthorizationIT for what each authority actually reaches
 */
@IntegrationTest
class SecurityConfigurationOrderIT {

    /** Backlog item 31 — a supplier reading its own row, and above the rule below it. */
    private static final String VENDORS = "/api/vendors";

    /** Backlog item 88 — the same supplier reading the same row by id. */
    private static final String VENDOR_RECORD = "/api/vendors/{id}";

    /** The console's directory counts, which stay the console's — and which {@code {id}} matches. */
    private static final String VENDOR_SUMMARY = "/api/vendors/summary";

    private static final String BLANKET_READ = "/api/**";

    @Autowired
    private SecurityFilterChain filterChain;

    /**
     * The authorization rules in the order the chain evaluates them, rendered as text.
     *
     * <p>Each entry is the matcher's {@code toString()} followed by the manager's — so one string
     * carries both the path and the authorities, which is what lets a single list answer "where is
     * it" and "what does it say".
     */
    private List<String> rulesInOrder() {
        AuthorizationFilter authorization = filterChain
            .getFilters()
            .stream()
            .filter(AuthorizationFilter.class::isInstance)
            .map(AuthorizationFilter.class::cast)
            .findFirst()
            .orElse(null);
        assertThat(authorization)
            .as("no AuthorizationFilter in the chain (%s) — the rules are not being applied at all", describe(filterChain.getFilters()))
            .isNotNull();

        Object manager = unwrap(authorization.getAuthorizationManager());
        Field mappings = ReflectionUtils.findField(manager.getClass(), "mappings");
        assertThat(mappings)
            .as(
                "no mappings field on %s — Spring Security's internals moved, so this guard can no longer " +
                    "read the order. Fix the reading; do not delete the assertions.",
                manager.getClass().getName()
            )
            .isNotNull();
        ReflectionUtils.makeAccessible(mappings);

        Object value = ReflectionUtils.getField(mappings, manager);
        assertThat(value).as("the mappings field is not a list of matcher entries").isInstanceOf(List.class);

        List<String> rules = ((List<?>) value)
            .stream()
            .filter(RequestMatcherEntry.class::isInstance)
            .map(RequestMatcherEntry.class::cast)
            .map(entry -> entry.getRequestMatcher() + " -> " + entry.getEntry())
            .toList();
        assertThat(rules).as("the chain holds no readable rules, so every assertion below would pass vacuously").isNotEmpty();
        return rules;
    }

    /**
     * Unwraps whatever the observation/deferred decorators have put around the delegating manager.
     *
     * <p>Micrometer's {@code ObservationAuthorizationManager} wraps it whenever an
     * {@code ObservationRegistry} is present, which it is here, and nothing in that decorator is
     * public. The loop follows a single {@code delegate}-shaped field rather than naming one
     * decorator, so a second layer does not need a second edit.
     */
    private static Object unwrap(AuthorizationManager<?> manager) {
        Object current = manager;
        for (int depth = 0; depth < 5; depth++) {
            if (ReflectionUtils.findField(current.getClass(), "mappings") != null) {
                return current;
            }
            Field delegate = ReflectionUtils.findField(current.getClass(), "delegate");
            if (delegate == null) {
                return current;
            }
            ReflectionUtils.makeAccessible(delegate);
            Object next = ReflectionUtils.getField(delegate, current);
            if (next == null) {
                return current;
            }
            current = next;
        }
        return current;
    }

    private static String describe(List<Filter> filters) {
        return filters
            .stream()
            .map(filter -> filter.getClass().getSimpleName())
            .toList()
            .toString();
    }

    /**
     * First rule matching this method and this path <em>exactly</em>, or -1.
     *
     * <p>The needle is built by asking Spring Security for the matcher the configuration would have
     * built, rather than by writing out how one renders. Two things come free from that. A substring
     * search would find {@code /api/vendors} inside a rule for {@code /api/vendors/**} and report a
     * carve-out one path wider than the one that exists — the difference between a supplier reading a
     * list and a supplier reading any record it can name. And the rendering is not API: it carried no
     * quotes around the pattern on 7.1.0, which is how the first version of this class reported every
     * rule missing while the chain was perfectly correct.
     */
    private static int indexOf(List<String> rules, HttpMethod method, String path) {
        String matcher = PathPatternRequestMatcher.withDefaults().matcher(method, path) + " -> ";
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).startsWith(matcher)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The premise: the reader can still see the rules it grades.
     *
     * <p>Pinned by a floor and by one rule that has been in this chain since the audit, so a reading
     * that silently degrades to "a list of things that are not matchers" cannot leave the ordering
     * cases passing over nothing.
     */
    @Test
    void theChainIsReadable() {
        List<String> rules = rulesInOrder();

        assertThat(rules).as("far fewer rules than SecurityConfiguration states — the reading is dropping entries").hasSizeGreaterThan(8);
        assertThat(indexOf(rules, HttpMethod.GET, "/api/patients/export"))
            .as("the admin-only patient export is not being read, so this guard is not reading SecurityConfiguration")
            .isGreaterThan(-1);
    }

    /**
     * <b>{@code GET /api/vendors} is stated, and it still admits the console.</b>
     *
     * <p>Backlog item 31. The rule is what lets a {@code ROLE_VENDOR} token — issued by hc-vendor's
     * gateway against the shared signing key — reach the vendor directory at all.
     *
     * <p>The other half is the regression that would be easy to miss: taking this path out of the
     * blanket rule's reach means the authorities it does <em>not</em> name lose it, so an
     * administrator or an operator dropped from this line loses the directory screen entirely. Both
     * are asserted here as well as behaviourally in {@code VendorScopeIT}.
     */
    @Test
    void theVendorRuleIsStatedAndKeepsTheConsole() {
        List<String> rules = rulesInOrder();
        int vendors = indexOf(rules, HttpMethod.GET, VENDORS);

        assertThat(vendors)
            .as(
                "GET %s is no longer stated in SecurityConfiguration. Without it the blanket read rule " +
                    "decides the path, which is admin-or-operator, and hc-vendor's portal cannot resolve a " +
                    "supplier to its directory record at all.",
                VENDORS
            )
            .isGreaterThan(-1);

        assertThat(rules.get(vendors))
            .as(
                "the GET %s rule no longer names all three authorities. First match wins, so this rule — " +
                    "not the blanket one — is what admins and operators are judged by on this path, and " +
                    "dropping either takes the console's vendor directory away.",
                VENDORS
            )
            .contains(AuthoritiesConstants.ADMIN)
            .contains(AuthoritiesConstants.OPERATOR)
            .contains(AuthoritiesConstants.VENDOR);
    }

    /**
     * <b>And it sits above the blanket {@code GET /api/**} rule.</b>
     *
     * <p>This is the assertion the class exists for. Below that rule the vendor matcher is never
     * evaluated: {@code hasAnyAuthority(ADMIN, OPERATOR)} answers first, a supplier's token is 403,
     * and the grant above reads as though it had been made. A test that only asserted the rule's
     * presence — including the one directly above this — would pass on exactly that chain.
     */
    @Test
    void theVendorRuleSitsAboveTheBlanketReadRule() {
        List<String> rules = rulesInOrder();
        int vendors = indexOf(rules, HttpMethod.GET, VENDORS);
        int blanketRead = indexOf(rules, HttpMethod.GET, BLANKET_READ);

        assertThat(vendors)
            .as(
                "GET %s is not stated at all, so it has no position to check — this is deletion rather " +
                    "than misplacement, and theVendorRuleIsStatedAndKeepsTheConsole says what it costs.",
                VENDORS
            )
            .isGreaterThan(-1);
        assertThat(blanketRead)
            .as("the blanket GET %s rule is gone — the whole read surface is now decided by something else", BLANKET_READ)
            .isGreaterThan(-1);

        assertThat(vendors)
            .as(
                "GET %s is now BELOW the blanket GET %s rule, which already matches it. The first match " +
                    "wins, so the vendor rule is never evaluated: a ROLE_VENDOR token is refused with 403 " +
                    "while the rule above still reads like a grant, and nothing else in this suite fails.",
                VENDORS,
                BLANKET_READ
            )
            .isLessThan(blanketRead);
    }

    /**
     * <b>{@code GET /api/vendors/{id}} is stated, above the blanket rule, and still keeps the
     * console.</b>
     *
     * <p>Backlog item 88, decided 2026-09-12. It is the same bargain as the listing rule with a
     * different scoping shape behind it: the matcher admits a supplier to a record addressed by an id
     * that has no relationship to the caller, and what keeps it to its own row is
     * {@code VendorResource.getVendor}, which loads the row and compares its {@code accountId}.
     *
     * <p>Below the blanket read rule this matcher is never evaluated — admin-or-operator answers
     * first, a supplier is 403, and the grant reads as though it had been made.
     * {@code ApiAuthorizationIT.aVendorReachesTheIdAddressedRecord} is the behavioural half, where the
     * same misplacement shows up as a 403 where a 404 is expected.
     *
     * <p>ADMIN and OPERATOR are asserted for the reason the listing rule asserts them: first match
     * wins, so this rule — not the blanket one — is what the console is judged by on this path now,
     * and dropping either takes the vendor record screen away.
     */
    @Test
    void theVendorRecordRuleSitsAboveTheBlanketReadRuleAndKeepsTheConsole() {
        List<String> rules = rulesInOrder();
        int record = indexOf(rules, HttpMethod.GET, VENDOR_RECORD);
        int blanketRead = indexOf(rules, HttpMethod.GET, BLANKET_READ);

        assertThat(record)
            .as(
                "GET %s is no longer stated in SecurityConfiguration. Without it the blanket read rule " +
                    "decides the path, which is admin-or-operator, and a supplier cannot read its own " +
                    "record at all — backlog item 88.",
                VENDOR_RECORD
            )
            .isGreaterThan(-1);
        assertThat(blanketRead)
            .as("the blanket GET %s rule is gone — the whole read surface is now decided by something else", BLANKET_READ)
            .isGreaterThan(-1);

        assertThat(record)
            .as(
                "GET %s is now BELOW the blanket GET %s rule, which already matches it. First match wins, " +
                    "so the rule is never evaluated: a ROLE_VENDOR token is 403 while the line above still " +
                    "reads like a grant.",
                VENDOR_RECORD,
                BLANKET_READ
            )
            .isLessThan(blanketRead);

        assertThat(rules.get(record))
            .as(
                "the GET %s rule no longer names all three authorities — and it, not the blanket rule, is what decides this path",
                VENDOR_RECORD
            )
            .contains(AuthoritiesConstants.ADMIN)
            .contains(AuthoritiesConstants.OPERATOR)
            .contains(AuthoritiesConstants.VENDOR);
    }

    /**
     * <b>And the summary rule sits above {@code {id}}, naming the console's authorities alone.</b>
     *
     * <p>This is the assertion item 88 added the class a second reason to exist for, and the ordering
     * it grades is between two carve-outs rather than between a carve-out and the blanket rule.
     * {@code {id}} is a <em>single-segment wildcard</em>, so {@code /api/vendors/summary} matches it:
     * put the summary rule second and the record rule answers first, admits a supplier on
     * {@code ROLE_VENDOR}, and MVC then routes the request to the summary handler — which counts every
     * vendor on the platform and takes no account of who is asking. Nothing else in this suite would
     * see it as an ordering problem; {@code ApiAuthorizationIT.aVendorReachesNothingElse} sees it as a
     * 200 where it wanted a 403.
     *
     * <p>The authorities are asserted too, negatively: a summary rule that named {@code ROLE_VENDOR}
     * would sit in the right place and disclose the same thing.
     */
    @Test
    void theVendorSummaryRuleSitsAboveTheRecordRuleAndStaysTheConsoles() {
        List<String> rules = rulesInOrder();
        int summary = indexOf(rules, HttpMethod.GET, VENDOR_SUMMARY);
        int record = indexOf(rules, HttpMethod.GET, VENDOR_RECORD);

        assertThat(summary)
            .as(
                "GET %s is not stated. It is a literal segment behind the %s wildcard, so without a rule " +
                    "of its own the record rule decides it and every supplier can read the whole " +
                    "directory's counts.",
                VENDOR_SUMMARY,
                VENDOR_RECORD
            )
            .isGreaterThan(-1);
        assertThat(record).as("GET %s is not stated, so there is no ordering to grade here", VENDOR_RECORD).isGreaterThan(-1);

        assertThat(summary)
            .as(
                "GET %s is now BELOW GET %s, which matches it — {id} is a single-segment wildcard. The " +
                    "record rule answers first, a supplier is admitted on ROLE_VENDOR, and MVC routes the " +
                    "request to the summary handler regardless.",
                VENDOR_SUMMARY,
                VENDOR_RECORD
            )
            .isLessThan(record);

        assertThat(rules.get(summary))
            .as("the GET %s rule names ROLE_VENDOR. Its position would then be protecting nothing.", VENDOR_SUMMARY)
            .doesNotContain(AuthoritiesConstants.VENDOR);
    }

    /**
     * The reader is not reporting the order it was asked to check, and no fourth rule has appeared.
     *
     * <p>{@code indexOf} matches on rendered text, so a rule naming a longer path that merely
     * contains the shorter one would be found in its place. Every live rule under {@code /api/vendors}
     * is therefore checked rather than assumed.
     *
     * <p><b>This case was called {@code onlyTheListPathIsCarvedOut} and asserted a count of one, and
     * item 88 made that name describe a rule that no longer holds.</b> It is three now — the listing,
     * the summary and the record — and the name and the assertion moved together on purpose: a
     * carve-out count nudged upwards to make a suite green is exactly how the next one arrives
     * unexamined. Each of the three takes its path out of the blanket rule's reach, so each has to be
     * gated on what it discloses, and the two cases above are where that is argued.
     */
    @Test
    void exactlyTheThreeDecidedVendorPathsAreCarvedOut() {
        List<String> rules = rulesInOrder();

        assertThat(
            rules
                .stream()
                .filter(rule -> rule.contains("/api/vendors"))
                .toList()
        )
            .as(
                "the set of /api/vendors matchers has changed. Each one takes its path out of the blanket " +
                    "rule's reach, so a new one has to be gated on what it discloses — and a path one " +
                    "segment deep also matters to %s, which matches it.",
                VENDOR_RECORD
            )
            .hasSize(3);
    }
}
