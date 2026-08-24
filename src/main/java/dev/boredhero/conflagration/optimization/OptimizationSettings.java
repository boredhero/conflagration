package dev.boredhero.conflagration.optimization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Settings for the mixin-based optimisations.
 *
 * <p><b>Why these are not in the main TOML config.</b> Mixins are applied during class transform,
 * long before NeoForge has loaded any {@code ModConfigSpec}. A mixin config plugin therefore cannot
 * read the normal config, so these live in a plain properties file that can be parsed with nothing
 * but the JDK. This mirrors how ModernFix and FerriteCore handle the same problem.
 *
 * <p>Everything defaults to <b>off</b>. The safe part of this mod needs no bytecode changes at all;
 * these are opt-in for administrators who have checked their pack and want the extra headroom.
 *
 * <p>This class deliberately has no Minecraft imports so it can be unit tested.
 */
public final class OptimizationSettings {

    public static final String FILE_NAME = "conflagration-optimizations.properties";

    public static final String KEY_ENABLED = "optimizations.enabled";
    public static final String KEY_FAST_NEIGHBOUR_SCAN = "optimizations.fast_neighbour_scan";
    public static final String KEY_TICK_BUDGET = "optimizations.tick_budget";
    public static final String KEY_MAX_FIRE_TICKS = "optimizations.max_fire_ticks_per_game_tick";
    public static final String KEY_DISTANCE_CULLING = "optimizations.distance_culling";
    public static final String KEY_CULL_RADIUS = "optimizations.cull_radius_blocks";
    public static final String KEY_IGNORE_CONFLICTS = "optimizations.ignore_detected_conflicts";

    /** Below this, a budget is more likely to stall fire than to protect the server. */
    public static final int MIN_FIRE_TICKS = 16;
    public static final int MAX_FIRE_TICKS = 100_000;
    /** Vanilla ticks fire wherever chunks simulate, so a tiny radius would be very visible. */
    public static final int MIN_CULL_RADIUS = 32;
    public static final int MAX_CULL_RADIUS = 4096;

    private static final int DEFAULT_MAX_FIRE_TICKS = 2048;
    private static final int DEFAULT_CULL_RADIUS = 256;

    private final boolean enabled;
    private final boolean fastNeighbourScan;
    private final boolean tickBudget;
    private final int maxFireTicksPerGameTick;
    private final boolean distanceCulling;
    private final int cullRadiusBlocks;
    private final boolean ignoreDetectedConflicts;

    private OptimizationSettings(Properties p) {
        this.enabled = bool(p, KEY_ENABLED, false);
        this.fastNeighbourScan = bool(p, KEY_FAST_NEIGHBOUR_SCAN, true);
        this.tickBudget = bool(p, KEY_TICK_BUDGET, false);
        this.maxFireTicksPerGameTick = clamp(
                integer(p, KEY_MAX_FIRE_TICKS, DEFAULT_MAX_FIRE_TICKS), MIN_FIRE_TICKS, MAX_FIRE_TICKS);
        this.distanceCulling = bool(p, KEY_DISTANCE_CULLING, false);
        this.cullRadiusBlocks = clamp(
                integer(p, KEY_CULL_RADIUS, DEFAULT_CULL_RADIUS), MIN_CULL_RADIUS, MAX_CULL_RADIUS);
        this.ignoreDetectedConflicts = bool(p, KEY_IGNORE_CONFLICTS, false);
    }

    /** All defaults; used when no file exists yet. */
    public static OptimizationSettings defaults() {
        return new OptimizationSettings(new Properties());
    }

    public static OptimizationSettings parse(String contents) {
        Properties p = new Properties();
        try (Reader reader = new StringReader(contents == null ? "" : contents)) {
            p.load(reader);
        } catch (IOException e) {
            return defaults();
        }
        return new OptimizationSettings(p);
    }

