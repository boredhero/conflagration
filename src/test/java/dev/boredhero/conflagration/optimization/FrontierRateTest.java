package dev.boredhero.conflagration.optimization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class FrontierRateTest {

    @Test
    void rateMultiplierScalesMeanDelayWithoutChangingItsShapeParameter() {
        double base = FrontierRate.meanDelayTicks(0.25, 1.0);
        assertEquals(base / 2.0, FrontierRate.meanDelayTicks(0.25, 2.0));
        assertEquals(base / 4.0, FrontierRate.meanDelayTicks(0.25, 4.0));
    }

    @Test
    void rejectsInvalidRateInputs() {
        assertThrows(IllegalArgumentException.class, () -> FrontierRate.meanDelayTicks(0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> FrontierRate.meanDelayTicks(1.1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> FrontierRate.meanDelayTicks(0.5, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> FrontierRate.meanDelayTicks(0.5, Double.NaN));
    }
}
