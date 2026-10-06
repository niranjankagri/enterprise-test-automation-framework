package com.enterprise.automation.api.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** An order as the API returns it, with its lines. */
public record OrderResponse(long id, long customerId, String customerName, String status, BigDecimal total,
                            Instant createdAt, List<Item> items) {

    /** One order line with the price at the time of ordering. */
    public record Item(long productId, String sku, String name, int quantity, BigDecimal unitPrice,
                       BigDecimal lineTotal) {
    }
}
