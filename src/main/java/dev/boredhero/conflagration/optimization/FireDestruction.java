package dev.boredhero.conflagration.optimization;

import dev.boredhero.conflagration.config.ConflagrationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.Tags;

/** Rare-path destructive preparation for a block that vanilla fire has selected to remove. */
public final class FireDestruction {

    private FireDestruction() {
    }

    public static void beforeBurnout(Level level, BlockPos position) {
        if (!ConflagrationConfig.BURN_CHESTS.get()
                || !ConflagrationConfig.DESTROY_CHEST_CONTENTS.get()
                || !level.getBlockState(position).is(Tags.Blocks.CHESTS_WOODEN)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity instanceof RandomizableContainer randomizable) {
            // Prevent an unopened loot chest from generating its table during onRemove.
            randomizable.setLootTable(null);
        }
        if (blockEntity instanceof Container container) {
            container.clearContent();
            container.setChanged();
        }
    }
}
