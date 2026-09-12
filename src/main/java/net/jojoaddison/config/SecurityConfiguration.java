package net.jojoaddison.config;

import net.jojoaddison.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tech.jhipster.config.JHipsterProperties;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    private final JHipsterProperties jHipsterProperties;

    public SecurityConfiguration(JHipsterProperties jHipsterProperties) {
        this.jHipsterProperties = jHipsterProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        var mvc = PathPatternRequestMatcher.withDefaults();
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz ->
                // prettier-ignore
                authz
                    .requestMatchers(mvc.matcher(HttpMethod.POST, "/api/authenticate")).permitAll()
                    .requestMatchers(mvc.matcher(HttpMethod.GET, "/api/authenticate")).permitAll()
                    .requestMatchers(mvc.matcher("/api/admin/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    // THERE IS DELIBERATELY NO ROLE_PATIENT RULE HERE, as of 2026-09-04.
                    //
                    // One used to sit at this line, admitting a patient token to
                    // GET /api/duty-rosters/patient/** — the only path in this service that accepted
                    // an authority this gateway never issues. It went with the endpoint. A patient's
                    // day plan is now GET /api/duty-roster/customer/{customerId} on
                    // professionalservice, which owns the roster of record and holds the visits it is
                    // derived from, reached through hc-patient's own gateway.
                    //
                    // Do not put it back to "fix" a 403 from this service. The endpoint took its
                    // subject from the path and checked nothing about who was asking, so the rule was
                    // only ever safe because ROLE_PATIENT was rare; hc-professional refuses a caller
                    // who is not that customer with a 403 that an unknown id gets identically, which
                    // is the check this service had no relationship to perform.
                    // ApiAuthorizationIT asserts a patient token now reaches nothing at all here.
                    //
                    // A professional reading their OWN roster and earnings. Like the patient rule
                    // that used to sit above it, this has to precede the blanket rules below: a
                    // clinician holds none of the authorities those require, so the request would be
                    // rejected here before ProfessionalSelfResource was ever reached.
                    //
                    // Authentication alone is the whole gate, on purpose. These endpoints take no
                    // subject — ProfessionalSelfResource resolves the caller from the token, and no
                    // path variable or parameter can name anyone else — so identity is the security
                    // boundary and an authority check would constrain nothing further. Naming the
                    // nine clinical authorities here instead would copy hc-professional's authority
                    // list into a fourth repo, which is exactly the duplication that breaks silently
                    // when it is edited in only some of the places it appears.
                    //
                    // Narrower than it looks: this opens /api/professionals/me/**, not
                    // /api/professionals/**. The id-addressed earnings endpoint stays admin-gated by
                    // the rule below, and that is what stops a clinician reading a colleague's pay.
                    .requestMatchers(mvc.matcher(HttpMethod.GET, "/api/professionals/me/**")).authenticated()
                    // Geographic spaces as reference data. hc-professional stores a space id on a
                    // roster round and has to render a name beside it; the authorities its callers
                    // hold are its own, and this service does not know them, so naming them here
                    // would copy that list into a fourth repository — the same argument as the rule
                    // above. Authentication is the gate.
                    //
                    // Unlike that rule, this one DOES take a subject, so what an id buys has to be
                    // answered rather than dissolved: a place name, the kind of area it is, and the
                    // area around it. That is reference data — it describes a place, not a person,
                    // and it reads the same for everyone — which is why disclosure is acceptable
                    // here and would not be one path along. GeographicSpaceReferenceResource
                    // projects those four values and nothing else, so a field added to the entity
                    // later does not join them by default.
                    //
                    // Two exact matchers rather than /api/geographic-spaces/**, and that is the
                    // point of writing them out: a sub-path of two segments or more — the
                    // professionals in a space, say — matches neither and falls to the blanket rules
                    // below, which is the safe direction to fail. A wildcard here would hand it to
                    // every authenticated caller in the network on the day it was written.
                    //
                    // {id} is NOT that protection for a sibling one segment deep, and this comment
                    // said it was until 2026-09-02. {id} is a single-segment wildcard, so a later
                    // GET /api/geographic-spaces/export would match it, be admitted on
                    // authentication alone, and be routed by MVC to the new literal handler — an
                    // admin-shaped bulk read open to three stacks, with nothing failing. That is not
                    // hypothetical: /api/patients/export is exactly that shape and needed the
                    // admin-only matcher twelve lines below, which is what a reader adding a sibling
                    // here would be copying from.
                    //
                    // So: ANY future literal path under /api/geographic-spaces/ must bring its own
                    // matcher ABOVE these two, gated on what it actually discloses. Enforced rather
                    // than requested — ApiAuthorizationIT reads the handler mapping and fails if a
                    // third pattern appears on this path, so a sibling cannot be written without
                    // this decision being made.
                    .requestMatchers(mvc.matcher(HttpMethod.GET, "/api/geographic-spaces")).authenticated()
                    .requestMatchers(mvc.matcher(HttpMethod.GET, "/api/geographic-spaces/{id}")).authenticated()
                    // Bulk export is admin-only, and it has to precede the read rule below, which
                    // would otherwise hand it to every operator along with the rest of GET.
                    //
                    // The narrowing is deliberate and was taken on 2026-08-24. An operator can
                    // already read every patient in the directory a page at a time, so this looks
                    // inconsistent — but paging through a directory and downloading it are
                    // different acts. One is bounded by attention, leaves a request per page in the
                    // audit trail, and produces nothing that outlives the session; the other emits
                    // every patient on the platform as a file in one request, and what happens to
                    // that file afterwards is outside every control this stack has. The read/write
                    // split was drawn around what an operator needs in order to work, and bulk
                    // extraction is not on that list.
                    .requestMatchers(mvc.matcher(HttpMethod.GET, "/api/patients/export")).hasAuthority(AuthoritiesConstants.ADMIN)
                    // A supplier reading its own directory row, from hc-vendor's portal. Backlog
                    // item 31, and hc-vendor's vendor-portal-spec row 5.1.
                    //
                    // ABOVE the blanket read rule, like every carve-out in this file: below it,
                    // admin-or-operator answers first and a vendor token is 403 while this line goes
                    // on reading like a grant. SecurityConfigurationOrderIT asserts the position
                    // and not merely the rule, because a misplaced matcher fails nothing.
                    //
                    // The suffix is load-bearing: this repository's guard ends IT, and hc-admin-gateway
                    // has a different one, of the same name ending Test. Both exist, one per repo, and
                    // this line named the gateway's for a while (backlog item 94) — which read as
                    // plausible precisely because it resolves to something real, one repository over.
                    // Check which repository you are in before "correcting" it back.
                    //
                    // ADMIN and OPERATOR are named here as well, and dropping them is the regression
                    // this rule invites: first match wins, so once this matcher owns
                    // GET /api/vendors the blanket rule below never sees it and a vendor-only rule
                    // would take the directory away from the console it was built for.
                    //
                    // ⚠ The authority and the scoping are ONE decision. The list rule admits a vendor
                    // to an unfiltered list endpoint; what keeps it to its own row is
                    // VendorResource.getAllVendors, which derives the caller's accountId from the
                    // token and refuses a request naming anybody else. Neither half is safe alone,
                    // and each names the other where it is written. The {id} rule below is the same
                    // bargain with a different scoping shape, and item 88 argues it there.
                    //
                    // Three exact matchers rather than /api/vendors/**, and writing them out is the
                    // point: everything else under /api/vendors/ — a bulk export, a sub-collection —
                    // matches none of them and falls to the blanket rules below, which is the safe
                    // direction to fail.
                    .requestMatchers(mvc.matcher(HttpMethod.GET, "/api/vendors"))
                        .hasAnyAuthority(AuthoritiesConstants.ADMIN, AuthoritiesConstants.OPERATOR, AuthoritiesConstants.VENDOR)
                    // The summary tiles are the whole directory's counts, so they stay the console's.
                    //
                    // ⚠ THIS LINE HAS TO PRECEDE THE {id} RULE BELOW and is not a tidy-up: {id} is a
                    // single-segment wildcard, so /api/vendors/summary MATCHES IT. Below it, the
                    // id-addressed rule would answer first, admit a supplier on ROLE_VENDOR, and MVC
                    // would route the request to the literal handler — every vendor on the platform
                    // counted, for a caller entitled to one row. It is the trap the geographic-space
                    // comment above describes, live rather than hypothetical, because item 88 added
                    // a {id} carve-out to a path that already had a literal sibling.
                    .requestMatchers(mvc.matcher(HttpMethod.GET, "/api/vendors/summary"))
                        .hasAnyAuthority(AuthoritiesConstants.ADMIN, AuthoritiesConstants.OPERATOR)
                    // A supplier reading its own row by id — backlog item 88, decided 2026-09-12.
                    //
                    // ⚠ THE MATCHER ALONE IS THE INVERSE OF THE ITEM. An id-addressed read takes its
                    // subject from the path and has no relationship to the caller, so this grant on
                    // its own opens EVERY vendor row to EVERY vendor, for the price of guessing an
                    // id. What makes it a scoped read is VendorResource.getVendor, which loads the
                    // row and compares its accountId to the caller's own — load-and-compare, because
                    // an id identifies one document and there is no criterion to narrow. Neither
                    // half is safe alone and each names the other where it is written.
                    //
                    // The refusal there is a 404 and not a 403, since a 403 on an id-addressed read
                    // confirms the row exists. That is why this matcher stays permissive and the
                    // decision is the handler's: a chain that refused here could only refuse with
                    // the status that discloses.
                    .requestMatchers(mvc.matcher(HttpMethod.GET, "/api/vendors/{id}"))
                        .hasAnyAuthority(AuthoritiesConstants.ADMIN, AuthoritiesConstants.OPERATOR, AuthoritiesConstants.VENDOR)
                    // The read/write split. Everything else under /api is admin data: operators read
                    // it, only admins change it. Authentication alone is deliberately not enough —
                    // a bare ROLE_USER reaches nothing here.
                    .requestMatchers(mvc.matcher(HttpMethod.GET, "/api/**"))
                        .hasAnyAuthority(AuthoritiesConstants.ADMIN, AuthoritiesConstants.OPERATOR)
                    .requestMatchers(mvc.matcher("/api/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.matcher("/v3/api-docs/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.matcher("/management/health")).permitAll()
                    .requestMatchers(mvc.matcher("/management/health/**")).permitAll()
                    .requestMatchers(mvc.matcher("/management/info")).permitAll()
                    .requestMatchers(mvc.matcher("/management/prometheus")).permitAll()
                    .requestMatchers(mvc.matcher("/management/**")).hasAuthority(AuthoritiesConstants.ADMIN)
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
