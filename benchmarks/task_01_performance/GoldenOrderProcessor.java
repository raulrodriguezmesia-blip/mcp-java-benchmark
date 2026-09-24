package com.sandbox.service;

import java.util.*;

/**
 * Golden Reference Implementation for Task 01 Performance.
 * Replaces O(N^2) list search with O(N) LinkedHashSet deduplication
 * while strictly preserving insertion order.
 */
public class GoldenOrderProcessor {

    public List<String> filterUniqueOrders(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // O(N) complexity using LinkedHashSet to maintain insertion order
        Set<String> uniqueOrders = new LinkedHashSet<>(orderIds);
        return new ArrayList<>(uniqueOrders);
    }
}
