package com.sandbox.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Procesador de pedidos con un cuello de botella de rendimiento intencional.
 */
public class OrderProcessor {

    /**
     * Filtra los pedidos ?nicos de la lista de pedidos brutos.
     * <p>
     * Este m?todo tiene un cuello de botella de rendimiento O(N^2) porque para cada elemento,
     * verifica si ya est? en la lista de resultados iterando sobre toda la lista de resultados.
     * En el peor caso (todos los elementos ?nicos), esto resulta en aproximadamente N^2/2 operaciones.
     *
     * @param rawOrders Lista de pedidos brutos (puede contener duplicados)
     * @return Lista de pedidos ?nicos en el orden de su primera aparici?n
     */
    public List<String> filterUniqueOrders(List<String> rawOrders) {
        List<String> uniqueOrders = new ArrayList<>();
        for (String order : rawOrders) {
            // Esta verificaci?n es O(N) en el peor caso, y se ejecuta N veces -> O(N^2)
            if (!uniqueOrders.contains(order)) {
                uniqueOrders.add(order);
            }
        }
        return uniqueOrders;
    }
}
