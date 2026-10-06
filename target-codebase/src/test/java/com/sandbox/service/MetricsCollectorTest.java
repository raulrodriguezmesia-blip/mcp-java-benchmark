package com.sandbox.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Thread-safety test suite for {@link MetricsCollector}.
 * Simulates high-frequency, high-contention writes and asserts zero data loss.
 */
public class MetricsCollectorTest {

    @Test
    @DisplayName("16 threads x 50k writes -> zero loss in total and category counters")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void concurrentRecordOrder_zero_data_loss() throws InterruptedException {
        final MetricsCollector collector = new MetricsCollector();
        final int threadCount = 16;
        final int operationsPerThread = 50_000;
        final long expectedTotal = (long) threadCount * operationsPerThread;

        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // all threads start simultaneously (max contention)
                    for (int j = 0; j < operationsPerThread; j++) {
                        collector.recordOrder("ELECTRONICS");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(collector.getTotalProcessedOrders())
                .as("total orders (no lost updates)")
                .isEqualTo(expectedTotal);
        assertThat(collector.getCategoryCounts().get("ELECTRONICS"))
                .as("category counter (no lost updates)")
                .isEqualTo(expectedTotal);
    }

    @Test
    @DisplayName("8 threads x 3 categories -> per-category totals exact")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void concurrentMixedCategories_exact_per_category_totals() throws InterruptedException {
        final MetricsCollector collector = new MetricsCollector();
        final int threadCount = 8;
        final String[] categories = {"ELECTRONICS", "APPLIANCES", "GARDENING"};
        final int opsPerCategory = 25_000;
        final long expectedPerCategory = (long) threadCount * opsPerCategory;

        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (final String cat : categories) {
                        for (int j = 0; j < opsPerCategory; j++) {
                            collector.recordOrder(cat);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        final Map<String, Long> counts = collector.getCategoryCounts();
        assertThat(counts).hasSize(categories.length);
        for (final String category : categories) {
            assertThat(counts.get(category)).isEqualTo(expectedPerCategory);
        }
        assertThat(collector.getTotalProcessedOrders())
                .isEqualTo(expectedPerCategory * categories.length);
    }

    @Test
    void nullCategory_is_ignored_deterministically() {
        final MetricsCollector collector = new MetricsCollector();
        collector.recordOrder(null);
        assertThat(collector.getTotalProcessedOrders()).isZero();
        assertThat(collector.getCategoryCounts()).isEmpty();
    }

    @Test
    void categorySnapshot_is_defensive_copy() {
        final MetricsCollector collector = new MetricsCollector();
        collector.recordOrder("ELECTRONICS");

        final Map<String, Long> snapshot = collector.getCategoryCounts();
        snapshot.put("ELECTRONICS", 999L); // mutate the snapshot
        snapshot.put("INJECTED", 1L);      // add a key in the snapshot

        assertThat(collector.getCategoryCounts().get("ELECTRONICS")).isEqualTo(1L);
        assertThat(collector.getCategoryCounts()).doesNotContainKey("INJECTED");
    }

    @Test
    @DisplayName("Fresh collector reports zero total processed orders")
    void totalProcessedOrders_is_zero_on_empty() {
        final MetricsCollector collector = new MetricsCollector();
        assertThat(collector.getTotalProcessedOrders()).isZero();
        assertThat(collector.getCategoryCounts()).isEmpty();
    }

    @Test
    @DisplayName("Snapshot mutation isolation under concurrent writes")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void categorySnapshot_mutationIsolation_underConcurrency() throws InterruptedException {
        final MetricsCollector collector = new MetricsCollector();
        final int threadCount = 8;
        final int opsPerThread = 10_000;
        final long expectedTotal = (long) threadCount * opsPerThread;

        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final CountDownLatch snapshotTaken = new CountDownLatch(1);

        // Writer threads flood the collector.
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < opsPerThread; j++) {
                        collector.recordOrder("CAT_" + (j % 4));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        // A concurrent reader takes a snapshot and mutates it while writers run.
        final Thread reader = new Thread(() -> {
            try {
                startGate.await();
                snapshotTaken.countDown();
                final Map<String, Long> snap = collector.getCategoryCounts();
                // Attempt to mutate the snapshot — must not leak into the collector.
                snap.put("INJECTED", -1L);
                snap.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        reader.start();

        startGate.countDown();
        assertThat(snapshotTaken.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        reader.interrupt();

        // After all writers finish, the internal map must be clean and contain
        // only the real categories with no INJECTED key.
        final Map<String, Long> finalCounts = collector.getCategoryCounts();
        assertThat(finalCounts).doesNotContainKey("INJECTED");
        assertThat(finalCounts).containsOnlyKeys("CAT_0", "CAT_1", "CAT_2", "CAT_3");
        assertThat(collector.getTotalProcessedOrders()).isEqualTo(expectedTotal);
    }
}