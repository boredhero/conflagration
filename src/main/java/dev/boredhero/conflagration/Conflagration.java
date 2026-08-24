package dev.boredhero.conflagration;

import com.mojang.logging.LogUtils;
import dev.boredhero.conflagration.config.ConflagrationConfig;
import dev.boredhero.conflagration.heat.FireHeatManager;
import dev.boredhero.conflagration.integration.FtbChunksIntegration;
import dev.boredhero.conflagration.optimization.FirePerformance;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * Conflagration - aggressive, configurable fire spread.
 *
 * <p>Server-side only in effect: everything it changes lives in server-side game logic, and it
 * registers no blocks, items or renderers, so clients need not install it.
 */
@Mod(Conflagration.MOD_ID)
public final class Conflagration {

    public static final String MOD_ID = "conflagration";

    private static final Logger LOG = LogUtils.getLogger();

    public Conflagration(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, ConflagrationConfig.SPEC);

        // Tag load, not mod construction: categorisation is tag-driven, and tags are datapack
        // content that is not available until the server loads them. This also re-applies after
        // a /reload, so editing the config and reloading takes effect without a restart.
        NeoForge.EVENT_BUS.addListener(Conflagration::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(Conflagration::onServerStarted);
        NeoForge.EVENT_BUS.addListener(Conflagration::onLevelTick);
        NeoForge.EVENT_BUS.addListener(Conflagration::onEntityTick);
        NeoForge.EVENT_BUS.addListener(Conflagration::onRegisterCommands);
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        // Static block data is shared by the logical server and client in single-player. Applying
        // both the server tag load and the following client packet would snapshot our own values as
        // the baseline and make later restoration impossible.
        if (event.shouldUpdateStaticData()) {
            FirePerformance.refreshFromConfig();
            FireHeatManager.refreshFromConfig();
            FlammabilityApplier.apply(LOG);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        FirePerformance.refreshFromConfig();
        FireHeatManager.refreshFromConfig();
        FirePerformance.logCompatibility(LOG);
        FireHeatManager.logStatus(LOG);

        if (!ConflagrationConfig.ENABLED.get()) {
            LOG.info("[Conflagration] disabled by config; optional integrations left alone");
            return;
        }

        String result = FtbChunksIntegration.apply(ConflagrationConfig.CLAIM_FIRE_PROTECTION.get(), LOG);
        LOG.info("[Conflagration] {}", result);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            FireHeatManager.tickLevel(level);
        }
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        FireHeatManager.tickEntity(event.getEntity());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("conflagration")
                .then(Commands.literal("heat").executes(context -> {
                    FireHeatManager.HeatReading reading = FireHeatManager.reading(
                            context.getSource().getEntityOrException());
                    String message = String.format(Locale.ROOT,
                            "Heat: %.2f kW/m², %.0f°C equivalent, %d nearby fires, "
                                    + "dose %.2f%s",
                            reading.fluxKwM2(),
                            reading.equivalentTemperatureC(),
                            reading.nearbyFires(),
                            reading.accumulatedDose(),
                            reading.occluded() ? ", shielded" : "");
                    context.getSource().sendSuccess(() -> Component.literal(message), false);
                    return 1;
                })));
    }
}