    /**
     * Loads from disk, writing a fully commented default file when absent. Never throws: a
     * malformed or unreadable file falls back to defaults, because failing to parse an optional
     * tuning file must never stop a server booting.
     */
    public static OptimizationSettings load(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(configDir);
                try (OutputStream out = Files.newOutputStream(file)) {
                    out.write(defaultFileContents().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                return defaults();
            }
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
            return new OptimizationSettings(p);
        } catch (IOException | RuntimeException e) {
            return defaults();
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /** True only when the master switch is also on. */
    public boolean fastNeighbourScan() {
        return enabled && fastNeighbourScan;
    }

    public boolean tickBudget() {
        return enabled && tickBudget;
    }

    public boolean distanceCulling() {
        return enabled && distanceCulling;
    }

    public int maxFireTicksPerGameTick() {
        return maxFireTicksPerGameTick;
    }

    public int cullRadiusBlocks() {
        return cullRadiusBlocks;
    }

    public boolean ignoreDetectedConflicts() {
        return ignoreDetectedConflicts;
    }

    /** True when no mixin needs to be applied at all, so the plugin can skip every one. */
    public boolean anyMixinNeeded() {
        return fastNeighbourScan() || tickBudget() || distanceCulling();
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (v.equals("true") || v.equals("yes") || v.equals("on") || v.equals("1")) {
            return true;
        }
        if (v.equals("false") || v.equals("no") || v.equals("off") || v.equals("0")) {
            return false;
        }
        return fallback;
    }

    private static int integer(Properties p, String key, int fallback) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static String defaultFileContents() {
        return """
                # Conflagration - optimisation settings
                #
                # These change Minecraft's fire code through mixins, which is a different kind of
                # risk from the rest of the mod. Conflagration's flammability tuning uses only
                # public API and cannot conflict with anything; the options below rewrite parts of
                # FireBlock and therefore can.
                #
                # They live in this file rather than the main TOML because mixins are applied long
                # before NeoForge loads normal configs.
                #
                # Everything defaults to off. Turn the master switch on only if you have looked at
                # your pack, and profile with spark afterwards.

                # Master switch. Nothing below has any effect while this is false.
                optimizations.enabled=false

                # Rewrite FireBlock's neighbour scan to stop throwing away memory.
                #
                # NeoForge's patched getIgniteOdds calls pos.relative(direction) TWICE per
                # direction - once for getBlockState and again for getFireSpreadSpeed - and
                # BlockPos.relative allocates every call. Together with Direction.values(), which
                # clones a 6 element array on each invocation, a single fire block can produce
                # several hundred short lived objects per tick.
                #
                # This computes each neighbour position once, reuses one mutable position, and
                # iterates a cached Direction array. It still calls getFireSpreadSpeed, so blocks
                # from other mods that override fire behaviour keep working normally.
                #
                # Safe, and the best value of the three. Only relevant when enabled=true.
                optimizations.fast_neighbour_scan=true

                # Cap how many fire blocks may run a full spread scan per game tick. Any beyond
                # the cap reschedule and are handled on a later tick, so nothing is lost - a very
                # large fire simply advances slightly more slowly instead of eating the tick.
                #
                # Changes observable behaviour under heavy load, so it is off by default.
                optimizations.tick_budget=false
                optimizations.max_fire_ticks_per_game_tick=2048

                # Skip fire ticks far from any player.
                #
                # Mojang added this natively in 1.21.5 (allowFireTicksAwayFromPlayer) and reworked
                # it again in 1.21.11 (fire_spread_radius_around_player). 1.21.1 has neither, so
                # this backports the idea.
                #
                # Note this genuinely changes behaviour: a fire you light and walk away from stops
                # spreading instead of burning on. Off by default for that reason.
                optimizations.distance_culling=false
                optimizations.cull_radius_blocks=256

                # Conflagration refuses to apply its fire mixins when it detects another mod that
                # mixes into the same FireBlock methods (Supplementaries and The Bumblezone both
                # do). Set this to true to override that and apply them anyway.
                #
                # Only do this if you are prepared to debug the result yourself.
                optimizations.ignore_detected_conflicts=false
                """;
    }
}
