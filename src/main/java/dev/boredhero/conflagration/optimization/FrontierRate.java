package dev.boredhero.conflagration.optimization;

/** Pure hazard-rate math for the FRONTIER engine. */
final class FrontierRate {

    private static final double BASE_MEAN_SCALE_TICKS = 35.0;

    private FrontierRate() {
    }

    static double meanDelayTicks(double ignitionProbability, double speedMultiplier) {
        if (!(ignitionProbability > 0.0 && ignitionProbability <= 1.0)) {
            throw new IllegalArgumentException("ignitionProbability must be in (0,1]");
        }
        if (!(speedMultiplier > 0.0) || !Double.isFinite(speedMultiplier)) {
            throw new IllegalArgumentException("speedMultiplier must be finite and positive");
        }
        return BASE_MEAN_SCALE_TICKS / (ignitionProbability * speedMultiplier);
    }
}
