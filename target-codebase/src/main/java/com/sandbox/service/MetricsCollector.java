package com.sandbox.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollector {

    private final AtomicLong totalProcessedOrders = new AtomicLong();
    private final ConcurrentHashMap<String, Long> categoryCounts = new ConcurrentHashMap<>();

    public void recordOrder(String category) {
        if (category == null) return;
        
        // Operación atómica incrementando el contador total
        totalProcessedOrders.incrementAndGet();

        // ConcurrentHashMap es thread-safe y merge es atómico
        categoryCounts.merge(category, 1L, Long::sum);
    }

    public long getTotalProcessedOrders() {
        return totalProcessedOrders.get();
    }

    public Map<String, Long> getCategoryCounts() {
        return new HashMap<>(categoryCounts);
    }
}