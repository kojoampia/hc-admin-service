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
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz ->
                // prettier-ignore
                authz
                    .requestMatchers(mvc.matcher(HttpMethod.POST, "/api/authenticate")).permitAll()
                    .requestMatchers(mvc.matcher(HttpMethod.GET, "/api/authenticate")).permitAll()
                    .requestMatchers(mvc.matcher("/api/admin/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    // A patient reading their own daily plan is the one path that accepts a token this
                    // gateway never issues. It has to precede the blanket rules below, which would
                    // otherwise reject ROLE_PATIENT here before DutyRosterResource's @PreAuthorize
                    // — the narrower gate — ever runs.
                    .requestMatchers(mvc.matcher(HttpMethod.GET, "/api/duty-rosters/patient/**"))
                        .hasAnyAuthority(AuthoritiesConstants.ADMIN, AuthoritiesConstants.OPERATOR, AuthoritiesConstants.PATIENT)
                    // A professional reading their OWN roster and earnings. Like the patient rule
                    // above, this has to precede the blanket rules below: a clinician holds none of
                    // the authorities those require, so the request would be rejected here before
                    // ProfessionalSelfResource was ever reached.
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
                    // point of writing them out: a future sub-path — the professionals in a space,
                    // say — matches neither and falls to the blanket rules below, which is the safe
                    // direction to fail. A wildcard here would hand it to every authenticated caller
                    // in the network on the day it was written, with nothing to notice.
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
