package dev.boredhero.conflagration.optimization;

import com.mojang.logging.LogUtils;
import dev.boredhero.conflagration.heat.FireHeatManager;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.Tags;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    private static final Logger LOG = LogUtils.getLogger();
    private static final int ORIGIN_FILE_MAGIC = 0x43464F52; // CFOR
    private static final int ORIGIN_FILE_VERSION = 1;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int STRUCTURAL_FUEL_FLAG = Integer.MIN_VALUE;
    private static final int FOREST_CACHE_TICKS = 200;
    private static final int OUTBREAK_ORIGIN_TTL_TICKS = 24_000;
    private static final int ORIGIN_PURGE_BUDGET = 128;
    private static final int MAX_ACTIVE_OUTBREAKS = 4_096;
    private static final int ORIGIN_INDEX_SHIFT = 10; // 1024-block cells
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
        LevelState state = LEVELS.computeIfAbsent(level, ignored -> new LevelState(level, now));
        state.beginTick(now);
        state.processDue(level, now);
        long origin = state.originFor(level, sourcePos.asLong(), now);
        state.discover(level, sourcePos, currentFire, origin, now);
        return true;
    }

    /** Applies the same outbreak boundary to vanilla's six direct burnout placements. */
    public static boolean mayPlaceDirect(ServerLevel level,
                                         BlockPos source,
                                         BlockPos target,
                                         BlockState targetFuel) {
        if (!FirePerformance.enabled()) {
            return true;
        }
        if (!ClaimFireCompatibility.mayIgnite(level, source, target)) {
            return false;
        }
        if (!FirePerformance.frontierActive()
                || !FirePerformance.frontierForestRadiusLimit()) {
            return true;
        }
        long now = level.getGameTime();
        LevelState state = LEVELS.computeIfAbsent(level, ignored -> new LevelState(level, now));
        long origin = state.originFor(level, source.asLong(), now);
        return state.allowsOutbreakSpread(
                level, source, target.asLong(), targetFuel, origin, now);
    }

    /** Preserves outbreak ancestry when checkBurnOut replaces a fuel block with fire. */
    public static void directPlaced(ServerLevel level, BlockPos source, BlockPos target) {
        if (!FirePerformance.frontierActive() || !level.getBlockState(target).is(Blocks.FIRE)) {
            return;
        }
        long now = level.getGameTime();
        LevelState state = LEVELS.computeIfAbsent(level, ignored -> new LevelState(level, now));
        long origin = state.originFor(level, source.asLong(), now);
        state.trackOrigin(target.asLong(), origin, now);
        FireHeatManager.observe(level, target, level.getBlockState(target));
    }

    /** Leaves flame behind when a removed log exposes the next log in a downward trunk column. */
    public static void placeTrunkDescent(ServerLevel level,
                                         BlockPos source,
                                         BlockPos target,
                                         BlockState removedFuel) {
        if (!FirePerformance.frontierActive()
                || !FirePerformance.frontierTrunkDescent()
                || !removedFuel.is(BlockTags.LOGS_THAT_BURN)
                || !level.isEmptyBlock(target)
                || !level.getBlockState(target.below()).is(BlockTags.LOGS_THAT_BURN)
                || !mayPlaceDirect(level, source, target, removedFuel)) {
            return;
        }

        BlockState placed = BaseFireBlock.getState(level, target);
        BlockState sourceState = level.getBlockState(source);
        if (placed.is(Blocks.FIRE) && sourceState.hasProperty(FireBlock.AGE)) {
            placed = placed.setValue(FireBlock.AGE, sourceState.getValue(FireBlock.AGE));
        }
        if (level.setBlock(target, placed, 3)) {
            directPlaced(level, source, target);
        }
    }

    /** Flushes the bounded active-origin registry alongside normal level saves. */
    public static void saveLevel(ServerLevel level) {
        LevelState state = LEVELS.get(level);
        if (state != null) {
            state.save(level);
        }
    }

    private static int getIgniteOdds(ServerLevel level, BlockPos target) {
        return getIgnitionInfo(level, target, false) & Integer.MAX_VALUE;
    }

    private static int getIgnitionInfo(ServerLevel level,
                                       BlockPos target,
                                       boolean detectStructuralFuel) {
        if (!level.isEmptyBlock(target)) {
            return 0;
        }
        int maximum = 0;
        boolean structuralFuel = false;
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (Direction direction : DIRECTIONS) {
            neighbour.setWithOffset(target, direction);
            BlockState state = level.getBlockState(neighbour);
            maximum = Math.max(maximum,
                    state.getFireSpreadSpeed(level, neighbour, direction.getOpposite()));
            structuralFuel |= detectStructuralFuel
                    && isStructuralFuel(state)
                    && state.getFireSpreadSpeed(level, neighbour, direction.getOpposite()) > 0;
        }
        return structuralFuel ? maximum | STRUCTURAL_FUEL_FLAG : maximum;
    }

    private static boolean isStructuralFuel(BlockState state) {
        return state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOODEN_STAIRS)
                || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_FENCES)
                || state.is(Tags.Blocks.FENCE_GATES_WOODEN)
                || state.is(BlockTags.WOODEN_DOORS)
                || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(BlockTags.WOOL)
                || state.is(BlockTags.WOOL_CARPETS)
                || state.is(BlockTags.BEDS)
                || state.is(Tags.Blocks.CHESTS_WOODEN);
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
                                   int verticalOffset,
                                   int horizontalOffset,
                                   boolean increasedBurnout) {
        int score = (odds + 40 + level.getDifficulty().getId() * 7) / (age + 30);
        if (increasedBurnout) {
            score /= 2;
        }
        if (score <= 0) {
            return -1;
        }

        int jumpPenalty = horizontalOffset <= 1 ? 1 : horizontalOffset * horizontalOffset;
        int denominator = (100 + Math.max(0, verticalOffset - 1) * 100) * jumpPenalty;
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
        private final Long2LongOpenHashMap pendingOriginByTarget = new Long2LongOpenHashMap();
        private final Long2LongOpenHashMap pendingSourceByTarget = new Long2LongOpenHashMap();
        private final Long2LongOpenHashMap nextSourceScan = new Long2LongOpenHashMap();
        private final Long2LongLinkedOpenHashMap outbreakOriginByFire =
                new Long2LongLinkedOpenHashMap();
        private final Long2LongOpenHashMap outbreakLastSeen = new Long2LongOpenHashMap();
        private final Long2LongLinkedOpenHashMap activeOutbreakLastSeen =
                new Long2LongLinkedOpenHashMap();
        private final Long2ObjectOpenHashMap<LongArrayList> outbreakOriginsByCell =
                new Long2ObjectOpenHashMap<>();
        private final Long2LongOpenHashMap forestClassificationByCell = new Long2LongOpenHashMap();
        private final LongOpenHashSet naturalOutbreaks = new LongOpenHashSet();
        private long budgetTick = Long.MIN_VALUE;
        private int processedThisTick;
        private int sourcesThisTick;
        private int particleArcsThisTick;
        private boolean originsDirty;

        LevelState(ServerLevel level, long now) {
            events = new FrontierEventWheel(now);
            earliestByTarget.defaultReturnValue(Long.MAX_VALUE);
            pendingOriginByTarget.defaultReturnValue(Long.MIN_VALUE);
            pendingSourceByTarget.defaultReturnValue(Long.MIN_VALUE);
            nextSourceScan.defaultReturnValue(Long.MIN_VALUE);
            outbreakOriginByFire.defaultReturnValue(Long.MIN_VALUE);
            outbreakLastSeen.defaultReturnValue(Long.MIN_VALUE);
            activeOutbreakLastSeen.defaultReturnValue(Long.MIN_VALUE);
            forestClassificationByCell.defaultReturnValue(Long.MIN_VALUE);
            loadOrigins(level, now);
        }

        void beginTick(long now) {
            if (budgetTick != now) {
                budgetTick = now;
                processedThisTick = 0;
                sourcesThisTick = 0;
                particleArcsThisTick = 0;
                purgeOutbreakOrigins(now);
            }
        }

        long originFor(ServerLevel level, long fire, long now) {
            if (!FirePerformance.frontierForestRadiusLimit()) {
                return fire;
            }
            long origin;
            if (outbreakOriginByFire.containsKey(fire)) {
                origin = outbreakOriginByFire.getAndMoveToLast(fire);
            } else {
                origin = findOverlappingOrigin(level.getSeed(), fire);
                if (origin == Long.MIN_VALUE) {
                    origin = fire;
                }
            }
            trackOrigin(fire, origin, now);
            return origin;
        }

        void processDue(ServerLevel level, long now) {
            int remaining = FirePerformance.frontierMaxEventsPerTick() - processedThisTick;
            if (remaining <= 0) {
                return;
            }
            processedThisTick += events.drainDue(now, remaining,
                    (due, source, target, age) -> execute(level, due, source, target, age));
        }

        void discover(ServerLevel level,
                      BlockPos sourcePos,
                      BlockState fireState,
                      long origin,
                      long now) {
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
            int jumpDistance = FirePerformance.frontierEmberJumpDistance();
            boolean increasedBurnout = level.getBiome(sourcePos)
                    .is(BiomeTags.INCREASED_FIRE_BURNOUT);
            Boolean forestEnvironment = null;
            for (int x = -jumpDistance; x <= jumpDistance; x++) {
                for (int z = -jumpDistance; z <= jumpDistance; z++) {
                    for (int y = -1; y <= 4; y++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }
                        target.setWithOffset(sourcePos, x, y, z);
                        if (!hasIgnitionNeighborhoodLoaded(level, target)) {
                            continue;
                        }
                        long targetLong = target.asLong();
                        boolean outsideShape = FirePerformance.frontierForestRadiusLimit()
                                && ForestOutbreakPolicy.outsideBoundary(
                                        level.getSeed(),
                                        origin,
                                        BlockPos.getX(origin),
                                        BlockPos.getZ(origin),
                                        target.getX(),
                                        target.getZ(),
                                        FirePerformance.frontierForestMinRadiusBlocks(),
                                        FirePerformance.frontierForestMaxRadiusBlocks());
                        if (outsideShape && forestEnvironment == null) {
                            forestEnvironment = isNaturalOutbreakAt(
                                    level, sourcePos, origin, now);
                        }
                        boolean outsideForestBoundary = outsideShape
                                && Boolean.TRUE.equals(forestEnvironment);
                        int ignitionInfo = getIgnitionInfo(
                                level, target, outsideForestBoundary);
                        int odds = ignitionInfo & Integer.MAX_VALUE;
                        if (odds <= 0) {
                            continue;
                        }
                        if (outsideForestBoundary
                                && (ignitionInfo & STRUCTURAL_FUEL_FLAG) == 0) {
                            continue;
                        }
                        if (!ClaimFireCompatibility.mayIgnite(level, sourcePos, target)) {
                            if (!FirePerformance.frontierActive()) {
                                return; // an adapter failed; the next fire tick resumes VANILLA
                            }
                            continue;
                        }
                        int horizontalOffset = Math.max(Math.abs(x), Math.abs(z));
                        int delay = sampleDelay(
                                level, source, targetLong, age, odds, y, horizontalOffset,
                                increasedBurnout);
                        if (delay > 0) {
                            schedule(source, targetLong, age, origin, now + delay);
                        }
                    }
                }
            }
        }

        void schedule(long source, long target, int sourceAge, long origin, long due) {
            if (events.size() >= FirePerformance.frontierMaxPendingEvents()) {
                return;
            }
            long existing = earliestByTarget.get(target);
            if (due > existing) {
                return;
            }
            if (due == existing) {
                long existingOrigin = pendingOriginByTarget.get(target);
                long existingSource = pendingSourceByTarget.get(target);
                int originOrder = Long.compareUnsigned(origin, existingOrigin);
                if (originOrder > 0
                        || (originOrder == 0
                                && Long.compareUnsigned(source, existingSource) >= 0)) {
                    return;
                }
            }
            earliestByTarget.put(target, due);
            if (FirePerformance.frontierForestRadiusLimit()) {
                pendingOriginByTarget.put(target, origin);
            }
            pendingSourceByTarget.put(target, source);
            events.offer(due, source, target, sourceAge);
        }

        void execute(ServerLevel level, long due, long sourceLong, long targetLong, int sourceAge) {
            if (earliestByTarget.get(targetLong) != due
                    || pendingSourceByTarget.get(targetLong) != sourceLong) {
                return; // stale event superseded by an earlier arrival
            }
            earliestByTarget.remove(targetLong);
            pendingSourceByTarget.remove(targetLong);
            long origin = pendingOriginByTarget.remove(targetLong);
            if (origin == Long.MIN_VALUE) {
                origin = sourceLong;
            }

            BlockPos source = BlockPos.of(sourceLong);
            BlockPos target = BlockPos.of(targetLong);
            if (!level.getChunkSource().hasChunk(source.getX() >> 4, source.getZ() >> 4)
                    || !level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4)
                    || !hasIgnitionNeighborhoodLoaded(level, target)
                    || !level.getBlockState(source).is(Blocks.FIRE)
                    || !level.isEmptyBlock(target)) {
                return;
            }
            boolean outsideForestBoundary = FirePerformance.frontierForestRadiusLimit()
                    && ForestOutbreakPolicy.outsideBoundary(
                            level.getSeed(),
                            origin,
                            BlockPos.getX(origin),
                            BlockPos.getZ(origin),
                            target.getX(),
                            target.getZ(),
                            FirePerformance.frontierForestMinRadiusBlocks(),
                            FirePerformance.frontierForestMaxRadiusBlocks())
                    && isNaturalOutbreakAt(level, source, origin, level.getGameTime());
            int ignitionInfo = getIgnitionInfo(level, target, outsideForestBoundary);
            if (!ClaimFireCompatibility.mayIgnite(level, source, target)
                    || (ignitionInfo & Integer.MAX_VALUE) <= 0
                    || (outsideForestBoundary
                            && (ignitionInfo & STRUCTURAL_FUEL_FLAG) == 0)
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
                trackOrigin(targetLong, origin, level.getGameTime());
                FireHeatManager.observe(level, target, placed);
                if (EmberParticles.isJump(source, target)
                        && FirePerformance.frontierEmberParticles()
                        && particleArcsThisTick < FirePerformance.frontierMaxParticleArcsPerTick()) {
                    particleArcsThisTick++;
                    EmberParticles.sendJump(level, source, target);
                }
            }
        }

        private boolean isForestEnvironment(ServerLevel level, BlockPos center, long now) {
            long cell = BlockPos.asLong(
                    center.getX() >> 2,
                    center.getY() >> 2,
                    center.getZ() >> 2);
            long cached = forestClassificationByCell.get(cell);
            if (cached != Long.MIN_VALUE && (cached >>> 1) >= now) {
                return (cached & 1L) != 0;
            }

            boolean forest = scanForestEnvironment(level, cell);
            forestClassificationByCell.put(
                    cell, ((now + FOREST_CACHE_TICKS) << 1) | (forest ? 1L : 0L));
            if (forestClassificationByCell.size() > 16_384) {
                forestClassificationByCell.clear();
            }
            return forest;
        }

        private boolean allowsOutbreakSpread(ServerLevel level,
                                             BlockPos source,
                                             long target,
                                             BlockState targetFuel,
                                             long origin,
                                             long now) {
            return !ForestOutbreakPolicy.outsideBoundary(
                    level.getSeed(),
                    origin,
                    BlockPos.getX(origin),
                    BlockPos.getZ(origin),
                    BlockPos.getX(target),
                    BlockPos.getZ(target),
                    FirePerformance.frontierForestMinRadiusBlocks(),
                    FirePerformance.frontierForestMaxRadiusBlocks())
                    || isStructuralFuel(targetFuel)
                    || !isNaturalOutbreakAt(level, source, origin, now);
        }

        private boolean isNaturalOutbreakAt(ServerLevel level,
                                            BlockPos source,
                                            long origin,
                                            long now) {
            if (naturalOutbreaks.contains(origin)) {
                return true;
            }
            if (isForestEnvironment(level, source, now)) {
                originsDirty |= naturalOutbreaks.add(origin);
                return true;
            }
            return false;
        }

        private boolean scanForestEnvironment(ServerLevel level, long cell) {
            int centerX = (BlockPos.getX(cell) << 2) + 1;
            int centerY = (BlockPos.getY(cell) << 2) + 1;
            int centerZ = (BlockPos.getZ(cell) << 2) + 1;
            int minX = centerX - 2;
            int maxX = centerX + 2;
            int minZ = centerZ - 2;
            int maxZ = centerZ + 2;
            for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
                for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                    if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                        return true; // unknown space cannot bypass the configured safety rail
                    }
                }
            }

            int leaves = 0;
            int logs = 0;
            int structural = 0;
            BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        sample.set(centerX + x, centerY + y, centerZ + z);
                        BlockState state = level.getBlockState(sample);
                        if (state.is(BlockTags.LEAVES)) {
                            leaves++;
                        }
                        if (state.is(BlockTags.LOGS_THAT_BURN)) {
                            logs++;
                        }
                        if (isStructuralFuel(state)) {
                            structural++;
                        }
                    }
                }
            }
            return ForestOutbreakPolicy.isForestDominant(leaves, logs, structural);
        }

        private void trackOrigin(long fire, long origin, long now) {
            if (!FirePerformance.frontierForestRadiusLimit()) {
                return;
            }
            outbreakOriginByFire.putAndMoveToLast(fire, origin);
            outbreakLastSeen.put(fire, now);
            boolean newOrigin = !activeOutbreakLastSeen.containsKey(origin);
            activeOutbreakLastSeen.putAndMoveToLast(origin, now);
            originsDirty |= newOrigin;
            if (newOrigin) {
                indexOrigin(origin);
            }
            while (activeOutbreakLastSeen.size() > MAX_ACTIVE_OUTBREAKS) {
                long removedOrigin = activeOutbreakLastSeen.firstLongKey();
                activeOutbreakLastSeen.removeFirstLong();
                naturalOutbreaks.remove(removedOrigin);
                unindexOrigin(removedOrigin);
                originsDirty = true;
            }
            int cap = FirePerformance.frontierMaxPendingEvents() * 2;
            while (outbreakOriginByFire.size() > cap) {
                long removed = outbreakOriginByFire.firstLongKey();
                outbreakOriginByFire.removeFirstLong();
                outbreakLastSeen.remove(removed);
            }
        }

        private void purgeOutbreakOrigins(long now) {
            int remaining = ORIGIN_PURGE_BUDGET;
            while (remaining-- > 0 && !outbreakOriginByFire.isEmpty()) {
                long fire = outbreakOriginByFire.firstLongKey();
                long lastSeen = outbreakLastSeen.get(fire);
                if (lastSeen != Long.MIN_VALUE && now - lastSeen <= OUTBREAK_ORIGIN_TTL_TICKS) {
                    break;
                }
                outbreakOriginByFire.removeFirstLong();
                outbreakLastSeen.remove(fire);
            }

        }

        private long findOverlappingOrigin(long worldSeed, long candidateOrigin) {
            long bestOrigin = Long.MIN_VALUE;
            long bestDistanceSquared = Long.MAX_VALUE;
            int candidateX = BlockPos.getX(candidateOrigin);
            int candidateZ = BlockPos.getZ(candidateOrigin);
            int maximumRadius = FirePerformance.frontierForestMaxRadiusBlocks();
            int cellRadius = (int) StrictMath.ceil(
                    maximumRadius * 4.48 / (1 << ORIGIN_INDEX_SHIFT));
            int centerCellX = candidateX >> ORIGIN_INDEX_SHIFT;
            int centerCellZ = candidateZ >> ORIGIN_INDEX_SHIFT;
            for (int cellX = centerCellX - cellRadius;
                    cellX <= centerCellX + cellRadius;
                    cellX++) {
                for (int cellZ = centerCellZ - cellRadius;
                        cellZ <= centerCellZ + cellRadius;
                        cellZ++) {
                    LongArrayList origins = outbreakOriginsByCell.get(packCell(cellX, cellZ));
                    if (origins == null) {
                        continue;
                    }
                    for (int index = 0; index < origins.size(); index++) {
                        long existingOrigin = origins.getLong(index);
                        if (!ForestOutbreakPolicy.boundariesOverlap(
                                worldSeed,
                                existingOrigin,
                                BlockPos.getX(existingOrigin),
                                BlockPos.getZ(existingOrigin),
                                candidateOrigin,
                                candidateX,
                                candidateZ,
                                FirePerformance.frontierForestMinRadiusBlocks(),
                                maximumRadius)) {
                            continue;
                        }
                        long deltaX = (long) BlockPos.getX(existingOrigin) - candidateX;
                        long deltaZ = (long) BlockPos.getZ(existingOrigin) - candidateZ;
                        long distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                        if (distanceSquared < bestDistanceSquared
                                || (distanceSquared == bestDistanceSquared
                                        && Long.compareUnsigned(existingOrigin, bestOrigin) < 0)) {
                            bestOrigin = existingOrigin;
                            bestDistanceSquared = distanceSquared;
                        }
                    }
                }
            }
            return bestOrigin;
        }

        private void indexOrigin(long origin) {
            long cell = packCell(
                    BlockPos.getX(origin) >> ORIGIN_INDEX_SHIFT,
                    BlockPos.getZ(origin) >> ORIGIN_INDEX_SHIFT);
            outbreakOriginsByCell.computeIfAbsent(cell, ignored -> new LongArrayList()).add(origin);
        }

        private void unindexOrigin(long origin) {
            long cell = packCell(
                    BlockPos.getX(origin) >> ORIGIN_INDEX_SHIFT,
                    BlockPos.getZ(origin) >> ORIGIN_INDEX_SHIFT);
            LongArrayList origins = outbreakOriginsByCell.get(cell);
            if (origins == null) {
                return;
            }
            origins.rem(origin);
            if (origins.isEmpty()) {
                outbreakOriginsByCell.remove(cell);
            }
        }

        private void loadOrigins(ServerLevel level, long now) {
            Path path = originFile(level);
            if (!Files.isRegularFile(path)) {
                return;
            }
            try (DataInputStream input = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(path)))) {
                if (input.readInt() != ORIGIN_FILE_MAGIC
                        || input.readInt() != ORIGIN_FILE_VERSION) {
                    throw new IOException("unsupported header");
                }
                int count = input.readInt();
                if (count < 0 || count > MAX_ACTIVE_OUTBREAKS) {
                    throw new IOException("invalid origin count " + count);
                }
                for (int index = 0; index < count; index++) {
                    long origin = input.readLong();
                    activeOutbreakLastSeen.put(origin, now);
                    indexOrigin(origin);
                    if (input.readBoolean()) {
                        naturalOutbreaks.add(origin);
                    }
                }
                LOG.info("[Conflagration] restored {} active forest outbreak origins for {}",
                        count, level.dimension().location());
            } catch (EOFException exception) {
                LOG.warn("[Conflagration] truncated outbreak-origin file {}; ignoring partial data",
                        path, exception);
                activeOutbreakLastSeen.clear();
                naturalOutbreaks.clear();
                outbreakOriginsByCell.clear();
            } catch (IOException | RuntimeException exception) {
                LOG.warn("[Conflagration] could not load outbreak-origin file {}; starting with "
                        + "an empty registry", path, exception);
                activeOutbreakLastSeen.clear();
                naturalOutbreaks.clear();
                outbreakOriginsByCell.clear();
            }
        }

        private void save(ServerLevel level) {
            if (!originsDirty) {
                return;
            }
            Path path = originFile(level);
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try {
                Files.createDirectories(path.getParent());
                try (DataOutputStream output = new DataOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                    output.writeInt(ORIGIN_FILE_MAGIC);
                    output.writeInt(ORIGIN_FILE_VERSION);
                    output.writeInt(activeOutbreakLastSeen.size());
                    for (long origin : activeOutbreakLastSeen.keySet()) {
                        output.writeLong(origin);
                        output.writeBoolean(naturalOutbreaks.contains(origin));
                    }
                }
                try {
                    Files.move(temporary, path,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
                originsDirty = false;
            } catch (IOException | RuntimeException exception) {
                LOG.error("[Conflagration] could not save outbreak origins to {}", path, exception);
            }
        }
    }

    private static Path originFile(ServerLevel level) {
        String dimension = level.dimension().location().toString()
                .replace(':', '_')
                .replace('/', '_');
        return level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("conflagration_outbreaks_" + dimension + ".dat");
    }

    private static long packCell(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    private static boolean hasIgnitionNeighborhoodLoaded(ServerLevel level, BlockPos target) {
        int minimumChunkX = (target.getX() - 1) >> 4;
        int maximumChunkX = (target.getX() + 1) >> 4;
        int minimumChunkZ = (target.getZ() - 1) >> 4;
        int maximumChunkZ = (target.getZ() + 1) >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }
}
