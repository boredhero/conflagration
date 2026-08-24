package dev.boredhero.conflagration.heat;

import dev.boredhero.conflagration.Conflagration;
import dev.boredhero.conflagration.api.event.ThermalFractureEvent;
import dev.boredhero.conflagration.api.event.ThermalCoolingEvent;
import dev.boredhero.conflagration.api.event.ThermalScorchEvent;
import dev.boredhero.conflagration.api.event.ThermalMeltEvent;
import dev.boredhero.conflagration.config.ConflagrationConfig;
import dev.boredhero.conflagration.optimization.FirePerformance;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
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

/** Sparse, loaded-chunk-only radiant heat index and bounded thermal effects. */
public final class FireHeatManager {

    private static final int CELL_SHIFT = 2;
    private static final int CELL_SIZE = 1 << CELL_SHIFT;
    private static final int CELL_VOLUME = CELL_SIZE * CELL_SIZE * CELL_SIZE;
    private static final int SOURCE_TTL_TICKS = 50;
    private static final int ENTITY_INTERVAL_TICKS = 10;
    private static final int GLASS_QUEUE_REBUILD_TICKS = 200;
    private static final double DOSE_COOLING_HALF_LIFE_SECONDS = 10.0;
    private static final double GLASS_MIN_FLUX_KW_M2 = 2.5;
    private static final int MAGMA_COOLING_CHECKS_PER_TICK = 8;
    private static final int MAX_TRACKED_COOLING_MAGMA = 16_384;
    private static final int MAGMA_FILE_MAGIC = 0x43464D47; // CFMG
    private static final int LEGACY_MAGMA_FILE_VERSION = 1;
    private static final int MAGMA_FILE_VERSION = 2;
    private static final long NO_CELL = Long.MIN_VALUE;

