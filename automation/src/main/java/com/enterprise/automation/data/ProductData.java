package com.enterprise.automation.data;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * A product of the catalogue.
 *
 * @param sku      unique stock-keeping unit, e.g. {@code ACC-3002}
 * @param name     display name
 * @param category one of the catalogue categories
 * @param price    unit price
 * @param stock    units available
 */
public record ProductData(String sku, String name, String category, BigDecimal price, int stock) {

    /** Price as the UI shows it, e.g. {@code $1,199.00}. */
    public String displayPrice() {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        return format.format(price);
    }

    public ProductData withStock(int value) {
        return new ProductData(sku, name, category, price, value);
    }

    public ProductData withPrice(BigDecimal value) {
        return new ProductData(sku, name, category, value, stock);
    }
}
