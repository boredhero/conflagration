package dev.boredhero.conflagration.optimization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class VanillaRateTest {

    @Test
    void oneIsExactAndZeroClaimVerdictsStayZero() {
        assertEquals(37, VanillaRate.scaleIgniteOdds(37, 1.0));
        assertEquals(0, VanillaRate.scaleIgniteOdds(0, 20.0));
    }

    @Test
    void scalesPositiveIgnitionOdds() {
        assertEquals(10, VanillaRate.scaleIgniteOdds(5, 2.0));
        assertEquals(13, VanillaRate.scaleIgniteOdds(5, 2.5));
    }

    @Test
    void rejectsSlowdownAndNonFiniteValues() {
        assertThrows(IllegalArgumentException.class, () -> VanillaRate.scaleIgniteOdds(5, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> VanillaRate.scaleIgniteOdds(5, Double.POSITIVE_INFINITY));
    }
}
