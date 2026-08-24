package dev.boredhero.conflagration.optimization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FireDropSuppressionTest {

    @Test
    void supportsNestedFireRemovalScopesAndCleansUp() {
        assertFalse(FireDropSuppression.active());
        FireDropSuppression.enter();
        FireDropSuppression.enter();
        assertTrue(FireDropSuppression.active());
        FireDropSuppression.exit();
        assertTrue(FireDropSuppression.active());
        FireDropSuppression.exit();
        assertFalse(FireDropSuppression.active());
        assertThrows(IllegalStateException.class, FireDropSuppression::exit);
    }
}
