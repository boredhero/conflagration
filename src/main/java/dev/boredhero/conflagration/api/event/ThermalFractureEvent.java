package dev.boredhero.conflagration.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Fired immediately before radiant heat destroys a thermally stressed glass block. */
public final class ThermalFractureEvent extends Event implements ICancellableEvent {

    private final ServerLevel level;
    private final BlockPos source;
    private final BlockPos target;
    private final BlockState targetState;
    private final double incidentFluxKwM2;
    private final double temperatureDeltaC;

    public ThermalFractureEvent(ServerLevel level,
                                BlockPos source,
                                BlockPos target,
                                BlockState targetState,
                                double incidentFluxKwM2,
                                double temperatureDeltaC) {
        this.level = level;
        this.source = source.immutable();
        this.target = target.immutable();
        this.targetState = targetState;
        this.incidentFluxKwM2 = incidentFluxKwM2;
        this.temperatureDeltaC = temperatureDeltaC;
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

    public double getTemperatureDeltaC() {
        return temperatureDeltaC;
    }
}
