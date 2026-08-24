package dev.boredhero.conflagration;

import com.mojang.logging.LogUtils;
import dev.boredhero.conflagration.config.ConflagrationConfig;
import dev.boredhero.conflagration.integration.FtbChunksIntegration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

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
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        FlammabilityApplier.apply(LOG);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        String result = FtbChunksIntegration.apply(ConflagrationConfig.CLAIM_FIRE_PROTECTION.get(), LOG);
        LOG.info("[Conflagration] {}", result);
    }
}
