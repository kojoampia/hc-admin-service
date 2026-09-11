package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.config.VendorAccountIndexes;
import net.jojoaddison.domain.Vendor;
import net.jojoaddison.repository.VendorRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What a {@code ROLE_VENDOR} token may read from the vendor directory — backlog item 31, and
 * hc-vendor's {@code vendor-portal-spec.md} row 5.1.
 *
 * <h2>Why this runs with the filter chain on</h2>
 *
 * <p>Every other {@code *ResourceIT} in this package runs {@code addFilters = false}, so none of
 * them holds an authority that {@link net.jojoaddison.config.SecurityConfiguration} would act on and
 * none of them could tell a scoped read from an unscoped one. The scope here is a function of the
 * <em>token</em>, so a test that does not present one is testing nothing:
 * {@code SecurityUtils.getCurrentUserLogin()} reads the JWT's subject, and with the chain off there
 * is no JWT to read.
 *
 * <p>{@link ApiAuthorizationIT} is the other class in this repository with filters on. It asserts
 * which authorities reach the endpoint; this asserts what they see when they do. The two halves are
 * one decision — the authority admits a vendor to a <em>list</em> endpoint, and the scoping is the
 * only thing between that grant and one supplier reading another's record — so neither file is
 * complete without the other, and each says so.
 *
 * <p>Authorities and the subject both come from the {@code jwt()} post-processor, for the reason
 * {@code ApiAuthorizationIT}'s javadoc gives at length: this is a resource server with
 * {@code STATELESS} sessions, so a {@code @WithMockUser} context is discarded before the request is
 * authorized. It is also the only shape that produces the authentication a real request produces —
 * {@code @WithMockUser}'s principal is a {@code String}, so a login read from it would prove nothing
 * about a {@code JwtAuthenticationToken}.
 *
 * <p><b>⚠ None of this can be exercised end to end, and a green run here is not evidence that it
 * works.</b> No principal can obtain a {@code ROLE_VENDOR} token in any environment this workspace
 * runs — hc-vendor grants the authority to nobody and its portal logins have never been seeded — so
 * what is proven here is this service's half against a token constructed in a test.
 */
@IntegrationTest
@AutoConfigureMockMvc
class VendorScopeIT {

    private static final String PATH = "/api/vendors";

    /** The vendor the caller is, in every case below. Lower-case, like every gateway login. */
    private static final String OWN_LOGIN = "kaneshie";

    /** The vendor the caller is not, which is the whole point of the class. */
    private static final String OTHER_LOGIN = "ridge";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private VendorAccountIndexes vendorAccountIndexes;

    private Vendor own;
    private Vendor other;

    /**
     * A token for a supplier, holding {@code ROLE_VENDOR} and naming itself in {@code sub}.
     *
     * <p>The subject is the whole identity: {@code Vendor.accountId} holds a vendor-gateway login and
     * the gateway puts the login in {@code sub}. No claim is invented here — {@code uid} is this
     * estate's <em>account id</em> claim and names a user document in a gateway this service cannot
     * read, so it is not the join.
     */
    private static JwtRequestPostProcessor vendor(String login) {
        return as(AuthoritiesConstants.VENDOR).jwt(builder -> builder.subject(login));
    }

    private static JwtRequestPostProcessor as(String... authorities) {
        return jwt().authorities(
            Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(GrantedAuthority[]::new)
        );
    }

    @BeforeEach
    void seed() {
        vendorRepository.deleteAll();
        own = vendorRepository.save(VendorResourceIT.createEntity().accountId(OWN_LOGIN));
        other = vendorRepository.save(VendorResourceIT.createEntity().accountId(OTHER_LOGIN));
        // A third with no portal login at all — the ordinary directory entry, and the row a scoped
        // read must not pick up either.
        vendorRepository.save(VendorResourceIT.createEntity().accountId(null));
    }

    @AfterEach
    void cleanup() {
        vendorRepository.deleteAll();
        // Whatever a case did to the index, the next class finds it as it was. See
        // theAmbiguityIsRefusedRatherThanAnswered, which is the only case that touches it.
        vendorAccountIndexes.createAccountIdIndex();
    }

    // --- the scope itself --------------------------------------------------------------------------

