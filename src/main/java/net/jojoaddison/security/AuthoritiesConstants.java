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
     * Not issued by this stack's gateway. It arrives on tokens minted by {@code hc-patient-ms},
     * which shares the JWT signing key, and is honoured on exactly one path — a patient reading
     * their own daily plan. Nothing else in this service accepts it.
     */
    public static final String PATIENT = "ROLE_PATIENT";

    public static final String USER = "ROLE_USER";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    private AuthoritiesConstants() {}
}
