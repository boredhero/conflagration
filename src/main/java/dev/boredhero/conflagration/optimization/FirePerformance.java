package dev.boredhero.conflagration.optimization;

import dev.boredhero.conflagration.config.ConflagrationConfig;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime switches and compatibility diagnostics for the fire mixins. */
public final class FirePerformance {

    private static final Map<String, String> KNOWN_MODS = knownMods();

    // Safe until the common config is loaded. Refreshed on tag load and server start.
    private static volatile boolean optimizeNeighbourScans = true;
    private static volatile FireEngineMode requestedEngine = FireEngineMode.VANILLA;
    private static volatile boolean frontierActive;
    private static volatile int frontierRescanInterval = 200;
    private static volatile int frontierMaxEventsPerTick = 2048;
    private static volatile int frontierMaxSourcesPerTick = 256;
    private static volatile int frontierMaxPendingEvents = 100_000;
    private static volatile Logger runtimeLog;
    private static final AtomicBoolean RUNTIME_FAILURE_LOGGED = new AtomicBoolean();

    private static final Map<String, FrontierConflict> FRONTIER_CONFLICTS = frontierConflicts();

    private FirePerformance() {
    }

    public static void refreshFromConfig() {
        FireEngineMode previousEngine = requestedEngine;
        optimizeNeighbourScans = ConflagrationConfig.ENABLED.get()
                && ConflagrationConfig.OPTIMIZE_NEIGHBOUR_SCANS.get();
        requestedEngine = ConflagrationConfig.ENABLED.get()
                ? ConflagrationConfig.FIRE_ENGINE.get()
                : FireEngineMode.VANILLA;
        frontierRescanInterval = ConflagrationConfig.FRONTIER_RESCAN_INTERVAL.get();
        frontierMaxEventsPerTick = ConflagrationConfig.FRONTIER_MAX_EVENTS_PER_TICK.get();
        frontierMaxSourcesPerTick = ConflagrationConfig.FRONTIER_MAX_SOURCES_PER_TICK.get();
        frontierMaxPendingEvents = ConflagrationConfig.FRONTIER_MAX_PENDING_EVENTS.get();
        // Compatibility is resolved once the complete mod list is available at server start.
        // Preserve an already-resolved FRONTIER decision across datapack/tag reloads. A runtime
        // engine change still waits for the next server start so we never enable an unchecked path.
        if (requestedEngine != FireEngineMode.FRONTIER || previousEngine != FireEngineMode.FRONTIER) {
            frontierActive = false;
        }
    }

    public static boolean optimizeNeighbourScans() {
        return optimizeNeighbourScans;
    }

    public static boolean frontierActive() {
        return frontierActive;
    }

    public static int frontierRescanInterval() {
        return frontierRescanInterval;
    }

    public static int frontierMaxEventsPerTick() {
        return frontierMaxEventsPerTick;
    }

    public static int frontierMaxSourcesPerTick() {
        return frontierMaxSourcesPerTick;
    }

    public static int frontierMaxPendingEvents() {
        return frontierMaxPendingEvents;
    }

    /**
     * Reports known optimization/fire mixin mods. The allocation wrapper is deliberately kept on:
     * it composes at the two {@code BlockPos.relative} calls and does not replace the helper, tick,
     * claim checks, contextual block hooks, or burnout callbacks those mods modify.
     */
    public static void logCompatibility(Logger log) {
        runtimeLog = log;
        RUNTIME_FAILURE_LOGGED.set(false);
        StringBuilder detected = new StringBuilder();
        for (Map.Entry<String, String> entry : KNOWN_MODS.entrySet()) {
            if (ModList.get().isLoaded(entry.getKey())) {
                if (!detected.isEmpty()) {
                    detected.append(", ");
                }
                detected.append(modDisplayName(entry.getKey(), entry.getKey()))
                        .append(' ').append(modVersion(entry.getKey()))
                        .append(" [").append(entry.getKey()).append("; ")
                        .append(entry.getValue()).append(']');
            }
        }

        if (detected.isEmpty()) {
            log.info("[Conflagration] fire optimization compatibility: no known overlapping mods detected");
        } else {
            log.info("[Conflagration] fire optimization compatibility: detected {}; preserving contextual hooks and mixin call sites",
                    detected);
        }


        List<CompatibilityBlocker> blockers = new ArrayList<>();
        for (Map.Entry<String, FrontierConflict> entry : FRONTIER_CONFLICTS.entrySet()) {
            if (ModList.get().isLoaded(entry.getKey())) {
                FrontierConflict conflict = entry.getValue();
                blockers.add(new CompatibilityBlocker(
                        entry.getKey(),
                        modDisplayName(entry.getKey(), conflict.name()),
                        modVersion(entry.getKey()),
                        conflict.reason(),
                        conflict.adapterNeeded()));
            }
        }
        blockers.addAll(ClaimFireCompatibility.initialize(log));
        boolean conflict = !blockers.isEmpty();
        boolean forced = ConflagrationConfig.FRONTIER_COMPATIBILITY.get()
                == FrontierCompatibilityMode.FORCE_UNSAFE;
        frontierActive = requestedEngine == FireEngineMode.FRONTIER && (!conflict || forced);

        if (requestedEngine == FireEngineMode.FRONTIER && conflict && !forced) {
            log.warn("[Conflagration] FRONTIER cannot start safely; falling back to VANILLA (AUTO_STRICT)");
            blockers.forEach(blocker -> logFrontierBlocker(log, blocker, false));
        } else if (frontierActive && conflict) {
            log.warn("[Conflagration] FRONTIER forced on despite a known conflict; claim or special-fire behavior may be bypassed");
            blockers.forEach(blocker -> logFrontierBlocker(log, blocker, true));
        }
        log.info("[Conflagration] fire spread engine: {}{}", frontierActive ? "FRONTIER" : "VANILLA",
                requestedEngine == FireEngineMode.FRONTIER && !frontierActive ? " (fallback)" : "");
        log.info("[Conflagration] allocation-safe neighbour scan optimization: {}",
                optimizeNeighbourScans ? "enabled" : "disabled");
    }

