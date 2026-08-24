package dev.boredhero.conflagration.config;

import dev.boredhero.conflagration.integration.ClaimProtectionMode;
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
                        "Both numbers are clamped to 0-100. Setting both to 0 makes the category",
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
