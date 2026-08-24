package dev.boredhero.conflagration.optimization;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ForestOutbreakPolicyTest {

    @Test
    void boundaryIsDeterministicAndIgnoresHeight() {
        long originKey = 987654321L;
        boolean outside = ForestOutbreakPolicy.outsideBoundary(
                42, originKey, 0, 0, 240, 0, 128, 128);
        assertTrue(outside);
        assertTrue(ForestOutbreakPolicy.outsideBoundary(
                42, originKey ^ (255L << 44), 0, 0, 240, 0, 128, 128));
        assertFalse(ForestOutbreakPolicy.outsideBoundary(
                42, originKey, 0, 0, 10, 10, 128, 128));
    }

    @Test
    void forestRequiresLeavesAndAWinningNaturalFuelRatio() {
        assertTrue(ForestOutbreakPolicy.isForestDominant(12, 12, 3));
        assertFalse(ForestOutbreakPolicy.isForestDominant(7, 30, 0));
        assertFalse(ForestOutbreakPolicy.isForestDominant(12, 3, 0));
        assertFalse(ForestOutbreakPolicy.isForestDominant(12, 12, 7));
    }

    @Test
    void nominalRadiusIsStableAndInsideConfiguredRange() {
        int radius = ForestOutbreakPolicy.radiusFor(123456789L, 60, 256);
        assertTrue(radius >= 60 && radius <= 256);
        assertTrue(ForestOutbreakPolicy.radiusFor(123456789L, 256, 60) == radius);
        assertTrue(ForestOutbreakPolicy.radiusFor(123456789L, 128, 128) == 128);
    }

    @Test
    void nominalRadiusVariesAcrossOutbreakSeeds() {
        Set<Integer> radii = new HashSet<>();
        for (long seed = 0; seed < 64; seed++) {
            radii.add(ForestOutbreakPolicy.radiusFor(seed * 0x9E3779B97F4A7C15L, 60, 256));
        }
        assertTrue(radii.size() > 40);
    }

    @Test
    void nearbyIgnitionsShareAnOverlappingOutbreakButDistantOnesDoNot() {
        long first = 1L;
        long nearby = 2L;
        long distant = 3L;
        assertTrue(ForestOutbreakPolicy.boundariesOverlap(
                42L, first, 0, 0, nearby, 18, -12, 60, 256));
        assertFalse(ForestOutbreakPolicy.boundariesOverlap(
                42L, first, 0, 0, distant, 2_000, 2_000, 60, 256));
    }

}