    private static Map<String, String> knownMods() {
        Map<String, String> mods = new LinkedHashMap<>();
        mods.put("lithium", "performance");
        mods.put("modernfix", "performance");
        mods.put("ferritecore", "memory");
        mods.put("canary", "performance");
        mods.put("radium", "performance");
        mods.put("servercore", "performance");
        mods.put("moonrise", "performance");
        mods.put("noisium", "performance");
        mods.put("openpartiesandclaims", "claim fire protection");
        mods.put("ftbchunks", "claim fire protection");
        mods.put("flan", "claim fire protection");
        mods.put("cadmus", "claim fire protection");
        mods.put("odyssey_claims", "claim fire protection");
        mods.put("griefdefender", "claim fire protection");
        mods.put("claimmyland", "claim fire protection");
        mods.put("claim", "claims; no audited fire hook");
        mods.put("supplementaries", "FireBlock extensions");
        mods.put("the_bumblezone", "FireBlock burnout hooks");
        return Map.copyOf(mods);
    }

    private static Map<String, FrontierConflict> frontierConflicts() {
        Map<String, FrontierConflict> conflicts = new LinkedHashMap<>();
        conflicts.put("cadmus", new FrontierConflict(
                "Cadmus",
                "uses source flags plus operation-specific source/target block-placement checks",
                "upstream canFireSpread(level, source, target, operation) API"));
        conflicts.put("odyssey_claims", new FrontierConflict(
                "Odyssey Claims",
                "the Cadmus-family protection model needs operation-specific source/target checks",
                "upstream canFireSpread(level, source, target, operation) API"));
        conflicts.put("griefdefender", new FrontierConflict(
                "GriefDefender",
                "its private NeoForge build and complete LuckPerms protection contexts cannot be audited here",
                "audited platform permission adapter"));
        conflicts.put("claimmyland", new FrontierConflict(
                "Claim My Land",
                "its placement event can cancel natural block placement beyond the named fire-spread helper",
                "upstream canNaturalBlockPlace(level, source, target, state) API"));
        conflicts.put("supplementaries", new FrontierConflict(
                "Supplementaries",
                "wraps vanilla ignition odds and fire placement for flammable liquids",
                "flammable-liquid ignition adapter"));
        conflicts.put("connectormod", new FrontierConflict(
                "Sinytra Connector",
                "may load Fabric FireBlock transformations that cannot be inventoried reliably",
                "transformed-class compatibility audit"));
        conflicts.put("connector", conflicts.get("connectormod"));
        return java.util.Collections.unmodifiableMap(conflicts);
    }

    static void disableFrontierAtRuntime(String providerName, String providerId, Throwable throwable) {
        frontierActive = false;
        Logger log = runtimeLog;
        if (log != null && RUNTIME_FAILURE_LOGGED.compareAndSet(false, true)) {
            log.error("[Conflagration] FRONTIER disabled at runtime: claim adapter {} {} [{}] failed "
                            + "during IGNITE_AIR with {}. This ignition was denied and subsequent fire ticks "
                            + "will use VANILLA. Please report this provider version.",
                    providerName, modVersion(providerId), providerId, throwable.toString());
        }
    }

    private static void logFrontierBlocker(Logger log,
                                           CompatibilityBlocker blocker,
                                           boolean forced) {
        String action = forced ? "FORCE_UNSAFE is bypassing this guard" : "VANILLA selected";
        log.warn("[Conflagration] FRONTIER blocker: {} {} [{}] - {}; {}. Future support needs: {}",
                blocker.name(), blocker.version(), blocker.id(), blocker.reason(), action, blocker.adapterNeeded());
    }

    private static String modDisplayName(String modId, String fallback) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(fallback);
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    private record FrontierConflict(String name, String reason, String adapterNeeded) {
    }

    record CompatibilityBlocker(String id,
                                String name,
                                String version,
                                String reason,
                                String adapterNeeded) {
    }
}
