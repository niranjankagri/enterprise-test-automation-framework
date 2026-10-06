package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.data.OrderData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.ui.pages.CheckoutPage;
import com.enterprise.automation.ui.pages.ProductPage;
import org.testng.annotations.Test;

/**
 * Cart and checkout rules. These tests stop before an order is placed (the cart lives in the
 * browser session), so they create no data on the server.
 */
@Test(groups = {"ui", "regression"})
public class CheckoutTest extends BaseTest {

    public void emptyCartCannotBeOrdered() {
        // A fresh browser session has an empty cart
        CheckoutPage checkout = loginAsAdmin().navigation().openCheckout();

        // Nothing to order: zero total and a disabled button
        assertThat(checkout.cart().isEmpty()).isTrue();
        assertThat(checkout.total()).isEqualTo("$0.00");
        assertThat(checkout.canPlaceOrder()).isFalse();
    }

    public void quantitiesDriveTheTotal() {
        // Expected total computed from the reference catalogue (no customer needed for the maths)
        OrderData expected = TestDataFactory.order(null,
                TestDataFactory.line("MON-2001", 3), TestDataFactory.line("ACC-3001", 1));
        ProductPage products = loginAsAdmin().navigation().openProducts();
        products.addToCart("27-inch 4K Monitor").addToCart("Wireless Keyboard");

        // Raise the monitor line from 1 to 3
        CheckoutPage checkout = products.header().openCart().setQuantity("27-inch 4K Monitor", 3);

        // Total and cart badge follow the new quantity
        assertThat(checkout.total()).isEqualTo(expected.displayTotal());
        assertThat(checkout.header().waitForCartCount(expected.itemCount()).cartCount()).isEqualTo(4);
    }

    public void removingTheLastItemEmptiesTheCart() {
        ProductPage products = loginAsAdmin().navigation().openProducts().addToCart("USB-C Dock");

        // Remove the only line
        CheckoutPage checkout = products.header().openCart().remove("USB-C Dock");

        // Empty cart again: no order possible, badge back to 0
        assertThat(checkout.cart().isEmpty()).isTrue();
        assertThat(checkout.canPlaceOrder()).isFalse();
        assertThat(checkout.header().waitForCartCount(0).cartCount()).isZero();
    }

    public void orderNeedsACustomer() {
        ProductPage products = loginAsAdmin().navigation().openProducts().addToCart("Wireless Mouse");

        // A filled cart but no customer selected
        String error = products.header().openCart().placeOrderExpectingError();

        // Rejected by the page before any API call
        assertThat(error).isEqualTo("Please select a customer");
    }

    @Test(groups = "sanity")
    public void viewerCannotPlaceOrders() {
        ProductPage products = loginAsViewer().navigation().openProducts().addToCart("Wireless Mouse");

        CheckoutPage checkout = products.header().openCart();

        // A viewer may fill a cart but not place the order
        assertThat(checkout.cart().column("Product")).containsExactly("Wireless Mouse");
        assertThat(checkout.canPlaceOrder()).as("read-only role").isFalse();
    }
}
