package dev.boredhero.conflagration.heat;

/** Pure fire-radiation, exposure-dose, and glazing-gradient equations. */
public final class RadiantHeatModel {

    private static final double STEFAN_BOLTZMANN = 5.670374419e-8;
    private static final double AMBIENT_KELVIN = 293.15;
    private static final double FOUR_PI = 4.0 * Math.PI;
    private static final double GLASS_AREAL_HEAT_CAPACITY = 8.2; // kJ / (m^2 K), 4 mm soda-lime
    private static final double GLASS_EFFECTIVE_ABSORPTION = 0.78;
    private static final double GLASS_TIME_CONSTANT_SECONDS = 240.0;

    private RadiantHeatModel() {
    }

    public static double ageWeight(int fireAge) {
        int clamped = Math.max(0, Math.min(15, fireAge));
        return 1.0 - 0.5 * clamped / 15.0;
    }

    public static double densityMultiplier(int localFireCount, double maximumMultiplier) {
        if (localFireCount <= 1 || maximumMultiplier <= 1.0) {
            return 1.0;
        }
        // Smooth saturation avoids an artificial threshold as a cluster grows.
        return 1.0 + (maximumMultiplier - 1.0)
                * (1.0 - StrictMath.exp(-(localFireCount - 1.0) / 8.0));
    }

    public static double incidentFlux(double sourcePowerKw,
                                      double ageWeight,
                                      double densityMultiplier,
                                      double distanceSquared) {
        double softenedDistanceSquared = Math.max(0.75 * 0.75, distanceSquared);
        return sourcePowerKw * ageWeight * densityMultiplier
                / (FOUR_PI * softenedDistanceSquared);
    }

    /** ISO/SFPE-style radiant dose in (kW/m^2)^(4/3) * minutes. */
    public static double doseIncrement(double fluxKwM2,
                                       double thresholdKwM2,
                                       double elapsedSeconds) {
        if (fluxKwM2 < thresholdKwM2 || elapsedSeconds <= 0.0) {
            return 0.0;
        }
        return StrictMath.pow(fluxKwM2, 4.0 / 3.0) * elapsedSeconds / 60.0;
    }

    public static double coolDose(double dose, double elapsedSeconds, double halfLifeSeconds) {
        if (dose <= 0.0 || elapsedSeconds <= 0.0) {
            return Math.max(0.0, dose);
        }
        return dose * StrictMath.pow(0.5, elapsedSeconds / halfLifeSeconds);
    }

    /** Equivalent black-body radiant temperature of the incident field, not local air temperature. */
    public static double equivalentRadiantTemperatureC(double fluxKwM2) {
        if (fluxKwM2 <= 0.0) {
            return AMBIENT_KELVIN - 273.15;
        }
        double kelvin = StrictMath.pow(
                StrictMath.pow(AMBIENT_KELVIN, 4.0)
                        + fluxKwM2 * 1_000.0 / STEFAN_BOLTZMANN,
                0.25);
        return kelvin - 273.15;
    }

    /** First-order center/edge thermal-gradient model for ordinary window glass. */
    public static double updateGlassGradient(double currentDeltaC,
                                             double fluxKwM2,
                                             double elapsedSeconds) {
        if (elapsedSeconds <= 0.0) {
            return Math.max(0.0, currentDeltaC);
        }
        double steadyState = GLASS_EFFECTIVE_ABSORPTION * Math.max(0.0, fluxKwM2)
                * GLASS_TIME_CONSTANT_SECONDS / GLASS_AREAL_HEAT_CAPACITY;
        double decay = StrictMath.exp(-elapsedSeconds / GLASS_TIME_CONSTANT_SECONDS);
        return Math.max(0.0, steadyState + (currentDeltaC - steadyState) * decay);
    }
}
