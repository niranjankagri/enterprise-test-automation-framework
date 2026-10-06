package com.enterprise.automation.ui.pages;

import com.enterprise.automation.ui.TestId;
import com.enterprise.automation.ui.components.TableComponent;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

/** Cart review and order placement. */
public class CheckoutPage extends ShopPage<CheckoutPage> {

    private static final By CUSTOMER = TestId.of("checkout-customer");
    private static final By PLACE_ORDER = TestId.of("place-order");
    private static final By TOTAL = TestId.of("order-total");
    private static final By ERROR = TestId.of("checkout-error");
    private static final String PRODUCT = "Product";

    @Override
    protected String pageId() {
        return "checkout";
    }

    @Override
    protected String path() {
        return "/checkout.html";
    }

    public TableComponent cart() {
        return new TableComponent(TestId.of("cart-table"));
    }

    /** Order total as shown, e.g. "$2,477.00". */
    public String total() {
        return actions.text(TOTAL);
    }

    /** Changes the quantity of a cart line; the line total and order total update. */
    public CheckoutPage setQuantity(String productName, int quantity) {
        By input = By.cssSelector("[data-testid='quantity'][aria-label='Quantity of " + productName + "']");
        if (String.valueOf(quantity).equals(actions.value(input))) {
            return this;
        }
        String before = total();
        wait.until(d -> {
            WebElement field = d.findElement(input);
            // END + one BACK_SPACE per digit, not Ctrl+A: Firefox does not select-all in number inputs
            int digits = String.valueOf(field.getDomProperty("value")).length();
            field.sendKeys(Keys.END, Keys.BACK_SPACE.toString().repeat(digits), String.valueOf(quantity), Keys.TAB);
            return true;
        }, "quantity of " + productName);
        wait.until(d -> !before.equals(d.findElement(TOTAL).getText().trim()), "order total to change");
        return this;
    }

    public int quantityOf(String productName) {
        return Integer.parseInt(actions.value(
                By.cssSelector("[data-testid='quantity'][aria-label='Quantity of " + productName + "']")));
    }

    public CheckoutPage remove(String productName) {
        cart().clickInRow(PRODUCT, productName, TestId.of("remove"));
        wait.until(d -> !cart().hasRow(PRODUCT, productName), productName + " removed from cart");
        return this;
    }

    /** Picks the customer whose option contains {@code text} (name or email). */
    public CheckoutPage selectCustomer(String text) {
        actions.selectContaining(CUSTOMER, text);
        return this;
    }

    public boolean canPlaceOrder() {
        return actions.isEnabled(PLACE_ORDER);
    }

    /** Places the order and lands on the order list. */
    public OrderPage placeOrder() {
        actions.click(PLACE_ORDER);
        return new OrderPage().waitUntilLoaded();
    }

    /** Clicks "Place order" expecting a validation or business error on this page. */
    public String placeOrderExpectingError() {
        actions.click(PLACE_ORDER);
        wait.until(d -> !d.findElement(ERROR).getText().isBlank(), "checkout error");
        return actions.text(ERROR);
    }
}
