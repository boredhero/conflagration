package dev.boredhero.conflagration.mixin;

import dev.boredhero.conflagration.optimization.CampfireSparks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the existing lit-campfire server ticker; no block-entity or world scan is added. */
@Mixin(CampfireBlockEntity.class)
abstract class CampfireBlockEntityMixin {

    @Inject(method = "cookTick", at = @At("TAIL"))
    private static void conflagration$rareSpark(Level level,
                                                BlockPos position,
                                                BlockState state,
                                                CampfireBlockEntity campfire,
                                                CallbackInfo callback) {
        CampfireSparks.tick(level, position, state);
    }
}
