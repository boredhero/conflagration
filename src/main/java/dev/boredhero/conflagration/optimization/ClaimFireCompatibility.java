package dev.boredhero.conflagration.optimization;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional, fail-closed bridges to claim mods that protect vanilla fire through mixins.
 *
 * <p>The handles are resolved once, while permission verdicts are deliberately never cached:
 * claim ownership and per-claim flags can change without a Conflagration-visible invalidation event.
 */
final class ClaimFireCompatibility {

    private static final AdapterSpec[] SPECS = {
            new AdapterSpec(
                    "ftbchunks",
                    "FTB Chunks",
                    "dev.ftb.mods.ftbchunks.util.FireSpreadHelper",
                    "shouldPreventFireSpread",
                    3,
                    true),
            new AdapterSpec(
                    "openpartiesandclaims",
                    "Open Parties and Claims",
                    "xaero.pac.common.server.core.ServerCore",
                    "canSpreadFire",
                    2,
                    false),
            new AdapterSpec(
                    "flan",
                    "Flan",
                    "io.github.flemmli97.flan.event.WorldEvents",
                    "canFireSpread",
                    2,
                    false)
    };

    private static volatile ActiveAdapter[] active = new ActiveAdapter[0];

    private ClaimFireCompatibility() {
    }

    static List<FirePerformance.CompatibilityBlocker> initialize(Logger log) {
        List<ActiveAdapter> resolved = new ArrayList<>();
        List<FirePerformance.CompatibilityBlocker> blockers = new ArrayList<>();

        for (AdapterSpec spec : SPECS) {
            if (!ModList.get().isLoaded(spec.modId())) {
                continue;
            }
            try {
                resolved.add(resolve(spec));
                log.info("[Conflagration] FRONTIER claim adapter: ACTIVE - {} {} [{}] via {}#{}; permission results are not cached",
                        displayName(spec.modId(), spec.displayName()), version(spec.modId()), spec.modId(),
                        spec.className(), spec.methodName());
            } catch (ReflectiveOperationException | RuntimeException exception) {
                blockers.add(new FirePerformance.CompatibilityBlocker(
                        spec.modId(),
                        displayName(spec.modId(), spec.displayName()),
                        version(spec.modId()),
                        "the installed version's fire-protection helper could not be linked ("
                                + exception.getClass().getSimpleName() + ")",
                        "version-gated claim adapter update"));
            }
        }

        blockers.addAll(detectBukkitClaimPlugins());
        active = resolved.toArray(ActiveAdapter[]::new);
        return List.copyOf(blockers);
    }

    static boolean mayIgnite(ServerLevel level, BlockPos source, BlockPos target) {
        for (ActiveAdapter adapter : active) {
            try {
                if (!adapter.allows(level, source, target)) {
                    return false;
                }
            } catch (Throwable throwable) {
                FirePerformance.disableFrontierAtRuntime(
                        adapter.spec().displayName(), adapter.spec().modId(), throwable);
                return false;
            }
        }
        return true;
    }

    private static ActiveAdapter resolve(AdapterSpec spec) throws ReflectiveOperationException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> owner = Class.forName(spec.className(), false, loader);
        Method candidate = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(spec.methodName())
                    && method.getParameterCount() == spec.parameterCount()
                    && method.getReturnType() == boolean.class
                    && Modifier.isStatic(method.getModifiers())) {
                if (candidate != null) {
                    throw new NoSuchMethodException("ambiguous helper " + spec.className() + '#' + spec.methodName());
                }
                candidate = method;
            }
        }
        if (candidate == null) {
            throw new NoSuchMethodException(spec.className() + '#' + spec.methodName());
        }
        if (!candidate.trySetAccessible()) {
            throw new IllegalAccessException(candidate.toString());
        }

        MethodHandle handle = MethodHandles.lookup().unreflect(candidate);
        MethodType genericType = spec.parameterCount() == 3
                ? MethodType.methodType(boolean.class, Object.class, Object.class, Object.class)
                : MethodType.methodType(boolean.class, Object.class, Object.class);
        return new ActiveAdapter(spec, handle.asType(genericType));
    }

    private static List<FirePerformance.CompatibilityBlocker> detectBukkitClaimPlugins() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final Class<?> bukkit;
        try {
            bukkit = Class.forName("org.bukkit.Bukkit", false, loader);
        } catch (ClassNotFoundException absent) {
            return List.of();
        }

        List<FirePerformance.CompatibilityBlocker> blockers = new ArrayList<>();
        try {
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            Method getPlugin = manager.getClass().getMethod("getPlugin", String.class);
            for (String plugin : List.of("WorldGuard", "Towny", "GriefDefender")) {
                if (getPlugin.invoke(manager, plugin) != null) {
                    blockers.add(new FirePerformance.CompatibilityBlocker(
                            "bukkit:" + plugin.toLowerCase(),
                            plugin,
                            "unknown",
                            "a hybrid Bukkit claim plugin owns BlockIgniteEvent/BlockBurnEvent, which direct NeoForge placement may bypass",
                            "hybrid-platform fire event bridge"));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            blockers.add(new FirePerformance.CompatibilityBlocker(
                    "bukkit",
                    "Bukkit-compatible hybrid server",
                    "unknown",
                    "the Bukkit plugin inventory could not be audited ("
                            + exception.getClass().getSimpleName() + ")",
                    "hybrid-platform compatibility audit"));
        }
        return blockers;
    }

    private static String displayName(String modId, String fallback) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(fallback);
    }

    private static String version(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    private record AdapterSpec(String modId,
                               String displayName,
                               String className,
                               String methodName,
                               int parameterCount,
                               boolean trueMeansDeny) {
    }

    private record ActiveAdapter(AdapterSpec spec, MethodHandle handle) {

        boolean allows(ServerLevel level, BlockPos source, BlockPos target) throws Throwable {
            boolean result = spec.parameterCount() == 3
                    ? (boolean) handle.invokeExact((Object) level, (Object) source, (Object) target)
                    : (boolean) handle.invokeExact((Object) level, (Object) target);
            return spec.trueMeansDeny() != result;
        }
    }
}
