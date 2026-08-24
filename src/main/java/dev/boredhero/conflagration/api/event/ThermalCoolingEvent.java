package dev.boredhero.conflagration.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Fired before magma previously created by Conflagration cools back into stone. */
public final class ThermalCoolingEvent extends Event implements ICancellableEvent {

    private final ServerLevel level;
    private final BlockPos originalSource;
    private final BlockPos target;

    public ThermalCoolingEvent(ServerLevel level, BlockPos originalSource, BlockPos target) {
        this.level = level;
        this.originalSource = originalSource.immutable();
        this.target = target.immutable();
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getOriginalSource() {
        return originalSource;
    }

    public BlockPos getTarget() {
        return target;
    }
}
