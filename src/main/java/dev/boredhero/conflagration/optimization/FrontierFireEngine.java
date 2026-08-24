package dev.boredhero.conflagration.optimization;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Experimental sparse event/frontier spread engine.
 *
 * <p>Vanilla's lifecycle and six direct burnout checks run unchanged. This replaces only the 53
 * repeated air-candidate attempts after those checks. A source discovers viable ignition targets
 * occasionally, samples deterministic arrival delays, deduplicates arrivals by target, and fully
 * revalidates world state and contextual NeoForge hooks before placing fire.
 */
public final class FrontierFireEngine {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<ServerLevel, LevelState> LEVELS = new WeakHashMap<>();

    private FrontierFireEngine() {
    }

    /** @return true when the vanilla candidate loop should be skipped */
    public static boolean tick(ServerLevel level, BlockPos sourcePos, BlockState fireState) {
        if (!FirePerformance.frontierActive()) {
            return false;
        }

        BlockState currentFire = level.getBlockState(sourcePos);
        if (!currentFire.is(Blocks.FIRE)) {
            return true;
        }

        long now = level.getGameTime();
        LevelState state = LEVELS.computeIfAbsent(level, ignored -> new LevelState(now));
        state.beginTick(now);
        state.processDue(level, now);
        state.discover(level, sourcePos, currentFire, now);
        return true;
    }

    private static int getIgniteOdds(ServerLevel level, BlockPos target) {
        if (!level.isEmptyBlock(target)) {
            return 0;
        }
        int maximum = 0;
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbour = target.relative(direction);
            BlockState state = level.getBlockState(neighbour);
            maximum = Math.max(maximum,
                    state.getFireSpreadSpeed(level, neighbour, direction.getOpposite()));
        }
        return maximum;
    }

    private static boolean isNearRain(ServerLevel level, BlockPos pos) {
        return level.isRainingAt(pos)
                || level.isRainingAt(pos.west())
                || level.isRainingAt(pos.east())
                || level.isRainingAt(pos.north())
                || level.isRainingAt(pos.south());
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    private static int sampleDelay(ServerLevel level,
                                   long source,
                                   long target,
                                   int age,
                                   int odds,
                                   int verticalOffset) {
        int score = (odds + 40 + level.getDifficulty().getId() * 7) / (age + 30);
        if (level.getBiome(BlockPos.of(source)).is(BiomeTags.INCREASED_FIRE_BURNOUT)) {
            score /= 2;
        }
        if (score <= 0) {
            return -1;
        }

        int denominator = 100 + Math.max(0, verticalOffset - 1) * 100;
        double probability = Math.min(0.999, (score + 1.0) / denominator);
        // Scaling the hazard rate (rather than the sampled result or scan interval) retains the
        // exponential arrival distribution: 2x speed means exactly half the mean waiting time.
        double meanTicks = FrontierRate.meanDelayTicks(
                probability, FirePerformance.frontierSpreadSpeed());
        long hash = mix64(level.getSeed() ^ source ^ Long.rotateLeft(target, 23));
        double unit = ((hash >>> 11) + 1.0) * 0x1.0p-53;
        int delay = (int) Math.ceil(-StrictMath.log(unit) * meanTicks);
        return Math.max(1, Math.min(FrontierEventWheel.WHEEL_SIZE - 1, delay));
    }

    private static final class LevelState {
        private final FrontierEventWheel events;
        private final Long2LongOpenHashMap earliestByTarget = new Long2LongOpenHashMap();
        private final Long2LongOpenHashMap nextSourceScan = new Long2LongOpenHashMap();
        private long budgetTick = Long.MIN_VALUE;
        private int processedThisTick;
        private int sourcesThisTick;

        LevelState(long now) {
            events = new FrontierEventWheel(now);
            earliestByTarget.defaultReturnValue(Long.MAX_VALUE);
            nextSourceScan.defaultReturnValue(Long.MIN_VALUE);
        }

        void beginTick(long now) {
            if (budgetTick != now) {
                budgetTick = now;
                processedThisTick = 0;
                sourcesThisTick = 0;
            }
        }

        void processDue(ServerLevel level, long now) {
            int remaining = FirePerformance.frontierMaxEventsPerTick() - processedThisTick;
            if (remaining <= 0) {
                return;
            }
            processedThisTick += events.drainDue(now, remaining,
                    (due, source, target, age) -> execute(level, due, source, target, age));
        }

        void discover(ServerLevel level, BlockPos sourcePos, BlockState fireState, long now) {
            if (sourcesThisTick >= FirePerformance.frontierMaxSourcesPerTick()) {
                return;
            }
            long source = sourcePos.asLong();
            if (now < nextSourceScan.get(source)) {
                return;
            }
            sourcesThisTick++;
            nextSourceScan.put(source, now + FirePerformance.frontierRescanInterval());
            if (nextSourceScan.size() > FirePerformance.frontierMaxPendingEvents() * 2) {
                nextSourceScan.clear();
            }

            int age = fireState.getValue(FireBlock.AGE);
            BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    for (int y = -1; y <= 4; y++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }
                        target.setWithOffset(sourcePos, x, y, z);
                        if (!ClaimFireCompatibility.mayIgnite(level, sourcePos, target)) {
                            if (!FirePerformance.frontierActive()) {
                                return; // an adapter failed; the next fire tick resumes VANILLA
                            }
                            continue;
                        }
                        int odds = getIgniteOdds(level, target);
                        if (odds <= 0) {
                            continue;
                        }
                        long targetLong = target.asLong();
                        int delay = sampleDelay(level, source, targetLong, age, odds, y);
                        if (delay > 0) {
                            schedule(source, targetLong, age, now + delay);
                        }
                    }
                }
            }
        }

        void schedule(long source, long target, int sourceAge, long due) {
            if (events.size() >= FirePerformance.frontierMaxPendingEvents()) {
                return;
            }
            long existing = earliestByTarget.get(target);
            if (due >= existing) {
                return;
            }
            earliestByTarget.put(target, due);
            events.offer(due, source, target, sourceAge);
        }

        void execute(ServerLevel level, long due, long sourceLong, long targetLong, int sourceAge) {
            if (earliestByTarget.get(targetLong) != due) {
                return; // stale event superseded by an earlier arrival
            }
            earliestByTarget.remove(targetLong);

            BlockPos source = BlockPos.of(sourceLong);
            BlockPos target = BlockPos.of(targetLong);
            if (!level.getChunkSource().hasChunk(source.getX() >> 4, source.getZ() >> 4)
                    || !level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4)
                    || !level.getBlockState(source).is(Blocks.FIRE)
                    || !level.isEmptyBlock(target)) {
                return;
            }
            if (!ClaimFireCompatibility.mayIgnite(level, source, target)
                    || getIgniteOdds(level, target) <= 0
                    || (level.isRaining() && isNearRain(level, target))) {
                return;
            }

            BlockState placed = BaseFireBlock.getState(level, target);
            if (placed.is(Blocks.FIRE)) {
                long hash = mix64(level.getSeed() ^ sourceLong ^ Long.rotateLeft(targetLong, 11));
                int age = Math.min(15, sourceAge + ((hash & 3L) == 0L ? 1 : 0));
                placed = placed.setValue(FireBlock.AGE, age);
            }
            if (level.setBlock(target, placed, 3)) {
                nextSourceScan.remove(targetLong);
            }
        }
    }
}
