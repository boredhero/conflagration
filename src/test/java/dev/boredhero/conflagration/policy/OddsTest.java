package dev.boredhero.conflagration.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OddsTest {

    @ParameterizedTest(name = "({0},{1}) clamps to ({2},{3})")
    @CsvSource({
            "  0,   0,   0,   0",
            " 50,  50,  50,  50",
            "100, 100, 100, 100",
            "101, 200, 101, 200",
            "301, 500, 300, 300",
            " -1,  -5,   0,   0",
            "  5,  20,   5,  20",
    })
    @DisplayName("values are clamped into [0,300]")
    void clampsOutOfRangeValues(int ignite, int burn, int expectedIgnite, int expectedBurn) {
        Odds odds = new Odds(ignite, burn);
        assertEquals(expectedIgnite, odds.ignite());
        assertEquals(expectedBurn, odds.burn());
    }

    @Test
    @DisplayName("INERT is zero/zero and reports itself as inert")
    void inertIsInert() {
        assertTrue(Odds.INERT.isInert());
        assertEquals(0, Odds.INERT.ignite());
        assertEquals(0, Odds.INERT.burn());
    }

    @Test
    @DisplayName("a block that catches but does not burn away is not inert")
    void partialValuesAreNotInert() {
        assertFalse(new Odds(30, 0).isInert());
        assertFalse(new Odds(0, 30).isInert());
    }
}
