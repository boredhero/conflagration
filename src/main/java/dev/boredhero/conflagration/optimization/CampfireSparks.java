package dev.boredhero.conflagration.optimization;

import dev.boredhero.conflagration.Conflagration;
import dev.boredhero.conflagration.config.ConflagrationConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Extremely rare, loaded-chunk-only campfire ember ignition. */
public final class CampfireSparks {

    private static final int MINUTE_TICKS = 20 * 60;
    private static final int LANDING_ATTEMPTS = 16;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final ResourceLocation ADVANCEMENT = ResourceLocation.fromNamespaceAndPath(
            Conflagration.MOD_ID, "only_you");

    private CampfireSparks() {
    }

    public static void tick(Level level, BlockPos campfire, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)
                || !ConflagrationConfig.ENABLED.get()
                || !ConflagrationConfig.CAMPFIRE_SPARKS.get()
                || !FirePerformance.directIgnitionAllowed()
                || !state.hasProperty(CampfireBlock.LIT)
                || !state.getValue(CampfireBlock.LIT)) {
            return;
        }

        long now = serverLevel.getGameTime();
        if (Math.floorMod(now + mix64(campfire.asLong()), MINUTE_TICKS) != 0
                || serverLevel.random.nextDouble()
                        >= ConflagrationConfig.CAMPFIRE_SPARK_CHANCE_PER_MINUTE.get()) {
            return;
        }

        int radius = ConflagrationConfig.CAMPFIRE_SPARK_RADIUS.get();
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < LANDING_ATTEMPTS; attempt++) {
            int x = serverLevel.random.nextInt(radius * 2 + 1) - radius;
            int y = serverLevel.random.nextInt(6) - 2;
            int z = serverLevel.random.nextInt(radius * 2 + 1) - radius;
            if (x * x + z * z > radius * radius) {
                continue;
            }
            target.setWithOffset(campfire, x, y, z);
            if (!serverLevel.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4)
                    || !serverLevel.isEmptyBlock(target)
                    || !hasNaturalOrWoodFuel(serverLevel, target)
                    || !ClaimFireCompatibility.mayIgnite(serverLevel, campfire, target)) {
                continue;
            }

            BlockState fire = BaseFireBlock.getState(serverLevel, target);
            if (serverLevel.setBlock(target, fire, 3)) {
                EmberParticles.sendJump(serverLevel, campfire, target);
                awardNearbyPlayer(serverLevel, campfire);
                return;
            }
        }
    }

    private static boolean hasNaturalOrWoodFuel(ServerLevel level, BlockPos target) {
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbour = target.relative(direction);
            BlockState state = level.getBlockState(neighbour);
            if (state.is(BlockTags.LEAVES)
                    || state.is(BlockTags.LOGS_THAT_BURN)
                    || state.is(BlockTags.PLANKS)
                    || state.is(BlockTags.WOODEN_STAIRS)
                    || state.is(BlockTags.WOODEN_SLABS)
                    || state.is(BlockTags.WOODEN_FENCES)
                    || state.is(BlockTags.WOODEN_DOORS)
                    || state.is(BlockTags.WOODEN_TRAPDOORS)) {
                return true;
            }
        }
        return false;
    }

    private static void awardNearbyPlayer(ServerLevel level, BlockPos campfire) {
        Player nearest = level.getNearestPlayer(
                campfire.getX() + 0.5,
                campfire.getY() + 0.5,
                campfire.getZ() + 0.5,
                16.0,
                false);
        if (!(nearest instanceof ServerPlayer player)) {
            return;
        }
        AdvancementHolder advancement = level.getServer().getAdvancements().get(ADVANCEMENT);
        if (advancement != null) {
            player.getAdvancements().award(advancement, "spark");
        }
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
}
