package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.data.OrderData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.ui.pages.CheckoutPage;
import com.enterprise.automation.ui.pages.ProductPage;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.testng.annotations.Test;

/**
 * Cart and checkout rules. These tests stop before an order is placed (the cart lives in the
 * browser session), so they create no data on the server.
 */
@Test(groups = {"ui", "regression"})
public class CheckoutTest extends BaseTest {

    /** An empty cart shows a zero total and cannot be ordered. */
    public void tc131_emptyCartCannotBeOrdered() {
        // A fresh browser session has an empty cart
        CheckoutPage checkout = loginAsAdmin().navigation().openCheckout();

        // Nothing to order: zero total and a disabled button
        assertThat(checkout.cart().isEmpty()).isTrue();
        assertThat(checkout.total()).isEqualTo("$0.00");
        assertThat(checkout.canPlaceOrder()).isFalse();
    }

    /** Changing a line's quantity updates the total, which matches the catalogue maths. */
    public void tc132_quantitiesDriveTheTotal() {
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

    /** Removing the only line empties the cart and the badge. */
    public void tc133_removingTheLastItemEmptiesTheCart() {
        ProductPage products = loginAsAdmin().navigation().openProducts().addToCart("USB-C Dock");

        // Remove the only line
        CheckoutPage checkout = products.header().openCart().remove("USB-C Dock");

        // Empty cart again: no order possible, badge back to 0
        assertThat(checkout.cart().isEmpty()).isTrue();
        assertThat(checkout.canPlaceOrder()).isFalse();
        assertThat(checkout.header().waitForCartCount(0).cartCount()).isZero();
    }

    /** An order without a customer is refused by the page before any API call. */
    public void tc134_orderNeedsACustomer() {
        ProductPage products = loginAsAdmin().navigation().openProducts().addToCart("Wireless Mouse");

        // A filled cart but no customer selected
        String error = products.header().openCart().placeOrderExpectingError();

        // Rejected by the page before any API call
        assertThat(error).isEqualTo("Please select a customer");
    }

    /** A viewer can fill a cart but not place the order. */
    @Test(groups = "sanity")
    public void tc135_viewerCannotPlaceOrders() {
        ProductPage products = loginAsViewer().navigation().openProducts().addToCart("Wireless Mouse");

        CheckoutPage checkout = products.header().openCart();

        // A viewer may fill a cart but not place the order
        assertThat(checkout.cart().column("Product")).containsExactly("Wireless Mouse");
        assertThat(checkout.canPlaceOrder()).as("read-only role").isFalse();
    }

    /** Checkout accessible names pass axe and its quantity/remove controls remain keyboard reachable. */
    @Test(groups = "smoke")
    public void tc136_checkoutHasNoAxeViolationsAndKeyboardFocusIsVisible() {
        CheckoutPage checkout = loginAsAdmin().navigation().openProducts().addToCart("Wireless Mouse")
                .header().openCart();
        AccessibilityAssertions.pageHasNoViolations(DriverManager.getDriver(), "checkout with a cart line");

        WebElement quantity = DriverManager.getDriver().findElement(By.cssSelector("[data-testid='quantity']"));
        quantity.click();
        new Actions(DriverManager.getDriver()).sendKeys(Keys.TAB).perform();
        WebElement focused = DriverManager.getDriver().switchTo().activeElement();
        assertThat(focused.getAttribute("data-testid")).isEqualTo("remove");
        assertThat(((List<?>) ((org.openqa.selenium.JavascriptExecutor) DriverManager.getDriver()).executeScript(
                "return [getComputedStyle(document.activeElement).outlineStyle,"
                        + "parseFloat(getComputedStyle(document.activeElement).outlineWidth)];")))
                .satisfies(style -> {
                    assertThat(style).first().isEqualTo("solid");
                    assertThat(((Number) style.get(1)).doubleValue()).isGreaterThanOrEqualTo(1.5);
                });
        assertThat(checkout.canPlaceOrder()).isTrue();
    }
}
