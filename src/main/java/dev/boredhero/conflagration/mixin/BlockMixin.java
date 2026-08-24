package dev.boredhero.conflagration.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.boredhero.conflagration.optimization.FireDropSuppression;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Prevents item drops from attachment/door cleanup synchronously caused by fire burnout. */
@Mixin(Block.class)
abstract class BlockMixin {

    @WrapOperation(
            method = "updateOrDestroy(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z"))
    private static boolean conflagration$suppressFireCascadeDrops(LevelAccessor level,
                                                                  BlockPos position,
                                                                  boolean drop,
                                                                  Entity breaker,
                                                                  int recursionLeft,
                                                                  Operation<Boolean> original) {
        boolean effectiveDrop = drop && !FireDropSuppression.active();
        return original.call(level, position, effectiveDrop, breaker, recursionLeft);
    }
}
