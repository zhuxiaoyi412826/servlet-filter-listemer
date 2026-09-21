package org.example.restaurant.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 购物车（存储在 HttpSession 中，Map<菜品ID, 数量>）。
 */
public class Cart implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<Long, Integer> items = new LinkedHashMap<>();

    public Map<Long, Integer> getRaw() {
        return items;
    }

    public void add(Long dishId, int quantity) {
        items.merge(dishId, quantity, Integer::sum);
    }

    public void set(Long dishId, int quantity) {
        if (quantity <= 0) items.remove(dishId);
        else items.put(dishId, quantity);
    }

    public void remove(Long dishId) {
        items.remove(dishId);
    }

    public void clear() {
        items.clear();
    }

    public int size() {
        return items.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
