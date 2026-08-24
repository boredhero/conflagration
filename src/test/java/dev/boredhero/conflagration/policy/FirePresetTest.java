package dev.boredhero.conflagration.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirePresetTest {

    @ParameterizedTest
    @EnumSource(FirePreset.class)
    @DisplayName("every preset defines every fuel category")
    void presetsAreComplete(FirePreset preset) {
        for (FuelCategory category : FuelCategory.values()) {
            assertNotNull(preset.get(category),
                    preset + " is missing a value for " + category);
        }
    }

    @Test
    @DisplayName("VANILLA reproduces Mojang's documented stock values")
    void vanillaMatchesStockValues() {
        // Verified against FireBlock's bootstrap table in 1.21.1.
        assertEquals(new Odds(5, 5), FirePreset.VANILLA.get(FuelCategory.LOGS));
        assertEquals(new Odds(5, 20), FirePreset.VANILLA.get(FuelCategory.PLANKS));
        assertEquals(new Odds(30, 60), FirePreset.VANILLA.get(FuelCategory.LEAVES));
        assertEquals(new Odds(30, 60), FirePreset.VANILLA.get(FuelCategory.WOOL));
        assertEquals(new Odds(60, 20), FirePreset.VANILLA.get(FuelCategory.CARPETS));
    }

    @Test
    @DisplayName("crops are inert in vanilla; making fields burn is an opt-in change")
    void cropsAreInertInVanilla() {
        assertTrue(FirePreset.VANILLA.get(FuelCategory.CROPS).isInert());
        assertFalse(FirePreset.AGGRESSIVE.get(FuelCategory.CROPS).isInert());
    }

    @ParameterizedTest
    @EnumSource(value = FirePreset.class, names = {"SMOULDERING", "AGGRESSIVE", "INFERNO"})
    @DisplayName("every non-vanilla preset raises log ignite odds above vanilla's 5")
    void presetsFixTheLogProblem(FirePreset preset) {
        // Vanilla logs at ignite 5 are the single reason trunks survive a canopy fire.
        int vanillaIgnite = FirePreset.VANILLA.get(FuelCategory.LOGS).ignite();
        assertTrue(preset.get(FuelCategory.LOGS).ignite() > vanillaIgnite,
                preset + " must raise log ignite odds above vanilla (" + vanillaIgnite + ")");
    }

    @ParameterizedTest
    @EnumSource(value = FirePreset.class, names = {"SMOULDERING", "AGGRESSIVE", "INFERNO"})
    @DisplayName("structural wood ignites more readily than it is consumed, so fires last")
    void structuralWoodBurnsSlowerThanItCatches(FirePreset preset) {
        // The core design rule: high ignite spreads fire, low burn keeps fuel around long
        // enough for the fire to level a building instead of flashing out.
        for (FuelCategory category : new FuelCategory[]{FuelCategory.LOGS, FuelCategory.PLANKS}) {
            Odds odds = preset.get(category);
            assertTrue(odds.ignite() > odds.burn(),
                    preset + "/" + category + " should have ignite > burn but was " + odds);
        }
    }

    @ParameterizedTest
    @EnumSource(FirePreset.class)
    @DisplayName("presets get strictly more aggressive in declared order")
    void presetsAreOrdered(FirePreset preset) {
        if (preset == FirePreset.CUSTOM) {
            return; // CUSTOM is a seed for user values, not a point on the intensity scale.
        }
        int logIgnite = preset.get(FuelCategory.LOGS).ignite();
        assertTrue(logIgnite >= FirePreset.VANILLA.get(FuelCategory.LOGS).ignite());
        assertTrue(logIgnite <= FirePreset.INFERNO.get(FuelCategory.LOGS).ignite());
    }

    @Test
    @DisplayName("only VANILLA reports itself as vanilla")
    void onlyVanillaIsVanilla() {
        assertTrue(FirePreset.VANILLA.isVanilla());
        assertFalse(FirePreset.AGGRESSIVE.isVanilla());
        assertFalse(FirePreset.INFERNO.isVanilla());
    }
}
