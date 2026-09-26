package com.sandbox.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class OrderProcessor {

    public List<String> filterUniqueOrders(List<String> rawOrders) {
        if (rawOrders == null || rawOrders.isEmpty()) {
            return new ArrayList<>();
        }

        // LinkedHashSet preserva el orden de inserción con búsquedas O(1)
        // y maneja correctamente los valores null (como el ArrayList original)
        Set<String> uniqueOrders = new LinkedHashSet<>(rawOrders.size());

        for (String order : rawOrders) {
            uniqueOrders.add(order);
        }

        return new ArrayList<>(uniqueOrders);
    }
}