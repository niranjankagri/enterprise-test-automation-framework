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
        CheckoutPage checkout = loginAsAdmin().navigation().openCheckout();

        assertThat(checkout.cart().isEmpty()).isTrue();
        assertThat(checkout.total()).isEqualTo("$0.00");
        assertThat(checkout.canPlaceOrder()).isFalse();
    }

    public void quantitiesDriveTheTotal() {
        OrderData expected = TestDataFactory.order(null,
                TestDataFactory.line("MON-2001", 3), TestDataFactory.line("ACC-3001", 1));
        ProductPage products = loginAsAdmin().navigation().openProducts();
        products.addToCart("27-inch 4K Monitor").addToCart("Wireless Keyboard");

        CheckoutPage checkout = products.header().openCart().setQuantity("27-inch 4K Monitor", 3);

        assertThat(checkout.total()).isEqualTo(expected.displayTotal());
        assertThat(checkout.header().waitForCartCount(expected.itemCount()).cartCount()).isEqualTo(4);
    }

    public void removingTheLastItemEmptiesTheCart() {
        ProductPage products = loginAsAdmin().navigation().openProducts().addToCart("USB-C Dock");

        CheckoutPage checkout = products.header().openCart().remove("USB-C Dock");

        assertThat(checkout.cart().isEmpty()).isTrue();
        assertThat(checkout.canPlaceOrder()).isFalse();
        assertThat(checkout.header().waitForCartCount(0).cartCount()).isZero();
    }

    public void orderNeedsACustomer() {
        ProductPage products = loginAsAdmin().navigation().openProducts().addToCart("Wireless Mouse");

        String error = products.header().openCart().placeOrderExpectingError();

        assertThat(error).isEqualTo("Please select a customer");
    }

    @Test(groups = "sanity")
    public void viewerCannotPlaceOrders() {
        ProductPage products = loginAsViewer().navigation().openProducts().addToCart("Wireless Mouse");

        CheckoutPage checkout = products.header().openCart();

        assertThat(checkout.cart().column("Product")).containsExactly("Wireless Mouse");
        assertThat(checkout.canPlaceOrder()).as("read-only role").isFalse();
    }
}
