package com.enterprise.automation.ui.pages;

import com.enterprise.automation.ui.TestId;
import com.enterprise.automation.ui.components.TableComponent;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

/** Cart review and order placement. */
public class CheckoutPage extends ShopPage<CheckoutPage> {

    // Customer select, order button, order total, error banner
    private static final By CUSTOMER = TestId.of("checkout-customer");
    private static final By PLACE_ORDER = TestId.of("place-order");
    private static final By TOTAL = TestId.of("order-total");
    private static final By ERROR = TestId.of("checkout-error");
    // Cart rows are identified by the product name column
    private static final String PRODUCT = "Product";

    @Override
    protected String pageId() {
        return "checkout";
    }

    @Override
    protected String path() {
        return "/checkout.html";
    }

    /** The cart lines. */
    public TableComponent cart() {
        return new TableComponent(TestId.of("cart-table"));
    }

    /** Order total as shown, e.g. "$2,477.00". */
    public String total() {
        return actions.text(TOTAL);
    }

    /** Changes the quantity of a cart line; the line total and order total update. */
    public CheckoutPage setQuantity(String productName, int quantity) {
        // Each quantity input is labelled with its product (accessible name), which identifies the line
        By input = By.cssSelector("[data-testid='quantity'][aria-label='Quantity of " + productName + "']");
        // Already the wanted quantity: the total would not change, so waiting for a change would time out
        if (String.valueOf(quantity).equals(actions.value(input))) {
            return this;
        }
        // Remember the total to detect when the page has recalculated it
        String before = total();
        wait.until(d -> {
            WebElement field = d.findElement(input);
            // END + one BACK_SPACE per digit, not Ctrl+A: Firefox does not select-all in number inputs
            int digits = String.valueOf(field.getDomProperty("value")).length();
            field.sendKeys(Keys.END, Keys.BACK_SPACE.toString().repeat(digits), String.valueOf(quantity), Keys.TAB);
            return true;
        }, "quantity of " + productName);
        // TAB fired the change event; the page re-renders the cart with the new total
        wait.until(d -> !before.equals(d.findElement(TOTAL).getText().trim()), "order total to change");
        return this;
    }

    /** Quantity of a cart line. */
    public int quantityOf(String productName) {
        return Integer.parseInt(actions.value(
                By.cssSelector("[data-testid='quantity'][aria-label='Quantity of " + productName + "']")));
    }

    /** Removes a line and waits until it is gone. */
    public CheckoutPage remove(String productName) {
        cart().clickInRow(PRODUCT, productName, TestId.of("remove"));
        // Removing the last line replaces it with the "empty" row; both count as "no such row"
        wait.until(d -> !cart().hasRow(PRODUCT, productName), productName + " removed from cart");
        return this;
    }

    /** Picks the customer whose option contains {@code text} (name or email). */
    public CheckoutPage selectCustomer(String text) {
        actions.selectContaining(CUSTOMER, text);
        return this;
    }

    /** The button is disabled for an empty cart and for read-only users. */
    public boolean canPlaceOrder() {
        return actions.isEnabled(PLACE_ORDER);
    }

    /** Places the order and lands on the order list. */
    public OrderPage placeOrder() {
        actions.click(PLACE_ORDER);
        // The page navigates to /orders.html?placed=<id> on success
        return new OrderPage().waitUntilLoaded();
    }

    /** Clicks "Place order" expecting a validation or business error on this page. */
    public String placeOrderExpectingError() {
        actions.click(PLACE_ORDER);
        // The banner is empty until an error is shown
        wait.until(d -> !d.findElement(ERROR).getText().isBlank(), "checkout error");
        return actions.text(ERROR);
    }
}
