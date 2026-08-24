package dev.boredhero.conflagration.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.boredhero.conflagration.optimization.FirePerformance;
import dev.boredhero.conflagration.optimization.FireDropSuppression;
import dev.boredhero.conflagration.optimization.FrontierFireEngine;
import dev.boredhero.conflagration.optimization.VanillaRate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Allocation-only acceleration for FireBlock's hottest helper.
 *
 * <p>NeoForge's helper asks for the same relative position twice per direction: once to fetch the
 * state and once to pass its position to the contextual fire-spread hook. Vanilla allocates a new
 * immutable BlockPos both times. These composable wrappers remember the first result and supply the
 * exact same immutable object to the second call. Scan order, reads, hook calls, random calls, and
 * object mutability are unchanged.
 *
 * <p>The default optimization intentionally does not inject at the helper head or alter
 * {@code tick}/{@code checkBurnOut}. The separate opt-in FRONTIER injection enters {@code tick}
 * only after lifecycle and direct burnout work. Open Parties and Claims cancels the helper at its
 * head, FTB Chunks and Supplementaries wrap its call site, and Bumblezone injects into burnout;
 * all of those seams stay intact in VANILLA mode.
 */
@Mixin(FireBlock.class)
abstract class FireBlockMixin {

    @Unique
    private static final Direction[] CONFLAGRATION_DIRECTIONS = Direction.values();

    /**
     * Runs after vanilla lifecycle, age, rain, survival, and six direct burnout checks, immediately
     * before its 53-candidate spread loop. Disabled by default; returning false leaves the loop and
     * every other mod's injection point untouched.
     */
    @Inject(
            method = "tick",
            at = @At(value = "NEW", target = "net/minecraft/core/BlockPos$MutableBlockPos"),
            cancellable = true)
    private void conflagration$runFrontier(BlockState state,
                                           ServerLevel level,
                                           BlockPos position,
                                           RandomSource random,
                                           CallbackInfo callback) {
        if (FrontierFireEngine.tick(level, position, state)) {
            callback.cancel();
        }
    }

    /** Keeps structural cleanup caused by a successful burnout inside a no-drop context. */
    @WrapOperation(
            method = "checkBurnOut",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean conflagration$suppressFireRemovalDrops(Level level,
                                                           BlockPos position,
                                                           boolean moving,
                                                           Operation<Boolean> original) {
        if (!FirePerformance.suppressFireDrops()) {
            return original.call(level, position, moving);
        }
        FireDropSuppression.enter();
        try {
            return original.call(level, position, moving);
        } finally {
            FireDropSuppression.exit();
        }
    }

    @WrapOperation(
            method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;values()[Lnet/minecraft/core/Direction;"))
    private Direction[] conflagration$reuseDirections(Operation<Direction[]> original) {
        return FirePerformance.optimizeNeighbourScans() ? CONFLAGRATION_DIRECTIONS : original.call();
    }

    /**
     * Speeds vanilla's candidate ignition probability without changing tick cadence or replacing
     * the helper. Claim mods that return zero remain zero, and call-site wrappers still run after
     * this result is produced.
     */
    @ModifyReturnValue(
            method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"))
    private int conflagration$scaleVanillaIgnition(int original) {
        if (FirePerformance.frontierActive()) {
            return original;
        }
        return VanillaRate.scaleIgniteOdds(original, FirePerformance.vanillaSpreadSpeed());
    }

    @WrapOperation(
            method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;",
                    ordinal = 0))
    private BlockPos conflagration$rememberNeighbour(BlockPos position,
                                                     Direction direction,
                                                     Operation<BlockPos> original,
                                                     @Share("conflagration$neighbour") LocalRef<BlockPos> neighbour) {
        BlockPos result = original.call(position, direction);
        // Once populated, the shared reference also carries the enabled state through the rest of
        // this helper invocation. The normal enabled path therefore pays one volatile config read,
        // not two reads for each of six directions.
        if (neighbour.get() != null || FirePerformance.optimizeNeighbourScans()) {
            neighbour.set(result);
        }
        return result;
    }

    @WrapOperation(
            method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;",
                    ordinal = 1))
    private BlockPos conflagration$reuseNeighbour(BlockPos position,
                                                  Direction direction,
                                                  Operation<BlockPos> original,
                                                  @Share("conflagration$neighbour") LocalRef<BlockPos> neighbour) {
        BlockPos cached = neighbour.get();
        if (cached != null) {
            return cached;
        }
        return original.call(position, direction);
    }
}
