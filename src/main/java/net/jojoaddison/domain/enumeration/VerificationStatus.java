package net.jojoaddison.domain.enumeration;

/**
 * The VerificationStatus enumeration.
 */
public enum VerificationStatus {
    VERIFIED,
    PENDING,
    REJECTED,
    /** Verified once and withdrawn since — distinct from REJECTED, which was never granted. */
    REVOKED,
    /** Lapsed by its own {@code expiresOn} rather than by anybody's decision. */
    EXPIRED,
}
