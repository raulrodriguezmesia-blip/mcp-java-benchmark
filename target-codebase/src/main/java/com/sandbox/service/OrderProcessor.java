package com.sandbox.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * High-performance order processor (Fase 1 - deterministic O(N) pipeline).
 *
 * <p>{@link #filterUniqueOrders(List)} guarantees:
 * <ul>
 *   <li>O(N) time / O(N) space deduplication via {@link LinkedHashSet}
 *       (hash table with O(1) average add/contains + insertion order).</li>
 *   <li>Pre-sized initial capacity (n / loadFactor + 1) so no rehashing
 *       occurs even with collections of 100,000+ elements.</li>
 *   <li>Strict preservation of insertion order and null-element semantics
 *       (LinkedHashSet admits exactly one null, like the original
 *       ArrayList-based contract).</li>
 * </ul>
 *
 * <p>Public API contract: {@code filterUniqueOrders(List<String> rawOrders)}
 * returns a new list with only unique elements, in first-seen order.
 */
public class OrderProcessor {

    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * Filters duplicates from {@code rawOrders} preserving first-seen order.
     *
     * @param rawOrders input collection (may contain nulls); null/empty yields an empty list
     * @return a new list with only unique elements, in first-seen order
     */
    public List<String> filterUniqueOrders(List<String> rawOrders) {
        if (rawOrders == null || rawOrders.isEmpty()) {
            return new ArrayList<>(0);
        }

        // Pre-sized capacity: avoids rehashing at n >= 100k (would otherwise cost ~3 resizes).
        final int initialCapacity = (int) Math.ceil(rawOrders.size() / DEFAULT_LOAD_FACTOR) + 1;
        final Set<String> uniqueOrders = new LinkedHashSet<>(initialCapacity);

        for (final String order : rawOrders) {
            uniqueOrders.add(order);
        }

        return new ArrayList<>(uniqueOrders);
    }
}