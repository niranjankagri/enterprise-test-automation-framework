package com.enterprise.automation.api.models;

import com.enterprise.automation.data.ProductData;
import java.math.BigDecimal;

/** Body of {@code POST/PUT /api/products}. */
public record ProductRequest(String sku, String name, String category, BigDecimal price, Integer stock) {

    /** Request body from a test-data record. */
    public static ProductRequest from(ProductData product) {
        return new ProductRequest(product.sku(), product.name(), product.category(), product.price(), product.stock());
    }
}
