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

    public static final ModConfigSpec.BooleanValue BURN_CHESTS;
    public static final ModConfigSpec.BooleanValue DESTROY_CHEST_CONTENTS;

    public static final ModConfigSpec.EnumValue<ClaimProtectionMode> CLAIM_FIRE_PROTECTION;

    public static final ModConfigSpec.BooleanValue OPTIMIZE_NEIGHBOUR_SCANS;
    public static final ModConfigSpec.EnumValue<FireEngineMode> FIRE_ENGINE;
    public static final ModConfigSpec.DoubleValue VANILLA_SPREAD_SPEED;
    public static final ModConfigSpec.EnumValue<FrontierCompatibilityMode> FRONTIER_COMPATIBILITY;
    public static final ModConfigSpec.DoubleValue FRONTIER_SPREAD_SPEED;
    public static final ModConfigSpec.IntValue FRONTIER_EMBER_JUMP_DISTANCE;
    public static final ModConfigSpec.BooleanValue FRONTIER_EMBER_PARTICLES;
    public static final ModConfigSpec.IntValue FRONTIER_MAX_PARTICLE_ARCS_PER_TICK;
    public static final ModConfigSpec.IntValue FRONTIER_RESCAN_INTERVAL;
    public static final ModConfigSpec.IntValue FRONTIER_MAX_EVENTS_PER_TICK;
    public static final ModConfigSpec.IntValue FRONTIER_MAX_SOURCES_PER_TICK;
    public static final ModConfigSpec.IntValue FRONTIER_MAX_PENDING_EVENTS;

    public static final ModConfigSpec.BooleanValue HEAT_ENABLED;
    public static final ModConfigSpec.BooleanValue HEAT_ENTITY_DAMAGE;
    public static final ModConfigSpec.DoubleValue HEAT_ENTITY_HEATING_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEAT_RADIUS;
    public static final ModConfigSpec.DoubleValue HEAT_SOURCE_POWER;
    public static final ModConfigSpec.DoubleValue HEAT_DENSITY_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEAT_DAMAGE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue HEAT_DAMAGE_DOSE;
    public static final ModConfigSpec.BooleanValue HEAT_GLASS_SHATTERING;
    public static final ModConfigSpec.IntValue HEAT_GLASS_RADIUS;
    public static final ModConfigSpec.DoubleValue HEAT_GLASS_HEATING_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEAT_GLASS_BREAK_DELTA;
    public static final ModConfigSpec.IntValue HEAT_MAX_GLASS_CHECKS_PER_TICK;
    public static final ModConfigSpec.IntValue HEAT_MAX_TRACKED_FIRES;
    public static final ModConfigSpec.BooleanValue HEAT_SMOKE;
    public static final ModConfigSpec.BooleanValue HEAT_SMOKE_BLINDNESS;
    public static final ModConfigSpec.DoubleValue HEAT_SMOKE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue HEAT_SMOKE_BLINDNESS_THRESHOLD;
    public static final ModConfigSpec.IntValue HEAT_MAX_SMOKE_PARTICLES;

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
                        "VANILLA     - Mojang's stock fuel values; heat remains independently configurable.",
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
                        "Destructive fuel behavior. These options affect only removals caused by fire.")
                .push("destruction");

        BURN_CHESTS = builder
                .comment("Let wooden chests and trapped chests catch fire. Enabled by default.")
                .define("burn_chests", true);

        DESTROY_CHEST_CONTENTS = builder
                .comment("When a chest burns, destroy its inventory instead of dropping it.",
                        "Set false to keep the destructive fire but spill the stored items.")
                .define("destroy_chest_contents", true);

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
                .defineEnum("engine", FireEngineMode.FRONTIER);

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

        FRONTIER_EMBER_PARTICLES = builder
                .comment("Show successful FRONTIER jumps as a short arc of vanilla ember particles.",
                        "Uses built-in client assets, so players still do not need this mod.")
                .define("frontier_ember_particles", true);

        FRONTIER_MAX_PARTICLE_ARCS_PER_TICK = builder
                .comment("Maximum successful ember-jump arcs sent per level per game tick.",
                        "Each arc is five nearby-player particle packets; excess visuals are",
                        "dropped without delaying or changing fire simulation.")
                .defineInRange("frontier_max_particle_arcs_per_tick", 8, 0, 256);

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

        builder.comment(
                        "Radiant heat from groups of active fire blocks. The model uses incident",
                        "heat flux and accumulated exposure: a lone fireplace remains tolerable,",
                        "while a dense structure fire becomes dangerous from several blocks away.")
                .push("heat");

        HEAT_ENABLED = builder
                .comment("Master switch for radiant entity heat and thermal glass failure.")
                .define("enabled", true);

        HEAT_ENTITY_DAMAGE = builder
                .comment("Allow accumulated radiant exposure to ignite living entities.",
                        "Vanilla fire immunity, Fire Resistance, damage hooks, and water still apply.")
                .define("damage_entities", true);

        HEAT_ENTITY_HEATING_MULTIPLIER = builder
                .comment("Gameplay multiplier for radiant exposure applied to entities.",
                        "1.0 uses the reference calibration; the default 4.0 lets players feel",
                        "a structure fire from farther away without changing the inverse-square model.")
                .defineInRange("entity_heating_multiplier", 4.0, 0.1, 20.0);

        HEAT_RADIUS = builder
                .comment("Maximum loaded-world radius, in blocks, used for entity heat queries.")
                .defineInRange("radius", 12.0, 2.0, 24.0);

        HEAT_SOURCE_POWER = builder
                .comment("Effective radiative power per fresh fire block, in kilowatts.",
                        "12.5 is gameplay-calibrated so one non-contact fire is tolerable while",
                        "compact groups become dangerous. This is not literal fuel heat release.")
                .defineInRange("source_radiative_power_kw", 12.5, 1.0, 100.0);

        HEAT_DENSITY_MULTIPLIER = builder
                .comment("Maximum per-flame radiative-power boost inside a dense four-block cell.",
                        "Total heat already rises with fire count; this smaller boost represents",
                        "mutual heating and is approached smoothly rather than as a hard step.")
                .defineInRange("dense_fire_power_multiplier", 1.5, 1.0, 4.0);

        HEAT_DAMAGE_THRESHOLD = builder
                .comment("Incident heat flux that starts accumulating harmful dose, in kW/m^2.",
                        "2.5 follows the lower bound commonly used by fire tenability models.")
                .defineInRange("damage_threshold_kw_m2", 2.5, 0.5, 50.0);

        HEAT_DAMAGE_DOSE = builder
                .comment("Radiant dose required for one two-second ignition pulse.",
                        "Dose units are (kW/m^2)^(4/3) * minutes. The default 0.45 compresses",
                        "exposure into Minecraft fire lifetimes; 1.33 is the reference pain dose.")
                .defineInRange("damage_dose", 0.45, 0.1, 20.0);

        HEAT_GLASS_SHATTERING = builder
                .comment("Allow radiant heat to shatter ordinary glass blocks and panes.",
                        "Claim adapters are checked; unsafe direct destruction fails closed.")
                .define("shatter_glass", true);

        HEAT_GLASS_RADIUS = builder
                .comment("Maximum loaded-world distance searched for thermally stressed glass.")
                .defineInRange("glass_radius", 8, 1, 12);

        HEAT_GLASS_HEATING_MULTIPLIER = builder
                .comment("Gameplay time-compression applied to the real thermal-gradient model.",
                        "1.0 follows the reference glazing times; the default 8.0 makes windows",
                        "fail during Minecraft's much shorter village-house fires.")
                .defineInRange("glass_heating_multiplier", 8.0, 0.1, 50.0);

        HEAT_GLASS_BREAK_DELTA = builder
                .comment("Modelled center-to-edge temperature difference that fractures glass, C.",
                        "60 C is representative ordinary soda-lime glazing, not reinforced glass.")
                .defineInRange("glass_break_delta_c", 60.0, 10.0, 300.0);

        HEAT_MAX_GLASS_CHECKS_PER_TICK = builder
                .comment("Hard per-level block-read budget for glass discovery and thermal updates.")
                .defineInRange("max_glass_checks_per_tick", 256, 0, 16_384);

        HEAT_MAX_TRACKED_FIRES = builder
                .comment("Hard per-level cap on fire positions retained by the heat index.")
                .defineInRange("max_tracked_fires", 100_000, 256, 2_000_000);

        HEAT_SMOKE = builder
                .comment("Create server-driven smoke haze around exposed players.",
                        "Uses only vanilla particles, so connecting clients need no mod or assets.")
                .define("smoke_haze", true);

        HEAT_SMOKE_BLINDNESS = builder
                .comment("Apply a short hidden Blindness effect in severe indoor smoke.",
                        "It clears about 1.5 seconds after the player reaches cleaner air.")
                .define("smoke_blindness", true);

        HEAT_SMOKE_THRESHOLD = builder
                .comment("Smoke exposure at which visible haze begins. Indoor spaces concentrate",
                        "exposure while open-sky smoke disperses; lower values are smokier.")
                .defineInRange("smoke_threshold", 0.75, 0.1, 50.0);

        HEAT_SMOKE_BLINDNESS_THRESHOLD = builder
                .comment("Indoor smoke exposure at which vision becomes severely obscured.")
                .defineInRange("smoke_blindness_threshold", 5.0, 0.5, 100.0);

        HEAT_MAX_SMOKE_PARTICLES = builder
                .comment("Maximum vanilla smoke particles emitted per exposed player per sample.",
                        "Smoke is sampled once per second; zero keeps vision effects but hides particles.")
                .defineInRange("max_smoke_particles_per_player", 12, 0, 128);

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
