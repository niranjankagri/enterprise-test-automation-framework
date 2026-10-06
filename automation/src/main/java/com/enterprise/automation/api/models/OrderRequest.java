package com.enterprise.automation.api.models;

import java.util.List;

/** Body of {@code POST /api/orders}. */
public record OrderRequest(Long customerId, List<Item> items) {

    // Immutable copy of the items; null stays null so negative tests can send "no items"
    public OrderRequest {
        items = items == null ? null : List.copyOf(items);
    }

    /** One line: product id and quantity. */
    public record Item(Long productId, Integer quantity) {
    }

    /** Short form for tests: {@code OrderRequest.of(customerId, item(productId, 2))}. */
    public static OrderRequest of(long customerId, Item... items) {
        return new OrderRequest(customerId, List.of(items));
    }

    /** One order line. */
    public static Item item(long productId, int quantity) {
        return new Item(productId, quantity);
    }
}
