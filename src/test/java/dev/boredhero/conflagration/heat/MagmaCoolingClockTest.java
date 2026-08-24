package dev.boredhero.conflagration.heat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MagmaCoolingClockTest {

    @Test
    void countsOnlyWallTimeNotAlreadyRepresentedByWorldTicks() {
        assertEquals(1_000L, MagmaCoolingClock.offlineTicks(
                1_000L, 10_000L, 3_000L, 160_000L));
    }

    @Test
    void doesNotDoubleCountOrdinaryOnlineRuntime() {
        assertEquals(0L, MagmaCoolingClock.offlineTicks(
                1_000L, 10_000L, 3_000L, 110_000L));
    }

    @Test
    void ignoresAClockThatMovedBackwards() {
        assertEquals(0L, MagmaCoolingClock.offlineTicks(
                1_000L, 10_000L, 1_000L, 5_000L));
    }

    @Test
    void overdueCoolingIsScheduledImmediatelyButNeverInThePast() {
        assertEquals(5_000L, MagmaCoolingClock.adjustedDueTick(
                5_500L, 1_000L, 5_000L));
        assertEquals(5_250L, MagmaCoolingClock.adjustedDueTick(
                6_250L, 1_000L, 5_000L));
    }
}
