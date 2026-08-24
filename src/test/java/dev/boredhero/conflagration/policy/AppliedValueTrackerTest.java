package dev.boredhero.conflagration.policy;

import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppliedValueTrackerTest {

    @Test
    void restoresValueStillOwnedByConflagration() {
        Object block = new Object();
        Map<Object, Odds> table = new IdentityHashMap<>();
        table.put(block, new Odds(5, 5));
        AppliedValueTracker<Object, Odds> tracker = new AppliedValueTracker<>();

        tracker.recordWrite(block, table.get(block), new Odds(35, 8));
        table.put(block, new Odds(35, 8));

        assertEquals(1, tracker.restore(table::get, table::put, (key, value) -> {}));
        assertEquals(new Odds(5, 5), table.get(block));
        assertEquals(0, tracker.managedCount());
    }

    @Test
    void preservesAndAdoptsALaterModsWrite() {
        Object block = new Object();
        Map<Object, Odds> table = new IdentityHashMap<>();
        table.put(block, new Odds(5, 5));
        AppliedValueTracker<Object, Odds> tracker = new AppliedValueTracker<>();

        tracker.recordWrite(block, table.get(block), new Odds(35, 8));
        table.put(block, new Odds(77, 12)); // another mod wrote after us

        assertEquals(0, tracker.restore(table::get, table::put, (key, value) -> {}));
        assertEquals(new Odds(77, 12), table.get(block));

        tracker.recordWrite(block, table.get(block), new Odds(70, 12));
        table.put(block, new Odds(70, 12));
        tracker.restore(table::get, table::put, (key, value) -> {});
        assertEquals(new Odds(77, 12), table.get(block), "the newer mod value becomes the baseline");
    }

    @Test
    void tracksKeysByIdentityRatherThanEquals() {
        String first = new String("same-id");
        String second = new String("same-id");
        AppliedValueTracker<String, Integer> tracker = new AppliedValueTracker<>();

        tracker.recordWrite(first, 1, 2);
        tracker.recordWrite(second, 10, 20);

        assertEquals(2, tracker.managedCount());
    }
}
