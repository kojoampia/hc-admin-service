package net.jojoaddison.security;

/**
 * Constants for Spring Security authorities.
 */
public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";

    /**
     * Read-only access to the admin surface. Seeded by the gateway's {@code AuthoritiesMigration}
     * and held by the {@code operator} account; see {@link net.jojoaddison.config.SecurityConfiguration}
     * for where the read/write split is drawn.
     */
    public static final String OPERATOR = "ROLE_OPERATOR";

    /**
     * Not issued by this stack's gateway, and — since 2026-09-04 — <b>honoured on no path here at
     * all</b>.
     *
     * <p>It arrives on tokens minted by {@code hc-patient-ms}, which shares the JWT signing key, and
     * was accepted on exactly one: a patient reading their own daily plan from
     * {@code GET /api/duty-rosters/patient/**}. That endpoint moved to {@code professionalservice}
     * with the rest of the roster, and the matcher went with it.
     *
     * <p><b>Kept rather than deleted, because the string still arrives.</b> A patient token can be
     * presented to this service tomorrow; what changed is that nothing here treats it as anything
     * but an authenticated caller holding no admin authority. {@code ApiAuthorizationIT} names this
     * constant to assert exactly that, and a test written against a bare literal would not have said
     * what it was asserting about.
     */
    public static final String PATIENT = "ROLE_PATIENT";

    /**
     * A supplier signing into the vendor portal, reading <b>its own directory row here and nothing
     * else</b>.
     *
     * <p>Like {@link #PATIENT} this is never issued by this stack's gateway. It arrives on tokens
     * minted by hc-vendor's gateway, which signs with the platform-wide {@code JWT_BASE64_SECRET},
     * and it is honoured on exactly one path: {@code GET /api/vendors}. See
     * {@link net.jojoaddison.config.SecurityConfiguration} for the matcher and
     * {@link net.jojoaddison.web.rest.VendorResource#getAllVendors} for the scoping, which are one
     * decision and not two — the authority admits the caller to a <em>list</em> endpoint, so without
     * the scoping it would admit every vendor to every vendor's row.
     *
     * <p><b>Unobtainable in every environment this workspace runs, as of 2026-09-11.</b>
     * hc-vendor's {@code authority.csv} carries the row and its {@code user_authority.csv} grants it
     * to nobody; the portal logins are that product's {@code quality/seed-data.py} to create and it
     * has never been run. So the rules here are built and cannot be exercised end to end — do not
     * read a green test as evidence that the path works, and see backlog item 31.
     *
     * <p>The trap that costs a day when it does become reachable: if the signing key is not shared,
     * this service cannot verify the relayed token and answers <b>401</b>, which hc-vendor surfaces
     * as {@code DIRECTORY_ERROR(401)} — the same shape as this authority having been taken away
     * again. Check the key before touching these rules.
     */
    public static final String VENDOR = "ROLE_VENDOR";

    public static final String USER = "ROLE_USER";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    private AuthoritiesConstants() {}
}
