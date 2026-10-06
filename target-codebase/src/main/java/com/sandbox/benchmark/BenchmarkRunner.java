package com.sandbox.benchmark;

import com.sandbox.service.MetricsCollector;
import com.sandbox.service.OrderProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Container entrypoint: runs the high-frequency pipeline benchmark and prints a
 * deterministic JSON summary (one line, stable field order).
 *
 * <p>Exit code is 0 on success, 1 on integrity failure.
 */
public final class BenchmarkRunner {

    private BenchmarkRunner() {}

    public static void main(String[] args) throws Exception {
        final int orders = parseEnv("BENCHMARK_ORDERS", 100_000);
        final int threads = parseEnv("BENCHMARK_THREADS", 8);

        // Phase 1: high-frequency generation (O(N)).
        final long t0 = System.nanoTime();
        final List<String> raw = new ArrayList<>(orders);
        for (int i = 0; i < orders; i++) {
            raw.add("ORDER_" + i);
        }
        final long genMs = (System.nanoTime() - t0) / 1_000_000L;

        // Phase 2: O(N) deduplication.
        final OrderProcessor processor = new OrderProcessor();
        final long t1 = System.nanoTime();
        final List<String> unique = processor.filterUniqueOrders(raw);
        final long filterMs = (System.nanoTime() - t1) / 1_000_000L;

        // Phase 3: concurrent metrics (thread-safe collector).
        final MetricsCollector collector = new MetricsCollector();
        final int perThread = orders / threads;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch latch = new CountDownLatch(threads);
        final long t2 = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            final int base = t * perThread;
            pool.submit(() -> {
                try {
                    for (int i = base; i < base + perThread; i++) {
                        collector.recordOrder("CAT_" + (i % 10));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(60, TimeUnit.SECONDS);
        pool.shutdown();
        final long metricsMs = (System.nanoTime() - t2) / 1_000_000L;

        final long totalMs = genMs + filterMs + metricsMs;
        final String json = String.format(
            "{\"status\":\"SUCCESS\",\"orders\":%d,\"unique\":%d,\"total_processed\":%d,"
            + "\"gen_ms\":%d,\"filter_ms\":%d,\"metrics_ms\":%d,\"total_ms\":%d}",
            orders, unique.size(), collector.getTotalProcessedOrders(),
            genMs, filterMs, metricsMs, totalMs
        );
        System.out.println(json);

        if (unique.size() != orders) {
            System.err.println("Benchmark integrity check failed");
            System.exit(1);
        }
    }

    private static int parseEnv(String key, int def) {
        final String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}