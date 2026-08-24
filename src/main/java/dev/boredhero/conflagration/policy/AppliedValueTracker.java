package dev.boredhero.conflagration.policy;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Tracks values temporarily owned by Conflagration and restores them without clobbering a later
 * writer. Keys use identity semantics, matching Minecraft's singleton block registry objects.
 */
public final class AppliedValueTracker<K, V> {

    private final Map<K, V> baseline = new IdentityHashMap<>();
    private final Map<K, V> lastApplied = new IdentityHashMap<>();

    public void recordWrite(K key, V currentValue, V writtenValue) {
        baseline.putIfAbsent(key, currentValue);
        lastApplied.put(key, writtenValue);
    }

    /**
     * Restores every value that still equals our last write. A different current value is treated
     * as a newer external write and adopted as the next baseline.
     *
     * @return number of values restored
     */
    public int restore(Function<K, V> reader,
                       BiConsumer<K, V> writer,
                       BiConsumer<K, V> externalWriteObserver) {
        int restored = 0;
        for (Map.Entry<K, V> entry : lastApplied.entrySet()) {
            K key = entry.getKey();
            V current = reader.apply(key);
            if (Objects.equals(current, entry.getValue())) {
                V original = baseline.get(key);
                if (original != null) {
                    writer.accept(key, original);
                    restored++;
                }
            } else {
                baseline.put(key, current);
                externalWriteObserver.accept(key, current);
            }
        }
        lastApplied.clear();
        return restored;
    }

    public int managedCount() {
        return lastApplied.size();
    }
}
