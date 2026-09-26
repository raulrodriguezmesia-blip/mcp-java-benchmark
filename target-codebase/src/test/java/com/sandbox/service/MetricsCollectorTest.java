package com.sandbox.service;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

public class MetricsCollectorTest {

    @Test
    public void testConcurrentRecordOrder() throws InterruptedException {
        MetricsCollector collector = new MetricsCollector();
        int threadCount = 10;
        int operationsPerThread = 10_000;
        int expectedTotal = threadCount * operationsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        collector.recordOrder("ELECTRONICS");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Este assert FALLARÁ en el código base debido a la condición de carrera
        assertEquals(expectedTotal, collector.getTotalProcessedOrders(), 
            "El total de órdenes procesadas no coincide debido a data race");
        
        assertEquals(expectedTotal, collector.getCategoryCounts().get("ELECTRONICS"), 
            "El conteo por categoría no es thread-safe");
    }
}