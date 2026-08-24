package dev.boredhero.conflagration.config;

import dev.boredhero.conflagration.integration.ClaimProtectionMode;
import dev.boredhero.conflagration.optimization.FireEngineMode;
import dev.boredhero.conflagration.optimization.FrontierCompatibilityMode;
import dev.boredhero.conflagration.policy.FirePreset;
import dev.boredhero.conflagration.policy.FlammabilityPolicy;
import dev.boredhero.conflagration.policy.FuelCategory;
import dev.boredhero.conflagration.policy.Odds;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The {@code config/conflagration-common.toml} schema. */
public final class ConflagrationConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.EnumValue<FirePreset> PRESET;
    public static final ModConfigSpec.BooleanValue LOG_APPLIED_VALUES;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> OVERRIDES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;

    public static final ModConfigSpec.EnumValue<ClaimProtectionMode> CLAIM_FIRE_PROTECTION;

    public static final ModConfigSpec.BooleanValue OPTIMIZE_NEIGHBOUR_SCANS;
    public static final ModConfigSpec.EnumValue<FireEngineMode> FIRE_ENGINE;
    public static final ModConfigSpec.DoubleValue VANILLA_SPREAD_SPEED;
    public static final ModConfigSpec.EnumValue<FrontierCompatibilityMode> FRONTIER_COMPATIBILITY;
    public static final ModConfigSpec.DoubleValue FRONTIER_SPREAD_SPEED;
    public static final ModConfigSpec.IntValue FRONTIER_EMBER_JUMP_DISTANCE;
    public static final ModConfigSpec.IntValue FRONTIER_RESCAN_INTERVAL;
    public static final ModConfigSpec.IntValue FRONTIER_MAX_EVENTS_PER_TICK;
    public static final ModConfigSpec.IntValue FRONTIER_MAX_SOURCES_PER_TICK;
    public static final ModConfigSpec.IntValue FRONTIER_MAX_PENDING_EVENTS;

    private static final Map<FuelCategory, ModConfigSpec.IntValue> CUSTOM_IGNITE =
            new EnumMap<>(FuelCategory.class);
    private static final Map<FuelCategory, ModConfigSpec.IntValue> CUSTOM_BURN =
            new EnumMap<>(FuelCategory.class);

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment(
                        "Conflagration - aggressive, configurable fire spread.",
                        "",
                        "Two numbers control every flammable block in Minecraft, and they do",
                        "different jobs:",
                        "  ignite - how likely the block is to CATCH from a nearby fire.",
                        "           Raise this to make fire TRAVEL.",
                        "  burn   - how quickly the block is CONSUMED once alight. Raising this",
                        "           too far makes fires shorter, because the fire loses the fuel",
                        "           it was standing on.",
                        "For fires that level a building, you want HIGH ignite and MODERATE burn.",
                        "",
                        "Vanilla logs sit at ignite 5, which is the single reason forest fires",
                        "burn the canopy and leave the trunks standing.")
                .push("general");

        ENABLED = builder
                .comment("Master switch. When false, Conflagration changes nothing at all.")
                .define("enabled", true);

        PRESET = builder
                .comment("",
                        "VANILLA     - Mojang's stock values. Effectively disables the mod.",
                        "SMOULDERING - restrained. Buildings burn, slowly, and fire is survivable.",
                        "AGGRESSIVE  - the intended default. A lit house burns down; forest fires carry.",
                        "INFERNO     - deliberately unreasonable. Fire crosses open ground. You will lose things.",
                        "CUSTOM      - use the [custom] values below verbatim.")
                .defineEnum("preset", FirePreset.AGGRESSIVE);

        LOG_APPLIED_VALUES = builder
                .comment("",
                        "Log every block whose flammability was changed, at server start.",
                        "Useful once to see what a preset actually did; noisy afterwards.")
                .define("log_applied_values", false);

        builder.pop();

        builder.comment(
                        "Per-category values, used when preset = CUSTOM.",
                        "Both numbers are clamped to 0-300. Setting both to 0 makes the category",
                        "fireproof.")
                .push("custom");

        for (FuelCategory category : FuelCategory.values()) {
            String key = category.name().toLowerCase(Locale.ROOT);
            Odds seed = FirePreset.CUSTOM.get(category);
            builder.push(key);
            CUSTOM_IGNITE.put(category, builder
                    .comment("How readily this catches fire. Vanilla: "
                            + FirePreset.VANILLA.get(category).ignite())
                    .defineInRange("ignite", seed.ignite(), Odds.MIN, Odds.MAX));
            CUSTOM_BURN.put(category, builder
                    .comment("How quickly this is consumed. Vanilla: "
                            + FirePreset.VANILLA.get(category).burn())
                    .defineInRange("burn", seed.burn(), Odds.MIN, Odds.MAX));
            builder.pop();
        }

        builder.pop();

        builder.comment("Fine-grained control over individual blocks.").push("blocks");

        OVERRIDES = builder
                .comment("Force exact values for specific blocks, whatever the preset says.",
                        "Format: \"namespace:block=ignite,burn\". The namespace may be omitted",
                        "and defaults to minecraft. Works for modded blocks.",
                        "Example: [\"minecraft:oak_log=80,10\", \"create:andesite_casing=0,0\"]")
                .defineListAllowEmpty("overrides", List.of(),
                        () -> "minecraft:oak_log=80,10", ConflagrationConfig::isString);

        BLACKLIST = builder
                .comment("",
                        "Blocks Conflagration must never touch, whatever else is configured.",
                        "Takes priority over overrides. Use this to protect a specific block",
                        "another mod depends on.",
                        "Example: [\"minecraft:bookshelf\"]")
                .defineListAllowEmpty("blacklist", List.of(),
                        () -> "minecraft:bookshelf", ConflagrationConfig::isString);

        builder.pop();

        builder.comment(
                        "Integration with other mods. All optional - Conflagration runs fine",
                        "on a bare NeoForge server with none of these installed.")
                .push("integration");

        CLAIM_FIRE_PROTECTION = builder
                .comment("Controls FTB Chunks' claim fire protection, if FTB Chunks is present.",
                        "Aggressive fire plus unprotected claims means a neighbour's forest fire",
                        "can take out someone's base, so this defaults to ENABLE.",
                        "  ENABLE      - fire cannot spread into another team's claimed chunks.",
                        "  DISABLE     - fire spreads everywhere, including into claims.",
                        "  LEAVE_ALONE - do not touch FTB Chunks' own setting.",
                        "Ignored entirely when FTB Chunks is not installed.")
                .defineEnum("claim_fire_protection", ClaimProtectionMode.ENABLE);

        builder.pop();

        builder.comment(
                        "Server-side fire performance. These optimizations use narrow mixin",
                        "injections but preserve NeoForge's contextual fire hooks for modded blocks.")
                .push("performance");

        OPTIMIZE_NEIGHBOUR_SCANS = builder
                .comment("Avoid the hundreds of temporary BlockPos objects vanilla creates during",
                        "each fire spread scan. This preserves scan order, random calls, world reads,",
                        "and BlockState#getFireSpreadSpeed callbacks. Enabled by default because it",
                        "does not change fire behavior. Set false as a compatibility escape hatch.")
                .define("optimize_neighbour_scans", true);

        FIRE_ENGINE = builder
                .comment("",
                        "Spread engine. VANILLA preserves Minecraft exactly. FRONTIER is an",
                        "experimental sparse event engine that scans each fire occasionally and",
                        "schedules deduplicated ignition arrivals instead of retrying all 53",
                        "candidates every fire tick. FRONTIER changes timing and RNG distribution.")
                .defineEnum("engine", FireEngineMode.VANILLA);

        VANILLA_SPREAD_SPEED = builder
                .comment("Ignition-odds multiplier used only by the VANILLA spread engine.",
                        "1.0 preserves vanilla timing. Higher values make nearby fuel catch faster",
                        "without changing fire tick cadence, burnout, claim checks, or mod hooks.")
                .defineInRange("vanilla_spread_speed", 1.0, 1.0, 20.0);

        FRONTIER_COMPATIBILITY = builder
                .comment("",
                        "AUTO_STRICT falls back to VANILLA when a known claim/fire mod injects into",
                        "the vanilla spread loop. FORCE_UNSAFE bypasses that guard. It may allow fire",
                        "through claims or skip special block behavior; pack authors own the result.")
                .defineEnum("frontier_compatibility", FrontierCompatibilityMode.AUTO_STRICT);

        FRONTIER_SPREAD_SPEED = builder
                .comment("FRONTIER ignition-arrival rate multiplier. 1.0 is the vanilla-speed",
                        "baseline (FRONTIER timing is still not bit-for-bit vanilla). The default",
                        "2.0 halves the mean sampled delay; 4.0 quarters it. This preserves the",
                        "exponential distribution while making an established fire accelerate",
                        "across available fuel more quickly.")
                .defineInRange("frontier_spread_speed", 2.0, 0.05, 20.0);

        FRONTIER_EMBER_JUMP_DISTANCE = builder
                .comment("Maximum horizontal landing distance FRONTIER scans for ember jumps.",
                        "1 matches vanilla's local footprint. The default 2 lets fire reach air",
                        "beside fuel across short stone/path gaps; the outer ring receives a",
                        "distance-squared delay penalty. Higher values cost more neighbour reads.")
                .defineInRange("frontier_ember_jump_distance", 2, 1, 4);

        FRONTIER_RESCAN_INTERVAL = builder
                .comment("Game ticks before FRONTIER re-discovers edges around the same source fire.",
                        "Higher values reduce scans; lower values react faster to changed neighbours.")
                .defineInRange("frontier_rescan_interval", 200, 20, 20_000);

        FRONTIER_MAX_EVENTS_PER_TICK = builder
                .comment("Maximum due frontier ignitions processed per level per game tick.")
                .defineInRange("frontier_max_events_per_tick", 2048, 16, 100_000);

        FRONTIER_MAX_SOURCES_PER_TICK = builder
                .comment("Maximum source fires allowed to discover new frontier edges per level tick.")
                .defineInRange("frontier_max_sources_per_tick", 256, 1, 10_000);

        FRONTIER_MAX_PENDING_EVENTS = builder
                .comment("Hard cap on queued ignition arrivals per level. New events are deferred",
                        "when full rather than allowing an inferno to exhaust server memory.")
                .defineInRange("frontier_max_pending_events", 100_000, 1024, 2_000_000);

        builder.pop();

        SPEC = builder.build();
    }

    private ConflagrationConfig() {
    }

    private static boolean isString(Object o) {
        return o instanceof String;
    }

    /** Builds the resolved policy from the current config values. */
    public static FlammabilityPolicy buildPolicy() {
        FirePreset preset = PRESET.get();
        FlammabilityPolicy.Builder builder = FlammabilityPolicy.builder(preset);

        if (preset == FirePreset.CUSTOM) {
            for (FuelCategory category : FuelCategory.values()) {
                builder.category(category, new Odds(
                        CUSTOM_IGNITE.get(category).get(),
                        CUSTOM_BURN.get(category).get()));
            }
        }

        return builder
                .overrides(OVERRIDES.get())
                .blacklist(BLACKLIST.get())
                .build();
    }
}
