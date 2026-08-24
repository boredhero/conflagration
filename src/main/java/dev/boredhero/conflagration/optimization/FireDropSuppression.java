package dev.boredhero.conflagration.optimization;

/**
 * Marks synchronous block-removal cascades caused by FireBlock burnout.
 *
 * <p>A mutable holder avoids boxing for nested removals. The scope is entered only when fire has
 * actually selected a block for removal, not for unsuccessful face checks. The server world is
 * single-threaded, while the ThreadLocal keeps integrated client/server and test threads isolated.
 */
public final class FireDropSuppression {

    private static final ThreadLocal<Depth> DEPTH = new ThreadLocal<>();

    private FireDropSuppression() {
    }

    public static void enter() {
        Depth depth = DEPTH.get();
        if (depth == null) {
            depth = new Depth();
            DEPTH.set(depth);
        }
        depth.value++;
    }

    public static void exit() {
        Depth depth = DEPTH.get();
        if (depth == null || depth.value == 0) {
            throw new IllegalStateException("Fire drop-suppression scope underflow");
        }
        if (--depth.value == 0) {
            DEPTH.remove();
        }
    }

    public static boolean active() {
        Depth depth = DEPTH.get();
        return depth != null && depth.value != 0;
    }

    private static final class Depth {
        private int value;
    }
}
