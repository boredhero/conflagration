package dev.boredhero.conflagration.integration;

import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Optional integration with FTB Chunks' claim-based fire protection.
 *
 * <p>Reached entirely by reflection so FTB Chunks stays a <em>soft</em> dependency: this mod
 * runs unchanged on a bare NeoForge server with no FTB mods installed. Nothing here is on a hot
 * path — it runs once per server start.
 *
 * <p>Why this matters: raising flammability makes fire genuinely dangerous, and on a shared
 * server that turns a neighbour's forest fire into someone else's lost base. FTB Chunks already
 * implements exactly the right guard ({@code fire_spread_protection}), so we drive its setting
 * rather than reimplementing claim lookups.
 *
 * <p>The value is applied in memory on every server start, so it is reasserted after any manual
 * edit or datapack reload rather than depending on the on-disk snbt staying as we left it.
 */
public final class FtbChunksIntegration {

    private static final String CONFIG_CLASS = "dev.ftb.mods.ftbchunks.FTBChunksWorldConfig";
    private static final String VALUE_CLASS = "dev.ftb.mods.ftblibrary.snbt.config.BaseValue";
    private static final String FIELD = "FIRE_SPREAD_PROTECTION";

    private FtbChunksIntegration() {
    }

    /**
     * @return a human-readable description of what happened, for logging
     */
    public static String apply(ClaimProtectionMode mode, Logger log) {
        if (mode == ClaimProtectionMode.LEAVE_ALONE) {
            return "claim fire protection left as configured by FTB Chunks";
        }

        boolean desired = mode == ClaimProtectionMode.ENABLE;

        try {
            Class<?> configClass = Class.forName(CONFIG_CLASS);
            Field field = configClass.getField(FIELD);
            Object value = field.get(null);
            if (value == null) {
                return "FTB Chunks present but " + FIELD + " was null; leaving it alone";
            }

            Class<?> baseValue = Class.forName(VALUE_CLASS);
            Method get = baseValue.getMethod("get");
            Method set = baseValue.getMethod("set", Object.class);

            Object before = get.invoke(value);
            if (Boolean.valueOf(desired).equals(before)) {
                return "claim fire protection already " + desired + "; nothing to do";
            }
            set.invoke(value, desired);

            return "claim fire protection set to " + desired + " (was " + before + ")";
        } catch (ClassNotFoundException e) {
            // The overwhelmingly common case on a non-FTB server. Not a problem.
            return "FTB Chunks not installed; skipping claim fire protection";
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            log.warn("[Conflagration] FTB Chunks is installed but its config API has changed "
                    + "({}). Set fire_spread_protection manually in ftbchunks-world.snbt.", e.toString());
            return "FTB Chunks config API not recognised; skipped";
        } catch (Throwable t) {
            log.warn("[Conflagration] Failed to apply claim fire protection", t);
            return "failed to apply claim fire protection: " + t;
        }
    }
}
