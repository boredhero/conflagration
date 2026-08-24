package dev.boredhero.conflagration.heat;

/** Wall-clock catch-up calculations for persistently tracked generated magma. */
final class MagmaCoolingClock {

    private static final long MILLIS_PER_TICK = 50L;

    private MagmaCoolingClock() {
    }

    static long offlineTicks(long savedGameTime,
                             long savedUnixMillis,
                             long currentGameTime,
                             long currentUnixMillis) {
        long wallMillis = Math.max(0L, currentUnixMillis - savedUnixMillis);
        long wallTicks = wallMillis / MILLIS_PER_TICK;
        long onlineTicks = Math.max(0L, currentGameTime - savedGameTime);
        return Math.max(0L, wallTicks - onlineTicks);
    }

    static long adjustedDueTick(long savedDueTick,
                                long offlineTicks,
                                long currentGameTime) {
        long adjusted = offlineTicks >= savedDueTick
                ? 0L
                : savedDueTick - offlineTicks;
        return Math.max(currentGameTime, adjusted);
    }
}
