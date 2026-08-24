package dev.boredhero.conflagration.optimization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModVersionRulesTest {

    @Test
    void ftbChunksOnlyOwnsFireSpreadFrom2101_1_15() {
        assertFalse(ModVersionRules.ftbChunksOwnsFireSpread("2101.1.1"));
        assertFalse(ModVersionRules.ftbChunksOwnsFireSpread("2101.1.14"));
        assertTrue(ModVersionRules.ftbChunksOwnsFireSpread("2101.1.15"));
        assertTrue(ModVersionRules.ftbChunksOwnsFireSpread("2101.1.21"));
        assertTrue(ModVersionRules.ftbChunksOwnsFireSpread("2101.2.0"));
        assertTrue(ModVersionRules.ftbChunksOwnsFireSpread("unknown"));
    }
}
