package dev.boredhero.conflagration.integration;

/** How Conflagration should treat another mod's claim-based fire protection. */
public enum ClaimProtectionMode {
    /** Never touch it; whatever the other mod is configured to do stands. */
    LEAVE_ALONE,
    /** Turn claim fire protection on, so fire cannot spread into other teams' claims. */
    ENABLE,
    /** Turn claim fire protection off, so fire spreads everywhere. */
    DISABLE
}
