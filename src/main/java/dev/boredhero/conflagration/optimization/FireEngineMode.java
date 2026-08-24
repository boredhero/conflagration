package dev.boredhero.conflagration.optimization;

/** Which spread engine handles non-adjacent ignition attempts. */
public enum FireEngineMode {
    /** Minecraft's exact scheduled-tick spread algorithm, with allocation-only wrappers. */
    VANILLA,
    /** Experimental sparse event/frontier propagation. Behavior-changing and disabled by default. */
    FRONTIER
}
