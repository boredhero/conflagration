package dev.boredhero.conflagration.optimization;

/** Pure integer scaling for vanilla's private candidate-ignition helper. */
public final class VanillaRate {

    private static final int SAFE_MAX_ODDS = Integer.MAX_VALUE - 64;

    private VanillaRate() {
    }

    public static int scaleIgniteOdds(int original, double multiplier) {
        if (original <= 0 || multiplier == 1.0) {
            return original;
        }
        if (!(multiplier >= 1.0) || !Double.isFinite(multiplier)) {
            throw new IllegalArgumentException("multiplier must be finite and at least 1.0");
        }
        double scaled = original * multiplier;
        return scaled >= SAFE_MAX_ODDS ? SAFE_MAX_ODDS : Math.max(1, (int) Math.round(scaled));
    }
}
