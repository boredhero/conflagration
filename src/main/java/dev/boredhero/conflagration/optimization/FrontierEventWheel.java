package dev.boredhero.conflagration.optimization;

/**
 * Allocation-free-after-growth timing wheel for frontier ignition arrivals.
 *
 * <p>Events are stored as parallel primitive arrays. Delays may span multiple wheel rotations;
 * absolute due ticks distinguish them. A partially drained bucket remains the next bucket visited,
 * so a deterministic budget postpones work without dropping or reordering it.
 */
final class FrontierEventWheel {

    static final int WHEEL_SIZE = 2048;
    private static final int MASK = WHEEL_SIZE - 1;

    private final Bucket[] buckets = new Bucket[WHEEL_SIZE];
    private long lastDrainedTick;
    private int size;

    FrontierEventWheel(long currentTick) {
        this.lastDrainedTick = currentTick - 1;
    }

    int size() {
        return size;
    }

    void offer(long dueTick, long source, long target, int sourceAge) {
        int index = (int) dueTick & MASK;
        Bucket bucket = buckets[index];
        if (bucket == null) {
            bucket = buckets[index] = new Bucket();
        }
        bucket.add(dueTick, source, target, sourceAge);
        size++;
    }

    int drainDue(long now, int budget, EventConsumer consumer) {
        if (budget <= 0 || size == 0) {
            return 0;
        }

        long first = lastDrainedTick + 1;
        if (now - first >= WHEEL_SIZE) {
            first = now - WHEEL_SIZE + 1;
        }

        int processed = 0;
        for (long tick = first; tick <= now && processed < budget; tick++) {
            Bucket bucket = buckets[(int) tick & MASK];
            if (bucket == null || bucket.size == 0) {
                lastDrainedTick = tick;
                continue;
            }

            DrainResult result = bucket.drain(now, budget - processed, consumer);
            processed += result.processed;
            size -= result.processed;
            if (result.dueWorkRemains) {
                lastDrainedTick = tick - 1;
                break;
            }
            lastDrainedTick = tick;
        }
        return processed;
    }

    @FunctionalInterface
    interface EventConsumer {
        void accept(long dueTick, long source, long target, int sourceAge);
    }

    private record DrainResult(int processed, boolean dueWorkRemains) {
    }

    private static final class Bucket {
        private long[] due = new long[8];
        private long[] source = new long[8];
        private long[] target = new long[8];
        private int[] age = new int[8];
        private int size;

        void add(long dueTick, long sourcePos, long targetPos, int sourceAge) {
            ensureCapacity(size + 1);
            due[size] = dueTick;
            source[size] = sourcePos;
            target[size] = targetPos;
            age[size] = sourceAge;
            size++;
        }

        DrainResult drain(long now, int budget, EventConsumer consumer) {
            int written = 0;
            int processed = 0;
            boolean dueWorkRemains = false;

            for (int i = 0; i < size; i++) {
                if (due[i] <= now && processed < budget) {
                    consumer.accept(due[i], source[i], target[i], age[i]);
                    processed++;
                } else {
                    if (due[i] <= now) {
                        dueWorkRemains = true;
                    }
                    due[written] = due[i];
                    source[written] = source[i];
                    target[written] = target[i];
                    age[written] = age[i];
                    written++;
                }
            }
            size = written;
            return new DrainResult(processed, dueWorkRemains);
        }

        private void ensureCapacity(int required) {
            if (required <= due.length) {
                return;
            }
            int capacity = due.length << 1;
            due = java.util.Arrays.copyOf(due, capacity);
            source = java.util.Arrays.copyOf(source, capacity);
            target = java.util.Arrays.copyOf(target, capacity);
            age = java.util.Arrays.copyOf(age, capacity);
        }
    }
}
