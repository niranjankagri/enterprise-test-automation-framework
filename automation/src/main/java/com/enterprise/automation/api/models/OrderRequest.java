package com.enterprise.automation.api.models;

import java.util.List;

/** Body of {@code POST /api/orders}. */
public record OrderRequest(Long customerId, List<Item> items) {

    public OrderRequest {
        items = items == null ? null : List.copyOf(items);
    }

    /** One line: product id and quantity. */
    public record Item(Long productId, Integer quantity) {
    }

    public static OrderRequest of(long customerId, Item... items) {
        return new OrderRequest(customerId, List.of(items));
    }

    public static Item item(long productId, int quantity) {
        return new Item(productId, quantity);
    }
}
