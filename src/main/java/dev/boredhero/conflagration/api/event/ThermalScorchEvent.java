package dev.boredhero.conflagration.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Fired before a low-tier terrain response (grass/farmland, sand, or clay) changes a block. */
public final class ThermalScorchEvent extends Event implements ICancellableEvent {

    private final ServerLevel level;
    private final BlockPos source;
    private final BlockPos target;
    private final BlockState targetState;
    private final double incidentFluxKwM2;

    public ThermalScorchEvent(ServerLevel level,
                              BlockPos source,
                              BlockPos target,
                              BlockState targetState,
                              double incidentFluxKwM2) {
        this.level = level;
        this.source = source.immutable();
        this.target = target.immutable();
        this.targetState = targetState;
        this.incidentFluxKwM2 = incidentFluxKwM2;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getSource() {
        return source;
    }

    public BlockPos getTarget() {
        return target;
    }

    public BlockState getTargetState() {
        return targetState;
    }

    public double getIncidentFluxKwM2() {
        return incidentFluxKwM2;
    }
}
