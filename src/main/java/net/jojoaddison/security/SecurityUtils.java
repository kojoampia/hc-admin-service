package net.jojoaddison.security;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Utility class for Spring Security.
 */
public final class SecurityUtils {

    public static final MacAlgorithm JWT_ALGORITHM = MacAlgorithm.HS512;

    public static final String AUTHORITIES_KEY = "auth";

    /**
     * Claim carrying the authenticated account's database id, minted by the gateway alongside the
     * login it puts in {@code sub}. See {@link #getCurrentUserId()}.
     */
    public static final String USER_ID_KEY = "uid";

    private SecurityUtils() {}

    /**
     * Get the login of the current user.
     *
     * @return the login of the current user.
     */
    public static Optional<String> getCurrentUserLogin() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional.ofNullable(extractPrincipal(securityContext.getAuthentication()));
    }

    /**
     * The database id of the current user, as opposed to their login.
     *
     * <p>This is the identifier the domain documents reference: the seed data puts
     * {@code a0eebc99-…-a11} in {@code createdBy}, and CLAUDE.md names those ids a contract shared
     * with hc-patient-ms and hc-professional-service. The login in {@code sub} is a different
     * identifier space and must not be substituted for it.
     *
     * <p>Empty when the request carries no {@code uid} claim — seed loading, service-to-service
     * calls, or a token minted before the gateway added the claim. Callers should fall back to
     * {@code Constants.SYSTEM} rather than reaching for the login.
     *
     * @return the id of the current user.
     */
    public static Optional<String> getCurrentUserId() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Authentication authentication = securityContext.getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getClaimAsString(USER_ID_KEY)).filter(id -> !id.isBlank());
        }
        return Optional.empty();
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof UserDetails springSecurityUser) {
            return springSecurityUser.getUsername();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        } else if (authentication.getPrincipal() instanceof String s) {
            return s;
        }
        return null;
    }

    /**
     * Get the JWT of the current user.
     *
     * <p><b>⚠ This reads the authentication's credentials as a {@code String}, and no request to
     * this service produces one.</b> {@code SecurityConfiguration} configures
     * {@code oauth2ResourceServer(oauth2 -> oauth2.jwt(...))}, so an authenticated request arrives
     * as a {@code JwtAuthenticationToken}, whose base class passes the token as token, principal
     * <em>and</em> credentials — verified in the bytecode of Spring Security <b>7.1.0</b>'s
     * {@code AbstractOAuth2TokenAuthenticationToken}, whose two-argument constructor is
     * {@code aload_1, aload_1, aload_1}. So {@code getCredentials()} answers a {@code Jwt} and this
     * method answers <b>empty on every real request</b>.
     *
     * <p>(This said <b>7.1.1</b> until 2026-09-09, and no such artifact is on this classpath:
     * {@code spring-boot-dependencies:4.1.0} manages Spring Security at {@code 7.1.0}, which is what
     * {@code dependency:list} resolves. The mechanism is identical in both and the claim was true —
     * but it is the one fact this javadoc exists to establish, so a reader who checked it against the
     * jar would have found nothing there to check. Corrected as part of backlog item 57, which is the
     * defect this paragraph describes, in the client that adopted the method below.)
     *
     * <p>It is generated JHipster code and is left exactly as it is on purpose: changing it would
     * turn every existing caller's "no token" branch into a live outbound call, which is a decision
     * about those features rather than about this method. <b>Use
     * {@link #getCurrentRequestJwt()} for anything that relays a token</b>; it covers this shape as
     * well, so it is a drop-in replacement wherever this is called today.
     *
     * @return the JWT of the current user.
     */
    public static Optional<String> getCurrentUserJWT() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional.ofNullable(securityContext.getAuthentication())
            .filter(authentication -> authentication.getCredentials() instanceof String)
            .map(authentication -> (String) authentication.getCredentials());
    }

    /**
     * The raw bearer token this request arrived with, whatever shape the authentication is in.
     *
     * <h2>Why this exists beside {@link #getCurrentUserJWT()} rather than replacing it</h2>
     *
     * <p>Backlog item 50. A cross-stack client relays the caller's own token — the three gateways
     * share one signing key, so a token this service received is accepted by the siblings — and
     * {@code getCurrentUserJWT()} cannot supply it: it filters credentials on {@code instanceof
     * String}, and this service's resource-server chain produces a {@code JwtAuthenticationToken}
     * whose credentials are the decoded {@code Jwt}. The failure is silent by construction, because
     * "no token" is a legitimate state on an unauthenticated request and every caller has a branch
     * for it — so a relay that never relays looks exactly like a deployment that is not configured
     * for one.
     *
     * <p>Both readings are here rather than one, so this is safe to adopt anywhere the older method
     * is called: the {@code String} branch keeps whatever behaviour a caller has today, and the
     * {@code JwtAuthenticationToken} branch is the one that fires in production.
     *
     * <p><b>⚠ That first branch returns any non-blank credential, and a test fixture can supply one
     * — so a green test is not on its own evidence that a relay works.</b> {@code @WithMockUser}
     * builds a {@code UsernamePasswordAuthenticationToken} whose credentials are literally
     * {@code "password"}, so an {@code *ResourceIT} running {@code addFilters = false} with a sibling
     * client enabled would relay {@code Bearer password} and pass against any stub that does not
     * check the header. No test does that today and no production request can reach it — every real
     * caller arrives through the resource-server chain. It is called out because the failure it would
     * produce is the one this method exists to end: a suite proving a relay that cannot work. Assert
     * the token, through the real chain, as {@code PatientNameRelayIT} and {@code RoundRelayIT} do.
     *
     * <p>The branch is <b>not</b> narrowed to exclude it. It is item 50's deliberate compatibility
     * path — the reason this method is a drop-in for {@link #getCurrentUserJWT()} — and tightening it
     * would trade a documented trap for a silent behaviour change in callers that have nothing to do
     * with either item.
     *
     * <p><b>{@code getTokenValue()} and not a re-encode.</b> {@code Jwt} holds the compact
     * serialization it was decoded from, so this is the caller's token byte for byte — signature
     * included, which is the whole point. Rebuilding one from the claims would need this service to
     * hold a signing key it deliberately does not use, and would mint a new token rather than pass
     * one on.
     *
     * @return the compact token, or empty when the request carries none.
     */
    public static Optional<String> getCurrentRequestJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        if (authentication.getCredentials() instanceof String token && !token.isBlank()) {
            return Optional.of(token);
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return Optional.ofNullable(jwtAuthentication.getToken())
                .map(Jwt::getTokenValue)
                .filter(token -> !token.isBlank());
        }
        return Optional.empty();
    }

    /**
     * Check if a user is authenticated.
     *
     * @return true if the user is authenticated, false otherwise.
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && getAuthorities(authentication).noneMatch(AuthoritiesConstants.ANONYMOUS::equals);
    }

    /**
     * Checks if the current user has any of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has any of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserAnyOfAuthorities(String... authorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (
            authentication != null && getAuthorities(authentication).anyMatch(authority -> Arrays.asList(authorities).contains(authority))
        );
    }

    /**
     * Checks if the current user has none of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has none of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserNoneOfAuthorities(String... authorities) {
        return !hasCurrentUserAnyOfAuthorities(authorities);
    }

    /**
     * Checks if the current user has a specific authority.
     *
     * @param authority the authority to check.
     * @return true if the current user has the authority, false otherwise.
     */
    public static boolean hasCurrentUserThisAuthority(String authority) {
        return hasCurrentUserAnyOfAuthorities(authority);
    }

    private static Stream<String> getAuthorities(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority);
    }
}
