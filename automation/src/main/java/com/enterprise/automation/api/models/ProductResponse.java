package com.enterprise.automation.api.models;

import java.math.BigDecimal;

/** A product as the API returns it. */
public record ProductResponse(long id, String sku, String name, String category, BigDecimal price, int stock,
                              boolean active) {
}
