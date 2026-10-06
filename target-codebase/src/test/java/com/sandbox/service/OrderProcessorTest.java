package com.sandbox.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Test suite for {@link OrderProcessor}.
 *
 * <p>Validates:
 * <ul>
 *   <li>correctness (dedup + insertion order + null handling)</li>
 *   <li>performance: O(N) dedup of 100k/1M elements well within the budget</li>
 * </ul>
 */
class OrderProcessorTest {

    private final OrderProcessor processor = new OrderProcessor();

    @Test
    @DisplayName("O(N) dedup of 100,000 unique elements must finish < 500 ms")
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
    void filterUniqueOrders_100k_unique_elements_within_500ms() {
        final int n = 100_000;
        final List<String> rawOrders = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rawOrders.add("ORDER_" + i);
        }

        final List<String> result = processor.filterUniqueOrders(rawOrders);

        assertThat(result).hasSize(n);
        for (int i = 0; i < n; i++) {
            assertThat(result.get(i)).as("element at position %d", i).isEqualTo("ORDER_" + i);
        }
    }

    @Test
    @DisplayName("O(N) dedup of 1,000,000 elements (stress) < 2 s")
    @Timeout(value = 2_000, unit = TimeUnit.MILLISECONDS)
    void filterUniqueOrders_1M_stress_within_2s() {
        final int n = 1_000_000;
        final List<String> rawOrders = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rawOrders.add("ORDER_" + i);
        }

        final List<String> result = processor.filterUniqueOrders(rawOrders);

        assertThat(result).hasSize(n);
    }

    @Test
    void filterUniqueOrders_dedupes_preserving_first_seen_order() {
        final List<String> result = processor.filterUniqueOrders(
                List.of("A", "B", "A", "C", "B", "D"));
        assertThat(result).containsExactly("A", "B", "C", "D");
    }

    @Test
    void filterUniqueOrders_heavy_dups_200k_to_100k_unique() {
        final int unique = 100_000;
        final List<String> raw = new ArrayList<>(unique * 2);
        for (int i = 0; i < unique * 2; i++) {
            raw.add("ORDER_" + (i % unique));
        }
        final List<String> result = processor.filterUniqueOrders(raw);
        assertThat(result).hasSize(unique);
        assertThat(result.get(0)).isEqualTo("ORDER_0");
        assertThat(result.get(unique - 1)).isEqualTo("ORDER_" + (unique - 1));
    }

    @Test
    void filterUniqueOrders_single_null_preserved_once() {
        final List<String> input = new ArrayList<>();
        input.add(null);
        input.add("A");
        input.add(null);
        input.add("B");

        final List<String> result = processor.filterUniqueOrders(input);
        assertThat(result).containsExactly(null, "A", "B");
    }

    @Test
    void filterUniqueOrders_null_input_returns_empty() {
        assertThat(processor.filterUniqueOrders(null)).isEmpty();
    }

    @Test
    void filterUniqueOrders_empty_input_returns_empty() {
        assertThat(processor.filterUniqueOrders(List.of())).isEmpty();
    }
}