package com.sandbox.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free, thread-safe high-frequency metrics collector (Fase 1).
 *
 * <p>Concurrency design:
 * <ul>
 *   <li>{@link LongAdder} for the global counter: internally striped, far
 *       less contention than {@code AtomicLong} under many-thread writes
 *       (JMM: happens-before guaranteed via read/write barriers).</li>
 *   <li>{@link ConcurrentHashMap} of category -> {@link LongAdder}:
 *       {@code computeIfAbsent} is atomic w.r.t. mapping creation and
 *       {@code increment()} is lock-free. No synchronized blocks, no
 *       read/write locks, no data race: every visible update is a
 *       consistent single-cell write.</li>
 *   <li>{@link #getCategoryCounts()} returns a defensive snapshot
 *       (weakly consistent per CHM spec), so caller mutations never
 *       leak back into the collector.</li>
 * </ul>
 *
 * <p>All operations are O(1) expected time.
 */
public class MetricsCollector {

    private final LongAdder totalProcessedOrders = new LongAdder();
    private final ConcurrentHashMap<String, LongAdder> categoryCounts = new ConcurrentHashMap<>();

    /**
     * Records one order under the given category.
     *
     * @param category category label; {@code null} is ignored (deterministic no-op)
     */
    public void recordOrder(String category) {
        if (category == null) {
            return;
        }
        totalProcessedOrders.increment();
        // Atomic creation + increment: no window where the category exists
        // but its counter is absent.
        categoryCounts.computeIfAbsent(category, key -> new LongAdder()).increment();
    }

    /** Total orders recorded so far (exactly-once per call to {@link #recordOrder}). */
    public long getTotalProcessedOrders() {
        return totalProcessedOrders.sum();
    }

    /**
     * Defensive snapshot of per-category counts at call time.
     * Mutating the returned map does not affect the collector.
     */
    public Map<String, Long> getCategoryCounts() {
        final Map<String, Long> snapshot = new HashMap<>(categoryCounts.size());
        for (final Map.Entry<String, LongAdder> entry : categoryCounts.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().sum());
        }
        return snapshot;
    }
}