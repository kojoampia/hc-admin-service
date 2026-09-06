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

    public static final String USER = "ROLE_USER";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    private AuthoritiesConstants() {}
}
