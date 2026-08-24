package dev.boredhero.conflagration.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlammabilityPolicyTest {

    private static Set<FuelCategory> of(FuelCategory... categories) {
        return categories.length == 0 ? EnumSet.noneOf(FuelCategory.class) : EnumSet.of(categories[0], categories);
    }

    @Test
    @DisplayName("a plain block resolves to its category's preset value")
    void resolvesFromCategory() {
        FlammabilityPolicy policy = FlammabilityPolicy.builder(FirePreset.AGGRESSIVE).build();

        Optional<Odds> odds = policy.resolve("minecraft:oak_log", of(FuelCategory.LOGS));

        assertEquals(Optional.of(FirePreset.AGGRESSIVE.get(FuelCategory.LOGS)), odds);
    }

    @Test
    @DisplayName("an uncategorised block is left alone")
    void ignoresUncategorisedBlocks() {
        FlammabilityPolicy policy = FlammabilityPolicy.builder(FirePreset.AGGRESSIVE).build();

        assertTrue(policy.resolve("minecraft:stone", of()).isEmpty());
    }

    @Test
    @DisplayName("a per-block override beats the category table")
    void overrideBeatsCategory() {
        FlammabilityPolicy policy = FlammabilityPolicy.builder(FirePreset.AGGRESSIVE)
                .overrides(List.of("minecraft:oak_log=99,1"))
                .build();

        assertEquals(Optional.of(new Odds(99, 1)),
                policy.resolve("minecraft:oak_log", of(FuelCategory.LOGS)));
        // Other logs still follow the preset.
        assertEquals(Optional.of(FirePreset.AGGRESSIVE.get(FuelCategory.LOGS)),
                policy.resolve("minecraft:birch_log", of(FuelCategory.LOGS)));
    }

    @Test
    @DisplayName("the blacklist beats everything, including an explicit override")
    void blacklistBeatsOverride() {
        FlammabilityPolicy policy = FlammabilityPolicy.builder(FirePreset.INFERNO)
                .overrides(List.of("minecraft:oak_log=99,1"))
                .blacklist(List.of("minecraft:oak_log"))
                .build();

        assertTrue(policy.resolve("minecraft:oak_log", of(FuelCategory.LOGS)).isEmpty(),
                "a blacklisted block must never be modified");
    }

    @Test
    @DisplayName("category precedence follows enum declaration order, not argument order")
    void categoryPrecedenceIsDeterministic() {
        FlammabilityPolicy policy = FlammabilityPolicy.builder(FirePreset.AGGRESSIVE).build();

        // A bamboo block plausibly matches both BAMBOO and PLANKS in some packs.
        // LOGS/BAMBOO are declared before PLANKS, so BAMBOO must win regardless of set order.
        Optional<Odds> a = policy.resolve("minecraft:bamboo_block", of(FuelCategory.BAMBOO, FuelCategory.PLANKS));
        Optional<Odds> b = policy.resolve("minecraft:bamboo_block", of(FuelCategory.PLANKS, FuelCategory.BAMBOO));

        assertEquals(a, b, "resolution must not depend on the order categories are supplied in");
        assertEquals(Optional.of(FirePreset.AGGRESSIVE.get(FuelCategory.BAMBOO)), a);
    }

    @Test
    @DisplayName("ids are normalized for case and an omitted minecraft namespace")
    void normalisesIdsOnLookup() {
        FlammabilityPolicy policy = FlammabilityPolicy.builder(FirePreset.AGGRESSIVE)
                .overrides(List.of("oak_log=99,1"))
                .build();

        assertEquals(Optional.of(new Odds(99, 1)),
                policy.resolve("minecraft:oak_log", of(FuelCategory.LOGS)));
        assertEquals(Optional.of(new Odds(99, 1)),
                policy.resolve("Minecraft:Oak_Log", of(FuelCategory.LOGS)));
    }

    @Test
    @DisplayName("malformed config entries are collected as warnings, not thrown")
    void collectsWarningsForBadEntries() {
        FlammabilityPolicy policy = FlammabilityPolicy.builder(FirePreset.AGGRESSIVE)
                .overrides(List.of("minecraft:oak_log=80,10", "this is not valid", "also:bad=x,y"))
                .blacklist(List.of("minecraft:stone", "NOT A BLOCK"))
                .build();

        assertEquals(3, policy.warnings().size(), policy.warnings().toString());
        // The good entries still applied.
        assertEquals(Optional.of(new Odds(80, 10)),
                policy.resolve("minecraft:oak_log", of(FuelCategory.LOGS)));
        assertTrue(policy.blacklist().contains("minecraft:stone"));
    }

    @Test
    @DisplayName("CUSTOM lets a single category be overridden without touching the others")
    void customCategoryOverride() {
        FlammabilityPolicy policy = FlammabilityPolicy.builder(FirePreset.CUSTOM)
                .category(FuelCategory.LOGS, new Odds(11, 22))
                .build();

        assertEquals(new Odds(11, 22), policy.forCategory(FuelCategory.LOGS));
        assertEquals(FirePreset.CUSTOM.get(FuelCategory.LEAVES), policy.forCategory(FuelCategory.LEAVES));
    }

    @Test
    @DisplayName("a vanilla preset with no overrides is a no-op")
    void vanillaWithoutOverridesIsNoOp() {
        assertTrue(FlammabilityPolicy.builder(FirePreset.VANILLA).build().isNoOp());
        assertFalse(FlammabilityPolicy.builder(FirePreset.VANILLA)
                .overrides(List.of("minecraft:stone=50,50"))
                .build().isNoOp());
        assertFalse(FlammabilityPolicy.builder(FirePreset.AGGRESSIVE).build().isNoOp());
    }

    @Test
    @DisplayName("VANILLA plus an override leaves every other category alone")
    void vanillaWithOverrideOnlyTouchesOverride() {
        FlammabilityPolicy policy = FlammabilityPolicy.builder(FirePreset.VANILLA)
                .overrides(List.of("minecraft:oak_log=99,1"))
                .build();

        assertEquals(Optional.of(new Odds(99, 1)),
                policy.resolve("minecraft:oak_log", of(FuelCategory.LOGS)));
        assertTrue(policy.resolve("minecraft:birch_log", of(FuelCategory.LOGS)).isEmpty());
    }
}
