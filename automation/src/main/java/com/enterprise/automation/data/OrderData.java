package com.enterprise.automation.data;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * An order to place: which customer buys which products in what quantity.
 *
 * @param customer the buyer
 * @param lines    products and quantities
 */
public record OrderData(CustomerData customer, List<Line> lines) {

    // Immutable copy of the lines
    public OrderData {
        lines = List.copyOf(lines);
    }

    /** One product and its quantity. */
    public record Line(ProductData product, int quantity) {

        /** Price x quantity. */
        public BigDecimal total() {
            return product.price().multiply(BigDecimal.valueOf(quantity));
        }
    }

    /** Expected order total: sum of price × quantity. */
    public BigDecimal total() {
        return lines.stream().map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Total as the UI shows it, e.g. {@code $2,477.00}. */
    public String displayTotal() {
        return NumberFormat.getCurrencyInstance(Locale.US).format(total());
    }

    /** Total number of units (the "Items" column of the order list). */
    public int itemCount() {
        return lines.stream().mapToInt(Line::quantity).sum();
    }
}
