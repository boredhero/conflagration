package dev.boredhero.conflagration.optimization;

/** How the experimental frontier engine reacts to mods that patch vanilla fire internals. */
public enum FrontierCompatibilityMode {
    /** Fall back to vanilla when a known claim/fire integration would be bypassed. */
    AUTO_STRICT,
    /** Run anyway. Intended only for pack authors prepared to validate every interaction. */
    FORCE_UNSAFE
}
