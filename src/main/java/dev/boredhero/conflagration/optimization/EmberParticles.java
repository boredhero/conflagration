package dev.boredhero.conflagration.optimization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/** Server-driven jump visuals using only particle types built into vanilla clients. */
final class EmberParticles {

    static final int POINT_COUNT = 5;
    private static final double ARC_HEIGHT = 1.15;

    private EmberParticles() {
    }

    static boolean isJump(BlockPos source, BlockPos target) {
        return EmberArc.isJump(target.getX() - source.getX(), target.getZ() - source.getZ());
    }

    static void sendJump(ServerLevel level, BlockPos source, BlockPos target) {
        double startX = source.getX() + 0.5;
        double startY = source.getY() + 0.65;
        double startZ = source.getZ() + 0.5;
        double deltaX = target.getX() - source.getX();
        double deltaY = target.getY() - source.getY();
        double deltaZ = target.getZ() - source.getZ();

        for (int point = 1; point <= POINT_COUNT; point++) {
            double time = point / (POINT_COUNT + 1.0);
            double arc = ARC_HEIGHT * 4.0 * time * (1.0 - time);
            level.sendParticles(
                    ParticleTypes.SMALL_FLAME,
                    startX + deltaX * time,
                    startY + deltaY * time + arc,
                    startZ + deltaZ * time,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0);
        }
    }
}