    private static final TagKey<Block> THERMAL_FRACTURABLE = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Conflagration.MOD_ID, "thermal_fracturable"));
    private static final TagKey<Block> THERMAL_FRACTURE_IMMUNE = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Conflagration.MOD_ID, "thermal_fracture_immune"));

    private static final Map<ServerLevel, LevelHeat> LEVELS = new WeakHashMap<>();

    private static volatile boolean enabled;
    private static volatile boolean damageEntities;
    private static volatile double entityHeatingMultiplier;
    private static volatile double radius;
    private static volatile double sourcePowerKw;
    private static volatile double densityPowerMultiplier;
    private static volatile double damageThresholdKwM2;
    private static volatile double damageDose;
    private static volatile boolean shatterGlass;
    private static volatile int glassRadius;
    private static volatile double glassHeatingMultiplier;
    private static volatile double glassBreakDeltaC;
    private static volatile int maxGlassChecksPerTick;
    private static volatile int maxTrackedFires;
    private static volatile boolean scorchGrass;
    private static volatile boolean bakeFarmland;
    private static volatile boolean fuseSand;
    private static volatile boolean fireClay;
    private static volatile int scorchGrassRadius;
    private static volatile double scorchGrassThreshold;
    private static volatile boolean meltSurfaceStone;
    private static volatile int meltSurfaceStoneRadius;
    private static volatile double meltSurfaceStoneThreshold;
    private static volatile boolean coolMagma;
    private static volatile int magmaCoolingDelayTicks;
    private static volatile int magmaCoolingSpreadTicks;
    private static volatile double magmaCoolingThreshold;
    private static volatile boolean smoke;
    private static volatile boolean smokeBlindness;
    private static volatile double smokeThreshold;
    private static volatile double smokeBlindnessThreshold;
    private static volatile int maxSmokeParticles;
    private static volatile Logger runtimeLog;

    private FireHeatManager() {
    }

    public static void refreshFromConfig() {
        enabled = ConflagrationConfig.ENABLED.get() && ConflagrationConfig.HEAT_ENABLED.get();
        damageEntities = ConflagrationConfig.HEAT_ENTITY_DAMAGE.get();
        entityHeatingMultiplier = ConflagrationConfig.HEAT_ENTITY_HEATING_MULTIPLIER.get();
        radius = ConflagrationConfig.HEAT_RADIUS.get();
        sourcePowerKw = ConflagrationConfig.HEAT_SOURCE_POWER.get();
        densityPowerMultiplier = ConflagrationConfig.HEAT_DENSITY_MULTIPLIER.get();
        damageThresholdKwM2 = ConflagrationConfig.HEAT_DAMAGE_THRESHOLD.get();
        damageDose = ConflagrationConfig.HEAT_DAMAGE_DOSE.get();
        shatterGlass = ConflagrationConfig.HEAT_GLASS_SHATTERING.get();
        glassRadius = ConflagrationConfig.HEAT_GLASS_RADIUS.get();
        glassHeatingMultiplier = ConflagrationConfig.HEAT_GLASS_HEATING_MULTIPLIER.get();
        glassBreakDeltaC = ConflagrationConfig.HEAT_GLASS_BREAK_DELTA.get();
        maxGlassChecksPerTick = ConflagrationConfig.HEAT_MAX_GLASS_CHECKS_PER_TICK.get();
        maxTrackedFires = ConflagrationConfig.HEAT_MAX_TRACKED_FIRES.get();
        scorchGrass = ConflagrationConfig.HEAT_SCORCH_GRASS.get();
        bakeFarmland = ConflagrationConfig.HEAT_BAKE_FARMLAND.get();
        fuseSand = ConflagrationConfig.HEAT_FUSE_SAND.get();
        fireClay = ConflagrationConfig.HEAT_FIRE_CLAY.get();
        scorchGrassRadius = ConflagrationConfig.HEAT_SCORCH_GRASS_RADIUS.get();
        scorchGrassThreshold = ConflagrationConfig.HEAT_SCORCH_GRASS_THRESHOLD.get();
        meltSurfaceStone = ConflagrationConfig.HEAT_MELT_SURFACE_STONE.get();
        meltSurfaceStoneRadius = ConflagrationConfig.HEAT_MELT_SURFACE_STONE_RADIUS.get();
        meltSurfaceStoneThreshold = ConflagrationConfig.HEAT_MELT_SURFACE_STONE_THRESHOLD.get();
        coolMagma = ConflagrationConfig.HEAT_COOL_MAGMA.get();
        magmaCoolingDelayTicks = ConflagrationConfig.HEAT_MAGMA_COOLING_DELAY_SECONDS.get() * 20;
        magmaCoolingSpreadTicks = ConflagrationConfig.HEAT_MAGMA_COOLING_SPREAD_SECONDS.get() * 20;
        magmaCoolingThreshold = ConflagrationConfig.HEAT_MAGMA_COOLING_THRESHOLD.get();
        smoke = ConflagrationConfig.HEAT_SMOKE.get();
        smokeBlindness = ConflagrationConfig.HEAT_SMOKE_BLINDNESS.get();
        smokeThreshold = ConflagrationConfig.HEAT_SMOKE_THRESHOLD.get();
        smokeBlindnessThreshold = ConflagrationConfig.HEAT_SMOKE_BLINDNESS_THRESHOLD.get();
        maxSmokeParticles = ConflagrationConfig.HEAT_MAX_SMOKE_PARTICLES.get();
    }

    public static void logStatus(Logger log) {
        runtimeLog = log;
        if (!enabled) {
            log.info("[Conflagration] radiant heat: disabled");
            return;
        }
        double oneBlockFlux = RadiantHeatModel.incidentFlux(sourcePowerKw, 1.0, 1.0, 1.0);
        log.info("[Conflagration] radiant heat: enabled - radius {} blocks, {} kW/fire, "
                        + "{}x entity heating, harm above {} kW/m^2 at dose {} "
                        + "(one fresh fire at 1 block: {} kW/m^2 effective, {} C equivalent)",
                radius, sourcePowerKw, entityHeatingMultiplier, damageThresholdKwM2, damageDose,
                round(oneBlockFlux * entityHeatingMultiplier),
                Math.round(RadiantHeatModel.equivalentRadiantTemperatureC(
                        oneBlockFlux * entityHeatingMultiplier)));
        log.info("[Conflagration] smoke haze: {}{}",
                smoke ? "enabled" : "disabled",
                smoke && smokeBlindness
                        ? " - indoor blindness at exposure " + smokeBlindnessThreshold
                        : "");
        if (!shatterGlass) {
            log.info("[Conflagration] thermal glass fracture: disabled by config");
        } else if (!FirePerformance.directBlockEffectsAllowed()) {
            log.warn("[Conflagration] thermal glass fracture: configured ON but disabled because a "
                    + "detected claim/fire provider has no audited block-destruction adapter");
        } else {
            log.info("[Conflagration] thermal glass fracture: enabled - radius {}, {} C gradient, "
                            + "{}x heating, {} block checks/level/tick",
                    glassRadius, glassBreakDeltaC, glassHeatingMultiplier, maxGlassChecksPerTick);
        }
        boolean lowTierTerrain = scorchGrass || bakeFarmland || fuseSand || fireClay;
        if (lowTierTerrain && !FirePerformance.directBlockEffectsAllowed()) {
            log.warn("[Conflagration] low-tier thermal terrain response: configured ON but disabled by "
                    + "the same unaudited claim/fire provider blocking thermal destruction");
        } else {
            log.info("[Conflagration] low-tier thermal terrain response: {}{}",
                    lowTierTerrain ? "enabled" : "disabled",
                    lowTierTerrain
                            ? (" - radius %d, threshold %s kW/m^2 effective; grass %s, "
                                    + "farmland %s, sand %s, clay %s").formatted(
                                            scorchGrassRadius,
                                            scorchGrassThreshold,
                                            scorchGrass ? "on" : "off",
                                            bakeFarmland ? "on" : "off",
                                            fuseSand ? "on" : "off",
                                            fireClay ? "on" : "off")
                            : "");
        }
        if (meltSurfaceStone && !FirePerformance.directBlockEffectsAllowed()) {
            log.warn("[Conflagration] extreme surface stone melting: configured ON but disabled "
                    + "by the same unaudited claim/fire provider blocking thermal destruction");
        } else {
            log.info("[Conflagration] extreme surface stone melting: {}{}",
                    meltSurfaceStone ? "enabled" : "disabled",
                    meltSurfaceStone
                            ? " - radius " + meltSurfaceStoneRadius + ", threshold "
                                    + meltSurfaceStoneThreshold + " kW/m^2 effective"
                            : "");
        }
        log.info("[Conflagration] generated magma cooling: {}{}",
                coolMagma ? "enabled" : "disabled",
                coolMagma
                        ? " - " + magmaCoolingDelayTicks / 20 + "s base + 0-"
                                + magmaCoolingSpreadTicks / 20 + "s gradual spread, waits above "
                                + magmaCoolingThreshold + " kW/m^2 effective"
                        : "");
    }

    /** Called from FireBlock's scheduled tick after lifecycle/rain/direct-burn processing. */
    public static void observe(ServerLevel level, BlockPos position, BlockState state) {
        if (!enabled || !state.is(BlockTags.FIRE)) {
            return;
        }
        int age = state.hasProperty(FireBlock.AGE) ? state.getValue(FireBlock.AGE) : 0;
        LevelHeat heat = LEVELS.computeIfAbsent(level, LevelHeat::new);
        heat.observe(position, age, level.getGameTime());
    }

    /** Processes bounded glass work and stale-source expiry once per level tick. */
    public static void tickLevel(ServerLevel level) {
        if (!enabled) {
            LEVELS.remove(level);
            return;
        }
        LevelHeat heat = LEVELS.computeIfAbsent(level, LevelHeat::new);
        heat.tick(level, level.getGameTime());
    }

    /** Flushes generated-magma identity and cooling deadlines alongside normal level saves. */
    public static void saveLevel(ServerLevel level) {
        LevelHeat heat = LEVELS.get(level);
        if (heat != null) {
            heat.save(level);
        }
    }

    /** Entity-tick entry point; work is staggered and becomes a no-op when no fire is indexed. */
    public static void tickEntity(Entity entity) {
        if (!enabled || (!damageEntities && !smoke)
                || !(entity instanceof LivingEntity living)
                || !(living.level() instanceof ServerLevel level)
                || !living.isAlive()
                || living.isSpectator()
                || living.fireImmune()) {
            return;
        }
        long now = level.getGameTime();
        if (Math.floorMod(now + living.getId(), ENTITY_INTERVAL_TICKS) != 0) {
            return;
        }
        LevelHeat heat = LEVELS.get(level);
        if (heat != null) {
            heat.expose(level, living, now);
        }
    }

    /** Snapshot used by the read-only /conflagration heat diagnostic command. */
    public static HeatReading reading(Entity entity) {
        if (!enabled || !(entity.level() instanceof ServerLevel level)) {
            return HeatReading.AMBIENT;
        }
        LevelHeat heat = LEVELS.get(level);
        if (heat == null) {
            return HeatReading.AMBIENT;
        }
        return heat.read(level, entity, level.getGameTime());
    }

    private static long cellKey(int x, int y, int z) {
        return BlockPos.asLong(x >> CELL_SHIFT, y >> CELL_SHIFT, z >> CELL_SHIFT);
    }

    private static long cellKeyFromCellCoordinates(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    private static final class LevelHeat {
        private final Long2LongOpenHashMap sources = new Long2LongOpenHashMap();
        private final Long2ObjectOpenHashMap<SourceCell> sourceCells =
                new Long2ObjectOpenHashMap<>();
        private final LongLinkedOpenHashSet glassScanCells = new LongLinkedOpenHashSet();
        private final Long2DoubleOpenHashMap glassGradients = new Long2DoubleOpenHashMap();
        private final Long2LongOpenHashMap glassLastUpdate = new Long2LongOpenHashMap();
        private final LongLinkedOpenHashSet coolingMagmaQueue = new LongLinkedOpenHashSet();
        private final Long2LongOpenHashMap magmaCoolAt = new Long2LongOpenHashMap();
        private final Long2LongOpenHashMap magmaOriginalSource = new Long2LongOpenHashMap();
        private final Int2DoubleOpenHashMap entityDoses = new Int2DoubleOpenHashMap();
        private long currentGlassCell = NO_CELL;
        private int currentGlassOffset;
        private long lastPurgeTick = Long.MIN_VALUE;
        private long lastQueueRebuildTick = Long.MIN_VALUE;
        private boolean sourceCapLogged;
        private boolean magmaDirty;

        LevelHeat(ServerLevel level) {
            sources.defaultReturnValue(Long.MIN_VALUE);
            glassLastUpdate.defaultReturnValue(Long.MIN_VALUE);
            loadMagma(level);
        }

        void observe(BlockPos position, int age, long now) {
            long packedPosition = position.asLong();
            long previous = sources.get(packedPosition);
            if (previous == Long.MIN_VALUE && sources.size() >= maxTrackedFires) {
                if (!sourceCapLogged && runtimeLog != null) {
                    sourceCapLogged = true;
                    runtimeLog.warn("[Conflagration] radiant heat source cap reached ({} per level); "
                            + "additional fires remain ordinary fire until index space expires", maxTrackedFires);
                }
                return;
            }
            sources.put(packedPosition, ((now + SOURCE_TTL_TICKS) << 4) | (age & 15L));
            if (previous != Long.MIN_VALUE) {
                SourceCell cell = sourceCells.get(cellKey(
                        position.getX(), position.getY(), position.getZ()));
                if (cell != null) {
                    cell.ageWeightSum += RadiantHeatModel.ageWeight(age)
                            - RadiantHeatModel.ageWeight((int) (previous & 15L));
                }
                return;
            }
            long key = cellKey(position.getX(), position.getY(), position.getZ());
            SourceCell cell = sourceCells.computeIfAbsent(key, ignored -> new SourceCell());
            boolean wasEmpty = cell.sources.isEmpty();
            cell.sources.add(packedPosition);
            cell.ageWeightSum += RadiantHeatModel.ageWeight(age);
            if (wasEmpty) {
                addGlassScanCellsAround(key);
            }
        }

        void tick(ServerLevel level, long now) {
            if (coolMagma && FirePerformance.directBlockEffectsAllowed()) {
                coolGeneratedMagma(level, now, MAGMA_COOLING_CHECKS_PER_TICK);
            }
            if (lastPurgeTick == Long.MIN_VALUE || now - lastPurgeTick >= 20) {
                purgeExpired(now);
                lastPurgeTick = now;
            }
            if (sources.isEmpty()) {
                glassScanCells.clear();
                glassGradients.clear();
                glassLastUpdate.clear();
                entityDoses.clear();
                currentGlassCell = NO_CELL;
                return;
            }
            if (lastQueueRebuildTick == Long.MIN_VALUE
                    || now - lastQueueRebuildTick >= GLASS_QUEUE_REBUILD_TICKS) {
                rebuildGlassQueue();
                lastQueueRebuildTick = now;
            }
            if ((shatterGlass || scorchGrass || bakeFarmland || fuseSand || fireClay
                    || meltSurfaceStone)
                    && FirePerformance.directBlockEffectsAllowed()
                    && maxGlassChecksPerTick > 0) {
                scanGlass(level, now, maxGlassChecksPerTick);
            }
        }

        void expose(ServerLevel level, LivingEntity living, long now) {
            int entityId = living.getId();
            double priorDose = entityDoses.get(entityId);
            double elapsedSeconds = ENTITY_INTERVAL_TICKS / 20.0;
            Vec3 samplePoint = living.getBoundingBox().getCenter();
            HeatSample sample = sampleAt(samplePoint.x, samplePoint.y, samplePoint.z, radius, now);
            exposeSmoke(level, living, sample.fluxKwM2() * entityHeatingMultiplier, now);

            if (!damageEntities) {
                return;
            }
            if (living.isInWaterOrRain() || living.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                storeCooledDose(entityId, priorDose, elapsedSeconds);
                return;
            }

            double flux = sample.fluxKwM2() * entityHeatingMultiplier;
            if (flux >= damageThresholdKwM2 && sample.source() != Long.MIN_VALUE) {
                BlockPos source = BlockPos.of(sample.source());
                if (!level.getChunkSource().hasChunk(source.getX() >> 4, source.getZ() >> 4)
                        || !level.getBlockState(source).is(BlockTags.FIRE)
                        || isOccluded(level, living, samplePoint, source)) {
                    flux *= 0.1;
                }
            }

            double dose = priorDose + RadiantHeatModel.doseIncrement(
                    flux, damageThresholdKwM2, elapsedSeconds);
            if (flux < 1.7) {
                dose = RadiantHeatModel.coolDose(
                        dose, elapsedSeconds, DOSE_COOLING_HALF_LIFE_SECONDS);
            }
            if (dose >= damageDose) {
                living.igniteForTicks(40);
                dose %= damageDose;
            }
            if (dose < 0.001) {
                entityDoses.remove(entityId);
            } else {
                entityDoses.put(entityId, dose);
            }
        }

        private void exposeSmoke(ServerLevel level,
                                 LivingEntity living,
                                 double effectiveFlux,
                                 long now) {
            if (!smoke || !(living instanceof ServerPlayer player)
                    || Math.floorMod(now + player.getId(), 20) != 0) {
                return;
            }
            BlockPos eyes = BlockPos.containing(player.getEyePosition());
            double roofCoverage = roofCoverage(level, eyes);
            boolean indoors = roofCoverage > 0.0;
            double dispersion = indoors
                    ? 1.0 + 1.5 * roofCoverage * roofCoverage
                    : 0.35;
            double exposure = effectiveFlux * dispersion;
            if (exposure < smokeThreshold) {
                return;
            }

            int particles = Math.min(maxSmokeParticles,
                    Math.max(1, (int) Math.ceil((exposure - smokeThreshold) * 1.5)));
            if (particles > 0) {
                Vec3 eye = player.getEyePosition();
                level.sendParticles(
                        ParticleTypes.LARGE_SMOKE,
                        eye.x,
                        eye.y + 0.2,
                        eye.z,
                        particles,
                        1.2,
                        0.45,
                        1.2,
                        0.015);
            }
            if (indoors && smokeBlindness && exposure >= smokeBlindnessThreshold) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.BLINDNESS, 40, 0, true, false, false));
            }
        }

        /**
         * Coarse enclosure test using cached motion-blocking heightmaps. Unlike sky light, this
         * treats transparent glass roofs as roofs and never raycasts, flood-fills, or loads chunks.
         */
        private double roofCoverage(ServerLevel level, BlockPos eyes) {
            int covered = 0;
            boolean centerCovered = false;
            for (int deltaX = -2; deltaX <= 2; deltaX += 2) {
                for (int deltaZ = -2; deltaZ <= 2; deltaZ += 2) {
                    int x = eyes.getX() + deltaX;
                    int z = eyes.getZ() + deltaZ;
                    if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                        return 0.0;
                    }
                    boolean sampleCovered = level.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) > eyes.getY() + 1;
                    if (sampleCovered) {
                        covered++;
                        centerCovered |= deltaX == 0 && deltaZ == 0;
                    }
                }
            }
            return centerCovered && covered >= 5 ? covered / 9.0 : 0.0;
        }

        HeatReading read(ServerLevel level, Entity entity, long now) {
            Vec3 point = entity.getBoundingBox().getCenter();
            HeatSample sample = sampleAt(point.x, point.y, point.z, radius, now);
            double flux = sample.fluxKwM2() * entityHeatingMultiplier;
            boolean occluded = false;
            if (sample.source() != Long.MIN_VALUE && entity instanceof LivingEntity living) {
                BlockPos source = BlockPos.of(sample.source());
                occluded = isOccluded(level, living, point, source);
                if (occluded) {
                    flux *= 0.1;
                }
            }
            return new HeatReading(
                    flux,
                    RadiantHeatModel.equivalentRadiantTemperatureC(flux),
                    sample.sourceCount(),
                    entityDoses.get(entity.getId()),
                    occluded);
        }

        private void storeCooledDose(int entityId, double priorDose, double elapsedSeconds) {
            double cooled = RadiantHeatModel.coolDose(
                    priorDose, elapsedSeconds, DOSE_COOLING_HALF_LIFE_SECONDS);
            if (cooled < 0.001) {
                entityDoses.remove(entityId);
            } else {
                entityDoses.put(entityId, cooled);
            }
        }

        private HeatSample sampleAt(double x, double y, double z, double queryRadius, long now) {
            int minCellX = ((int) Math.floor(x - queryRadius)) >> CELL_SHIFT;
            int maxCellX = ((int) Math.floor(x + queryRadius)) >> CELL_SHIFT;
            int minCellY = ((int) Math.floor(y - queryRadius)) >> CELL_SHIFT;
            int maxCellY = ((int) Math.floor(y + queryRadius)) >> CELL_SHIFT;
            int minCellZ = ((int) Math.floor(z - queryRadius)) >> CELL_SHIFT;
            int maxCellZ = ((int) Math.floor(z + queryRadius)) >> CELL_SHIFT;
            double radiusSquared = queryRadius * queryRadius;
            double flux = 0.0;
            double strongestContribution = 0.0;
            long strongestSource = Long.MIN_VALUE;
            int sourceCount = 0;

            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
                    for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                        SourceCell cell = sourceCells.get(
                                cellKeyFromCellCoordinates(cellX, cellY, cellZ));
                        if (cell == null || cell.sources.isEmpty()) {
                            continue;
                        }
                        double density = RadiantHeatModel.densityMultiplier(
                                cell.sources.size(), densityPowerMultiplier);
                        double centerX = (cellX << CELL_SHIFT) + CELL_SIZE * 0.5;
                        double centerY = (cellY << CELL_SHIFT) + CELL_SIZE * 0.5;
                        double centerZ = (cellZ << CELL_SHIFT) + CELL_SIZE * 0.5;
                        double centerDeltaX = centerX - x;
                        double centerDeltaY = centerY - y;
                        double centerDeltaZ = centerZ - z;
                        double centerDistanceSquared = centerDeltaX * centerDeltaX
                                + centerDeltaY * centerDeltaY + centerDeltaZ * centerDeltaZ;
                        if (queryRadius > 6.0 && centerDistanceSquared > 16.0) {
                            if (centerDistanceSquared > radiusSquared) {
                                continue;
                            }
                            double contribution = RadiantHeatModel.incidentFlux(
                                    sourcePowerKw, cell.ageWeightSum, density, centerDistanceSquared);
                            flux += contribution;
                            sourceCount += cell.sources.size();
                            if (contribution > strongestContribution) {
                                strongestContribution = contribution;
                                strongestSource = cell.sources.iterator().nextLong();
                            }
                            continue;
                        }
                        for (long source : cell.sources) {
                            long metadata = sources.get(source);
                            if (metadata == Long.MIN_VALUE || (metadata >>> 4) < now) {
                                continue;
                            }
                            double deltaX = BlockPos.getX(source) + 0.5 - x;
                            double deltaY = BlockPos.getY(source) + 0.5 - y;
                            double deltaZ = BlockPos.getZ(source) + 0.5 - z;
                            double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                            if (distanceSquared > radiusSquared) {
                                continue;
                            }
                            int age = (int) (metadata & 15L);
                            double contribution = RadiantHeatModel.incidentFlux(
                                    sourcePowerKw,
                                    RadiantHeatModel.ageWeight(age),
                                    density,
                                    distanceSquared);
                            flux += contribution;
                            sourceCount++;
                            if (contribution > strongestContribution) {
                                strongestContribution = contribution;
                                strongestSource = source;
                            }
                        }
                    }
                }
            }
            return new HeatSample(flux, strongestSource, sourceCount);
        }

        private boolean isOccluded(ServerLevel level,
                                   LivingEntity living,
                                   Vec3 samplePoint,
                                   BlockPos source) {
            HitResult result = level.clip(new ClipContext(
                    samplePoint,
                    Vec3.atCenterOf(source),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    living));
            return result.getType() == HitResult.Type.BLOCK;
        }

        private void scanGlass(ServerLevel level, long now, int budget) {
            int checked = 0;
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            while (checked < budget && (!glassScanCells.isEmpty() || currentGlassCell != NO_CELL)) {
                if (currentGlassCell == NO_CELL) {
                    currentGlassCell = glassScanCells.removeFirstLong();
                    currentGlassOffset = 0;
                    int baseX = BlockPos.getX(currentGlassCell) << CELL_SHIFT;
                    int baseZ = BlockPos.getZ(currentGlassCell) << CELL_SHIFT;
                    if (!level.getChunkSource().hasChunk(baseX >> 4, baseZ >> 4)) {
                        currentGlassCell = NO_CELL;
                        continue;
                    }
                }

                // A full-period permutation scatters consecutive checks across the 4x4x4 cell,
                // avoiding visibly painting one rectangular strip of terrain at a time.
                int local = (currentGlassOffset++ * 37) & (CELL_VOLUME - 1);
                int baseX = BlockPos.getX(currentGlassCell) << CELL_SHIFT;
                int baseY = BlockPos.getY(currentGlassCell) << CELL_SHIFT;
                int baseZ = BlockPos.getZ(currentGlassCell) << CELL_SHIFT;
                int localX = local & (CELL_SIZE - 1);
                int localZ = (local >> CELL_SHIFT) & (CELL_SIZE - 1);
                int localY = local >> (CELL_SHIFT * 2);
                int targetY = baseY + localY;
                checked++;

                if (!level.isOutsideBuildHeight(targetY)) {
                    position.set(baseX + localX, targetY, baseZ + localZ);
                    updateGlass(level, position, now);
                }

                if (currentGlassOffset == CELL_VOLUME) {
                    glassScanCells.add(currentGlassCell);
                    currentGlassCell = NO_CELL;
                }
            }
        }

        private void updateGlass(ServerLevel level, BlockPos.MutableBlockPos position, long now) {
            BlockState state = level.getBlockState(position);
            long packedPosition = position.asLong();
            BlockState terrainReplacement = lowTierTerrainReplacement(state);
            if (terrainReplacement != null) {
                tryTransformLowTierTerrain(
                        level, position, state, terrainReplacement, now);
                glassGradients.remove(packedPosition);
                glassLastUpdate.remove(packedPosition);
                return;
            }
            if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)) {
                tryMeltSurfaceStone(level, position, state, now);
                glassGradients.remove(packedPosition);
                glassLastUpdate.remove(packedPosition);
                return;
            }
            if (!state.is(THERMAL_FRACTURABLE)
                    || state.is(THERMAL_FRACTURE_IMMUNE)
                    || state.hasBlockEntity()) {
                glassGradients.remove(packedPosition);
                glassLastUpdate.remove(packedPosition);
                return;
            }

            HeatSample sample = sampleAt(
                    position.getX() + 0.5,
                    position.getY() + 0.5,
                    position.getZ() + 0.5,
                    glassRadius,
                    now);
            long previousTick = glassLastUpdate.put(packedPosition, now);
            double elapsedSeconds = previousTick == Long.MIN_VALUE
                    ? 0.0
                    : Math.min(10.0, (now - previousTick) / 20.0);
            double gradient = RadiantHeatModel.updateGlassGradient(
                    glassGradients.get(packedPosition),
                    sample.fluxKwM2() * glassHeatingMultiplier,
                    elapsedSeconds);

            if (gradient < 0.01
                    && sample.fluxKwM2() * glassHeatingMultiplier < GLASS_MIN_FLUX_KW_M2) {
                glassGradients.remove(packedPosition);
                glassLastUpdate.remove(packedPosition);
                return;
            }
            glassGradients.put(packedPosition, gradient);
            if (gradient < glassBreakDeltaC || sample.source() == Long.MIN_VALUE) {
                return;
            }

            BlockPos source = BlockPos.of(sample.source());
            if (!level.getChunkSource().hasChunk(source.getX() >> 4, source.getZ() >> 4)
                    || !level.getBlockState(source).is(BlockTags.FIRE)
                    || !FirePerformance.mayShatterGlass(level, source, position)) {
                return;
            }
            ThermalFractureEvent event = new ThermalFractureEvent(
                    level, source, position, state, sample.fluxKwM2(), gradient);
            NeoForge.EVENT_BUS.post(event);
            if (!event.isCanceled() && level.destroyBlock(position, false)) {
                glassGradients.remove(packedPosition);
                glassLastUpdate.remove(packedPosition);
            }
        }

        private void tryTransformLowTierTerrain(ServerLevel level,
                                                BlockPos.MutableBlockPos position,
                                                BlockState state,
                                                BlockState replacement,
                                                long now) {
            HeatSample sample = sampleAt(
                    position.getX() + 0.5,
                    position.getY() + 0.5,
                    position.getZ() + 0.5,
                    scorchGrassRadius,
                    now);
            double effectiveFlux = sample.fluxKwM2() * entityHeatingMultiplier;
            if (effectiveFlux < scorchGrassThreshold || sample.source() == Long.MIN_VALUE) {
                return;
            }
            BlockPos source = BlockPos.of(sample.source());
            if (!level.getChunkSource().hasChunk(source.getX() >> 4, source.getZ() >> 4)
                    || !level.getBlockState(source).is(BlockTags.FIRE)
                    || isTerrainOccluded(level, position, source)
                    || !FirePerformance.mayApplyThermalBlockEffect(level, source, position)) {
                return;
            }
            ThermalScorchEvent event = new ThermalScorchEvent(
                    level, source, position, state, effectiveFlux);
            NeoForge.EVENT_BUS.post(event);
            if (!event.isCanceled()) {
                level.setBlock(position, replacement, 3);
            }
        }

        private BlockState lowTierTerrainReplacement(BlockState state) {
            if (scorchGrass && state.is(Blocks.GRASS_BLOCK)) {
                return Blocks.DIRT.defaultBlockState();
            }
            if (bakeFarmland && state.is(Blocks.FARMLAND)) {
                return Blocks.DIRT.defaultBlockState();
            }
            if (fuseSand && state.is(Blocks.SAND)) {
                return Blocks.SANDSTONE.defaultBlockState();
            }
            if (fuseSand && state.is(Blocks.RED_SAND)) {
                return Blocks.RED_SANDSTONE.defaultBlockState();
            }
            if (fireClay && state.is(Blocks.CLAY)) {
                return Blocks.TERRACOTTA.defaultBlockState();
            }
            return null;
        }

        private boolean isTerrainOccluded(ServerLevel level, BlockPos target, BlockPos source) {
            Vec3 targetCenter = Vec3.atCenterOf(target);
            Vec3 sourceCenter = Vec3.atCenterOf(source);
            Vec3 delta = sourceCenter.subtract(targetCenter);
            double largestAxis = Math.max(
                    Math.max(Math.abs(delta.x), Math.abs(delta.y)), Math.abs(delta.z));
            if (largestAxis < 1.0E-6) {
                return false;
            }
            // Start just beyond the target cube's face. Starting at its center would report the
            // terrain block itself as the obstruction.
            Vec3 start = targetCenter.add(delta.scale(0.501 / largestAxis));
            HitResult result = level.clip(new ClipContext(
                    start,
                    sourceCenter,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            return result.getType() == HitResult.Type.BLOCK;
        }

        private void tryMeltSurfaceStone(ServerLevel level,
                                         BlockPos.MutableBlockPos position,
                                         BlockState state,
                                         long now) {
            if (!meltSurfaceStone) {
                return;
            }
            BlockState above = level.getBlockState(position.above());
            if (!above.isAir() && !above.is(BlockTags.FIRE)) {
                return;
            }
            HeatSample sample = sampleAt(
                    position.getX() + 0.5,
                    position.getY() + 0.5,
                    position.getZ() + 0.5,
                    meltSurfaceStoneRadius,
                    now);
            double effectiveFlux = sample.fluxKwM2() * entityHeatingMultiplier;
            if (effectiveFlux < meltSurfaceStoneThreshold || sample.source() == Long.MIN_VALUE) {
                return;
            }
            BlockPos source = BlockPos.of(sample.source());
            if (!level.getChunkSource().hasChunk(source.getX() >> 4, source.getZ() >> 4)
                    || !level.getBlockState(source).is(BlockTags.FIRE)
                    || isTerrainOccluded(level, position, source)
                    || !FirePerformance.mayApplyThermalBlockEffect(level, source, position)) {
                return;
            }
            ThermalMeltEvent event = new ThermalMeltEvent(
                    level, source, position, state, effectiveFlux);
            NeoForge.EVENT_BUS.post(event);
            if (!event.isCanceled()) {
                if (level.setBlock(position, Blocks.MAGMA_BLOCK.defaultBlockState(), 3)) {
                    trackGeneratedMagma(position.asLong(), sample.source(), now);
                }
            }
        }

        private void trackGeneratedMagma(long position, long source, long now) {
            long jitter = magmaCoolingSpreadTicks == 0
                    ? 0
                    : Long.remainderUnsigned(mix64(position ^ source), magmaCoolingSpreadTicks + 1L);
            magmaCoolAt.put(position, now + magmaCoolingDelayTicks + jitter);
            magmaOriginalSource.put(position, source);
            coolingMagmaQueue.addAndMoveToLast(position);
            magmaDirty = true;
            while (coolingMagmaQueue.size() > MAX_TRACKED_COOLING_MAGMA) {
                long removed = coolingMagmaQueue.removeFirstLong();
                magmaCoolAt.remove(removed);
                magmaOriginalSource.remove(removed);
            }
        }

        private void coolGeneratedMagma(ServerLevel level, long now, int budget) {
            BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
            for (int checked = 0; checked < budget && !coolingMagmaQueue.isEmpty(); checked++) {
                long packedTarget = coolingMagmaQueue.removeFirstLong();
                target.set(packedTarget);
                if (!level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4)) {
                    coolingMagmaQueue.add(packedTarget);
                    continue;
                }
                if (!level.getBlockState(target).is(Blocks.MAGMA_BLOCK)) {
                    magmaCoolAt.remove(packedTarget);
                    magmaOriginalSource.remove(packedTarget);
                    magmaDirty = true;
                    continue;
                }
                if (now < magmaCoolAt.get(packedTarget)) {
                    coolingMagmaQueue.add(packedTarget);
                    continue;
                }

                HeatSample sample = sampleAt(
                        target.getX() + 0.5,
                        target.getY() + 0.5,
                        target.getZ() + 0.5,
                        meltSurfaceStoneRadius,
                        now);
                if (sample.fluxKwM2() * entityHeatingMultiplier > magmaCoolingThreshold) {
                    magmaCoolAt.put(packedTarget, now + 200);
                    magmaDirty = true;
                    coolingMagmaQueue.add(packedTarget);
                    continue;
                }

                long packedSource = magmaOriginalSource.get(packedTarget);
                BlockPos source = BlockPos.of(packedSource);
                if (!level.getChunkSource().hasChunk(source.getX() >> 4, source.getZ() >> 4)
                        || !FirePerformance.mayApplyThermalBlockEffect(level, source, target)) {
                    magmaCoolAt.put(packedTarget, now + 200);
                    magmaDirty = true;
                    coolingMagmaQueue.add(packedTarget);
                    continue;
                }
                ThermalCoolingEvent event = new ThermalCoolingEvent(level, source, target);
                NeoForge.EVENT_BUS.post(event);
                if (event.isCanceled()
                        || !level.setBlock(target, Blocks.STONE.defaultBlockState(), 3)) {
                    magmaCoolAt.put(packedTarget, now + 200);
                    magmaDirty = true;
                    coolingMagmaQueue.add(packedTarget);
                    continue;
                }
                BlockPos above = target.above();
                if (level.getBlockState(above).is(BlockTags.FIRE)) {
                    level.removeBlock(above, false);
                }
                magmaCoolAt.remove(packedTarget);
                magmaOriginalSource.remove(packedTarget);
                magmaDirty = true;
            }
        }

        private void loadMagma(ServerLevel level) {
            Path path = magmaFile(level);
            if (!Files.isRegularFile(path)) {
                return;
            }
            try (DataInputStream input = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(path)))) {
                if (input.readInt() != MAGMA_FILE_MAGIC) {
                    throw new IOException("unsupported header");
                }
                int version = input.readInt();
                long currentGameTime = level.getGameTime();
                long currentWallTime = System.currentTimeMillis();
                long savedGameTime;
                long savedWallTime;
                if (version == MAGMA_FILE_VERSION) {
                    savedGameTime = input.readLong();
                    savedWallTime = input.readLong();
                } else if (version == LEGACY_MAGMA_FILE_VERSION) {
                    // Version 1 did not retain enough timing information to distinguish online
                    // from offline time. Count its file age once during migration; the live heat
                    // check below still prevents actively heated magma from cooling early.
                    savedGameTime = currentGameTime;
                    savedWallTime = Files.getLastModifiedTime(path).toMillis();
                } else {
                    throw new IOException("unsupported magma file version " + version);
                }
                long offlineTicks = MagmaCoolingClock.offlineTicks(
                        savedGameTime, savedWallTime, currentGameTime, currentWallTime);
                int count = input.readInt();
                if (count < 0 || count > MAX_TRACKED_COOLING_MAGMA) {
                    throw new IOException("invalid magma count " + count);
                }
                for (int index = 0; index < count; index++) {
                    long target = input.readLong();
                    coolingMagmaQueue.add(target);
                    magmaCoolAt.put(target, MagmaCoolingClock.adjustedDueTick(
                            input.readLong(), offlineTicks, currentGameTime));
                    magmaOriginalSource.put(target, input.readLong());
                }
                magmaDirty = version != MAGMA_FILE_VERSION || offlineTicks > 0;
                if (count > 0 && runtimeLog != null) {
                    runtimeLog.info("[Conflagration] restored {} cooling magma blocks for {}{}",
                            count,
                            level.dimension().location(),
                            offlineTicks > 0
                                    ? " (caught up " + offlineTicks / 20
                                            + "s of offline cooling)"
                                    : "");
                }
            } catch (EOFException exception) {
                if (runtimeLog != null) {
                    runtimeLog.warn("[Conflagration] truncated magma-cooling file {}; ignoring "
                            + "partial data", path, exception);
                }
                clearMagmaTracking();
            } catch (IOException | RuntimeException exception) {
                if (runtimeLog != null) {
                    runtimeLog.warn("[Conflagration] could not load magma-cooling file {}; "
                            + "natural magma remains untouched", path, exception);
                }
                clearMagmaTracking();
            }
        }

        private void save(ServerLevel level) {
            if (!magmaDirty) {
                return;
            }
            Path path = magmaFile(level);
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try {
                Files.createDirectories(path.getParent());
                try (DataOutputStream output = new DataOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                    output.writeInt(MAGMA_FILE_MAGIC);
                    output.writeInt(MAGMA_FILE_VERSION);
                    output.writeLong(level.getGameTime());
                    output.writeLong(System.currentTimeMillis());
                    output.writeInt(coolingMagmaQueue.size());
                    for (long target : coolingMagmaQueue) {
                        output.writeLong(target);
                        output.writeLong(magmaCoolAt.get(target));
                        output.writeLong(magmaOriginalSource.get(target));
                    }
                }
                try {
                    Files.move(temporary, path,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
                magmaDirty = false;
            } catch (IOException | RuntimeException exception) {
                if (runtimeLog != null) {
                    runtimeLog.error("[Conflagration] could not save magma cooling to {}",
                            path, exception);
                }
            }
        }

        private void clearMagmaTracking() {
            coolingMagmaQueue.clear();
            magmaCoolAt.clear();
            magmaOriginalSource.clear();
        }

        private void purgeExpired(long now) {
            var iterator = sources.long2LongEntrySet().fastIterator();
            while (iterator.hasNext()) {
                Long2LongMap.Entry entry = iterator.next();
                if ((entry.getLongValue() >>> 4) >= now) {
                    continue;
                }
                long source = entry.getLongKey();
                int age = (int) (entry.getLongValue() & 15L);
                iterator.remove();
                long key = cellKey(BlockPos.getX(source), BlockPos.getY(source), BlockPos.getZ(source));
                SourceCell cell = sourceCells.get(key);
                if (cell != null) {
                    cell.sources.remove(source);
                    cell.ageWeightSum -= RadiantHeatModel.ageWeight(age);
                    if (cell.sources.isEmpty()) {
                        sourceCells.remove(key);
                    }
                }
            }
            if (sources.size() < maxTrackedFires) {
                sourceCapLogged = false;
            }
        }

        private void rebuildGlassQueue() {
            glassScanCells.clear();
            currentGlassCell = NO_CELL;
            for (long sourceCell : sourceCells.keySet()) {
                addGlassScanCellsAround(sourceCell);
            }
        }

        private void addGlassScanCellsAround(long sourceCell) {
            int scanRadius = Math.max(
                    Math.max(shatterGlass ? glassRadius : 0,
                        (scorchGrass || bakeFarmland || fuseSand || fireClay)
                                ? scorchGrassRadius : 0),
                    meltSurfaceStone ? meltSurfaceStoneRadius : 0);
            int cellRadius = (scanRadius + CELL_SIZE - 1) >> CELL_SHIFT;
            int sourceX = BlockPos.getX(sourceCell);
            int sourceY = BlockPos.getY(sourceCell);
            int sourceZ = BlockPos.getZ(sourceCell);
            for (int x = -cellRadius; x <= cellRadius; x++) {
                for (int y = -cellRadius; y <= cellRadius; y++) {
                    for (int z = -cellRadius; z <= cellRadius; z++) {
                        glassScanCells.add(cellKeyFromCellCoordinates(
                                sourceX + x, sourceY + y, sourceZ + z));
                    }
                }
            }
        }
    }

    private record HeatSample(double fluxKwM2, long source, int sourceCount) {
    }

    public record HeatReading(double fluxKwM2,
                              double equivalentTemperatureC,
                              int nearbyFires,
                              double accumulatedDose,
                              boolean occluded) {
        private static final HeatReading AMBIENT = new HeatReading(0.0, 20.0, 0, 0.0, false);
    }

    private static final class SourceCell {
        private final LongOpenHashSet sources = new LongOpenHashSet();
        private double ageWeightSum;
    }

    private static Path magmaFile(ServerLevel level) {
        String dimension = level.dimension().location().toString()
                .replace(':', '_')
                .replace('/', '_');
        return level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("conflagration_cooling_magma_" + dimension + ".dat");
    }
}
