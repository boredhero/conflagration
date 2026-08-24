package dev.boredhero.conflagration.optimization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EmberParticlesTest {

    @Test
    void visualsAreReservedForTheDistancePenalizedOuterRing() {
        assertFalse(EmberArc.isJump(0, 0));
        assertFalse(EmberArc.isJump(1, 0));
        assertFalse(EmberArc.isJump(-1, 1));
        assertTrue(EmberArc.isJump(2, 0));
        assertTrue(EmberArc.isJump(-2, 1));
        assertTrue(EmberArc.isJump(2, -2));
    }
}
