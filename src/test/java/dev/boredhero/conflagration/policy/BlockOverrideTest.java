package dev.boredhero.conflagration.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockOverrideTest {

    @Test
    @DisplayName("parses a fully qualified entry")
    void parsesQualifiedEntry() {
        BlockOverride.Result result = BlockOverride.parse("minecraft:oak_log=80,10");
        assertTrue(result.ok(), result.error());

        BlockOverride override = result.value().orElseThrow();
        assertEquals("minecraft:oak_log", override.blockId());
        assertEquals(new Odds(80, 10), override.odds());
    }

    @Test
    @DisplayName("an entry without a namespace defaults to minecraft")
    void defaultsNamespace() {
        BlockOverride override = BlockOverride.parse("oak_log=80,10").value().orElseThrow();
        assertEquals("minecraft:oak_log", override.blockId());
    }

    @Test
    @DisplayName("modded ids keep their own namespace")
    void keepsModdedNamespace() {
        BlockOverride override = BlockOverride.parse("create:andesite_casing=40,20").value().orElseThrow();
        assertEquals("create:andesite_casing", override.blockId());
    }

    @Test
    @DisplayName("surrounding whitespace is tolerated")
    void toleratesWhitespace() {
        BlockOverride override = BlockOverride.parse("  minecraft:oak_log =  80 , 10  ").value().orElseThrow();
        assertEquals("minecraft:oak_log", override.blockId());
        assertEquals(new Odds(80, 10), override.odds());
    }

    @Test
    @DisplayName("out of range values are clamped rather than rejected")
    void clampsRatherThanRejects() {
        BlockOverride override = BlockOverride.parse("minecraft:oak_log=500,-3").value().orElseThrow();
        assertEquals(new Odds(100, 0), override.odds());
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {
            "minecraft:oak_log",          // no '=' at all
            "minecraft:oak_log=80",       // missing the second value
            "minecraft:oak_log=a,b",      // non-numeric
            "=80,10",                     // no id
            "minecraft:=80,10",           // empty path
            ":oak_log=80,10",             // empty namespace
            "mine:craft:oak_log=80,10",   // two colons
            "minecraft:Oak Log=80,10",    // space and capitals in path
            "",
            "   ",
    })
    @DisplayName("malformed entries fail with a reason instead of throwing")
    void rejectsMalformedEntries(String raw) {
        BlockOverride.Result result = BlockOverride.parse(raw);
        assertFalse(result.ok(), "expected \"" + raw + "\" to be rejected");
        assertNotNull(result.error(), "a rejection must explain itself");
        assertFalse(result.error().isBlank());
    }

    @Test
    @DisplayName("a null entry is rejected, not a crash")
    void rejectsNull() {
        assertFalse(BlockOverride.parse(null).ok());
    }

    @Test
    @DisplayName("capitals in an id are normalised to lower case")
    void normalisesCase() {
        assertEquals("minecraft:oak_log", BlockOverride.normaliseId("Minecraft:Oak_Log"));
    }
}