    /**
     * <b>The case this whole item exists for: vendor A cannot read vendor B's row.</b>
     *
     * <p>Watched red against the authority grant with no scoping behind it, where it answered 200
     * with B's record — which is what the grant does on its own, because {@code accountId.equals} is
     * a parameter the caller chooses to send.
     *
     * <p>A refusal rather than a silent substitution of the caller's own login. A vendor asking about
     * somebody else is a portal bug or a probe, and answering it with the caller's own row would
     * report success for a request that was not honoured — the shape of failure this service refuses
     * elsewhere. The body is asserted as well as the status, because a 403 whose body carried the row
     * would be the same disclosure with a different number on it.
     */
    @Test
    void aVendorCannotReadAnotherVendorsRow() throws Exception {
        String body = mvc
            .perform(get(PATH).param("accountId.equals", OTHER_LOGIN).with(vendor(OWN_LOGIN)))
            .andExpect(status().isForbidden())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(body).as("the refused row must not be in the refusal").doesNotContain(other.getId());
    }

    /**
     * And sending no filter at all does not open the directory.
     *
     * <p>The likelier mistake of the two: a portal that never learned to send the filter, or a caller
     * who simply lists the endpoint. Unscoped this answers with every vendor on the platform and
     * looks entirely healthy.
     */
    @Test
    void aVendorSendingNoFilterGetsOnlyItsOwnRow() throws Exception {
        mvc.perform(get(PATH).param("size", "100").with(vendor(OWN_LOGIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(own.getId()))
            // The count the pager reads has to be scoped too: a total of three over a page of one
            // draws two more pages of a directory the caller may not see.
            .andExpect(header().string("X-Total-Count", "1"));
    }

    /** Its own login, in the casing a human would type it, still resolves to itself. */
    @Test
    void aVendorMayNameItself() throws Exception {
        mvc.perform(get(PATH).param("accountId.equals", "  Kaneshie ").with(vendor(OWN_LOGIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(own.getId()));
    }

    /**
     * A vendor whose login names no row here gets an empty page, not the directory.
     *
     * <p>This is the ordinary state on the day a supplier is created in hc-vendor and not yet in this
     * console, and it is exactly where a scope that silently falls back to "no filter" does its
     * damage: the failure would be indistinguishable from the feature working.
     */
    @Test
    void aVendorWithNoRowHereSeesNothing() throws Exception {
        mvc.perform(get(PATH).param("size", "100").with(vendor("nobody")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0))
            .andExpect(header().string("X-Total-Count", "0"));
    }

    /**
     * A {@code ROLE_VENDOR} token carrying no login is refused rather than scoped to nothing.
     *
     * <p>It is a broken credential, not a supplier with no rows, and the two need different answers —
     * hc-vendor's own {@code VendorScopeResolver} makes the same distinction for the same reason. The
     * dangerous alternative is not "answer nothing" but "answer with no filter", which is what a
     * blank login does to {@code NamedFilters} one layer along.
     */
    @Test
    void aVendorTokenWithNoLoginIsRefused() throws Exception {
        mvc.perform(get(PATH).with(as(AuthoritiesConstants.VENDOR).jwt(builder -> builder.subject(" ")))).andExpect(status().isForbidden());
    }

    /** A blank filter is still a 400 and not an empty scope — the refusal #89 added, on this path. */
    @Test
    void aBlankFilterIsStillRefused() throws Exception {
        mvc.perform(get(PATH).param("accountId.equals", "").with(vendor(OWN_LOGIN))).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).param("accountId.equals", "").with(as(AuthoritiesConstants.ADMIN))).andExpect(status().isBadRequest());
    }

    // --- the console, which must not have changed -------------------------------------------------

    /**
     * <b>The regression most likely to slip through.</b>
     *
     * <p>The new matcher takes {@code GET /api/vendors} out of the blanket read rule's reach — first
     * match wins — so an administrator or an operator left off it loses the directory entirely, and a
     * scope keyed on "not a vendor" that got its precedence wrong would hand them one row instead of
     * the collection. Both are asserted on the count rather than on the status.
     */
    @Test
    void anAdministratorStillReadsTheWholeDirectory() throws Exception {
        mvc.perform(get(PATH).param("size", "100").with(as(AuthoritiesConstants.ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(header().string("X-Total-Count", "3"));
    }

    @Test
    void anOperatorStillReadsTheWholeDirectory() throws Exception {
        mvc.perform(get(PATH).param("size", "100").with(as(AuthoritiesConstants.OPERATOR)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(header().string("X-Total-Count", "3"));
    }

    /** And the console may still resolve a login to a vendor, which is what the filter was added for. */
    @Test
    void anAdministratorMayStillResolveAnyLogin() throws Exception {
        mvc.perform(get(PATH).param("accountId.equals", OTHER_LOGIN).with(as(AuthoritiesConstants.ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(other.getId()));
    }

    /**
     * A principal holding both authorities is unscoped, and that is decided rather than inherited.
     *
     * <p>No such account exists or is expected. It is asserted because a scope has to be a function
     * of the token and not of the order two authorities happen to appear in — the same answer
     * hc-vendor's resolver gives, so the two products cannot disagree about one token.
     */
    @Test
    void anAdministratorWhoAlsoHoldsTheVendorAuthorityIsNotScoped() throws Exception {
        mvc.perform(
            get(PATH)
                .param("size", "100")
                .with(as(AuthoritiesConstants.ADMIN, AuthoritiesConstants.VENDOR).jwt(builder -> builder.subject(OWN_LOGIN)))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3));
    }

    // --- the invariant the index is supposed to hold, and what happens when it does not -------------

    /**
     * <b>Two vendors on one login are refused, loudly, at the read.</b>
     *
     * <p>{@link VendorAccountIndexes} creates a unique sparse index on {@code account_id} and — like
     * the other two index creators in {@code config/} — <em>reports and continues</em> when it cannot,
     * rather than failing startup over a data problem. That policy is only defensible if the read
     * itself refuses, because otherwise the service runs believing an invariant nothing is enforcing:
     * the scope would silently become "the first of two rows", which is one supplier holding another
     * supplier's record.
     *
     * <p>So this case reproduces exactly the state that policy permits — <b>index dropped, duplicates
     * present</b> — which is also the only way to write it: with the index in place MongoDB refuses
     * the second insert, and a test that could not create the data would be asserting nothing. The
     * index is restored in {@code cleanup()}, so nothing that runs afterwards sees the collection
     * without it.
     *
     * <p>409 and not 500: the request is well formed and the answer is a conflict in the data. Both
     * callers are covered — the vendor whose scope is ambiguous, and the administrator resolving a
     * login through the same filter, which is the reconciliation hc-vendor calls and which must not
     * be told "here is your vendor" when this database holds two.
     */
    @Test
    void theAmbiguityIsRefusedRatherThanAnswered() throws Exception {
        mongoTemplate.indexOps(Vendor.class).dropIndex(VendorAccountIndexes.ACCOUNT_ID_INDEX);
        vendorRepository.save(VendorResourceIT.createEntity().accountId(OWN_LOGIN));

        assertThat(
            vendorRepository
                .findAll()
                .stream()
                .filter(v -> OWN_LOGIN.equals(v.getAccountId()))
                .toList()
        )
            .as("the duplicate this case is about was not created, so nothing below is being asserted")
            .hasSize(2);

        mvc.perform(get(PATH).with(vendor(OWN_LOGIN))).andExpect(status().isConflict());
        mvc.perform(get(PATH).param("accountId.equals", OWN_LOGIN).with(as(AuthoritiesConstants.ADMIN))).andExpect(status().isConflict());

        // An unrelated login is unaffected: the refusal is about one caller's rows, not the endpoint.
        mvc.perform(get(PATH).param("accountId.equals", OTHER_LOGIN).with(as(AuthoritiesConstants.ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    /**
     * And the index is really there — the prevention half, asserted where the enforcement half is.
     *
     * <p>Unique <em>and</em> sparse, both read off the running database rather than off the source
     * that asked for them. Sparse is not decoration: {@code accountId} is null on most vendors, a
     * plain unique index treats every missing value as one shared null, and the second unlinked
     * vendor would be refused — which {@code seed()} above would have discovered by failing.
     */
    @Test
    void theAccountIdIndexExistsAndIsUniqueAndSparse() {
        List<org.springframework.data.mongodb.core.index.IndexInfo> indexes = mongoTemplate.indexOps(Vendor.class).getIndexInfo();

        assertThat(indexes)
            .as("no index named %s on the vendor collection — see VendorAccountIndexes", VendorAccountIndexes.ACCOUNT_ID_INDEX)
            .anySatisfy(index -> {
                assertThat(index.getName()).isEqualTo(VendorAccountIndexes.ACCOUNT_ID_INDEX);
                assertThat(index.isUnique()).as("the index is not unique, so it prevents nothing").isTrue();
                assertThat(index.isSparse()).as("the index is not sparse, so two vendors with no login collide").isTrue();
            });
    }
}
