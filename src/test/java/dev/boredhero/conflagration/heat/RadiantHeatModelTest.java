package dev.boredhero.conflagration.heat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RadiantHeatModelTest {

    @Test
    void oneCalibratedFireIsBelowTheDamageThresholdAtOneBlock() {
        double flux = RadiantHeatModel.incidentFlux(12.5, 1.0, 1.0, 1.0);
        assertEquals(0.995, flux, 0.001);
        assertEquals(0.0, RadiantHeatModel.doseIncrement(flux, 2.5, 10.0));
    }

    @Test
    void denseFireRaisesPowerSmoothlyAndSaturates() {
        assertEquals(1.0, RadiantHeatModel.densityMultiplier(1, 1.5));
        assertTrue(RadiantHeatModel.densityMultiplier(8, 1.5) > 1.25);
        assertTrue(RadiantHeatModel.densityMultiplier(100, 1.5) < 1.5);
    }

    @Test
    void fourKilowattsReachesDefaultPainDoseInAboutThirteenSeconds() {
        double dose = RadiantHeatModel.doseIncrement(4.0, 2.5, 12.57);
        assertEquals(1.33, dose, 0.01);
    }

    @Test
    void calibratedGlassGradientMatchesReferenceTimes() {
        assertTrue(glassAfter(5.0, 170) < 60.0);
        assertTrue(glassAfter(5.0, 185) > 60.0);
        assertTrue(glassAfter(9.0, 78) < 60.0);
        assertTrue(glassAfter(9.0, 88) > 60.0);
        assertTrue(glassAfter(2.5, 3_600) < 60.0);
    }

    @Test
    void defaultGameplayCompressionBreaksHouseWindowsDuringTheFire() {
        assertTrue(glassAfter(5.0 * 8.0, 12) < 60.0);
        assertTrue(glassAfter(5.0 * 8.0, 20) > 60.0);
    }

    @Test
    void radiantTemperatureIncreasesWithCombinedFlux() {
        double small = RadiantHeatModel.equivalentRadiantTemperatureC(1.0);
        double large = RadiantHeatModel.equivalentRadiantTemperatureC(10.0);
        assertTrue(large > small);
        assertTrue(small > 100.0);
    }

    private static double glassAfter(double flux, int seconds) {
        double gradient = 0.0;
        for (int second = 0; second < seconds; second++) {
            gradient = RadiantHeatModel.updateGlassGradient(gradient, flux, 1.0);
        }
        return gradient;
    }
}
