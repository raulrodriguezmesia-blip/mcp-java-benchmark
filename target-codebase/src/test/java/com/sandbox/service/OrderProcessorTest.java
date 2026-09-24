package com.sandbox.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Clase de prueba para OrderProcessor.
 */
class OrderProcessorTest {

    private final OrderProcessor processor = new OrderProcessor();

    /**
     * Prueba de rendimiento para el m?todo filterUniqueOrders con 100,000 elementos.
     * Se espera que falle con el c?digo actual debido al cuello de botella O(N^2).
     */
    @Test
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
    void testFilterUniqueOrdersPerformance() {
        // Crear una lista de 100,000 elementos ?nicos (peor caso para el algoritmo O(N^2))
        List<String> rawOrders = new ArrayList<>(100000);
        for (int i = 0; i < 100000; i++) {
            rawOrders.add("ORDER_" + i);
        }

        // Este m?todo deber?a tardar m?s de 500ms debido al O(N^2)
        List<String> result = processor.filterUniqueOrders(rawOrders);

        // Verificar que el resultado es correcto (todos los elementos ?nicos, mismo orden)
        assertEquals(100000, result.size(), "Deber?a tener 100,000 elementos ?nicos");
        for (int i = 0; i < 100000; i++) {
            assertEquals("ORDER_" + i, result.get(i), "El elemento en la posici?n " + i + " deber?a be ORDER_" + i);
        }
    }

    // Una prueba adicional para verificar el funcionamiento correcto con un conjunto peque?o
    @Test
    void testFilterUniqueOrdersSmallSet() {
        List<String> rawOrders = List.of("A", "B", "A", "C", "B", "D");
        List<String> expected = List.of("A", "B", "C", "D");
        List<String> result = processor.filterUniqueOrders(rawOrders);
        assertEquals(expected, result);
    }
}
