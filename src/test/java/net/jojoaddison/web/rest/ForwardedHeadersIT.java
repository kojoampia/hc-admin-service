package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code Link} headers must describe the URL the client actually called.
 *
 * <p>{@code PaginationUtil} builds them from the current request, and this service never sees the
 * request as the browser made it: nginx terminates TLS, and the gateway strips two path segments
 * before proxying. Left alone, a page fetched from
 * {@code https://admin.abofonsa.com/services/hcadminservice/api/profiles} advertised its siblings as
 * {@code http://admin.abofonsa.com/api/profiles} — wrong scheme, wrong path. The first is mixed
 * content that a browser blocks outright; the second is the gateway's own surface and 404s.
 *
 * <p>{@code server.forward-headers-strategy: framework} fixes it by registering Spring's
 * {@code ForwardedHeaderFilter}. That is a <strong>filter</strong>, which is why this test cannot
 * use {@code addFilters = false} the way every other {@code *ResourceIT} here does — with the chain
 * off, the header is rebuilt from the raw request and the assertions below pass against the broken
 * behaviour. That is the whole reason this file exists separately.
 *
 * <p><strong>This test cannot prove the fix works.</strong> It injects the headers directly, so it
 * shows only that this service rebuilds URLs correctly once they arrive. Whether they arrive is a
 * property of the gateway: Spring Cloud Gateway discards every {@code X-Forwarded-*} header unless
 * {@code spring.cloud.gateway.server.webflux.trusted-proxies} matches its caller, and the container
 * nginx must not overwrite {@code X-Forwarded-Proto} with its own scheme. Both were wrong when this
 * test was first written, and it passed anyway. Verify the chain end to end, not just this.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ForwardedHeadersIT {

    private static final String ENDPOINT = "/api/profiles";

    @Autowired
    private MockMvc restMockMvc;

    /**
     * Authorities go on the request, not into {@code TestSecurityContextHolder}. With filters on,
     * {@code @WithMockUser} is discarded — {@code SessionCreationPolicy.STATELESS} installs a null
     * {@code SecurityContextRepository} that loads an empty context over it, and every call 401s.
     * Same reason {@code ApiAuthorizationIT} does this.
     */
    private static JwtRequestPostProcessor asAdmin() {
        return jwt().authorities((GrantedAuthority[]) new SimpleGrantedAuthority[] { new SimpleGrantedAuthority("ROLE_ADMIN") });
    }

    @Test
    void schemeAndHostComeFromTheProxyHeaders() throws Exception {
        String link = restMockMvc
            .perform(
                get(ENDPOINT + "?page=0&size=20")
                    .with(asAdmin())
                    .header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Host", "admin.abofonsa.com")
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Link");

        assertThat(link).isNotNull();
        assertThat(link).contains("https://admin.abofonsa.com");
        // Not merely "starts with https" — the bug produced a well-formed URL on the wrong scheme,
        // so the absence of the plain-http origin is the assertion that would have caught it.
        assertThat(link).doesNotContain("http://admin.abofonsa.com");
    }

    @Test
    void prefixIsRestored() throws Exception {
        String link = restMockMvc
            .perform(
                get(ENDPOINT + "?page=0&size=20")
                    .with(asAdmin())
                    .header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Host", "admin.abofonsa.com")
                    .header("X-Forwarded-Prefix", "/services/hcadminservice")
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Link");

        assertThat(link).isNotNull();
        assertThat(link).contains("https://admin.abofonsa.com/services/hcadminservice/api/profiles");
    }

    @Test
    void withoutProxyHeadersTheRequestIsDescribedAsItArrived() throws Exception {
        // Direct calls — container to container, or a probe on the host — carry no forwarded
        // headers, and must not have a proxy's identity invented for them.
        String link = restMockMvc
            .perform(get(ENDPOINT + "?page=0&size=20").with(asAdmin()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Link");

        assertThat(link).isNotNull();
        assertThat(link).contains(ENDPOINT);
        assertThat(link).doesNotContain("admin.abofonsa.com");
    }
}
