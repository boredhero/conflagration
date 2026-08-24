package dev.boredhero.conflagration.optimization;

/** Pure geometry predicates shared by jump simulation, visuals, and unit tests. */
final class EmberArc {

    private EmberArc() {
    }

    static boolean isJump(int deltaX, int deltaZ) {
        return Math.max(Math.abs(deltaX), Math.abs(deltaZ)) > 1;
    }
}
