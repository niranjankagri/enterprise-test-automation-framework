package com.enterprise.automation.ui.components;

import com.enterprise.automation.ui.BaseComponent;
import com.enterprise.automation.ui.TestId;
import com.enterprise.automation.ui.pages.CheckoutPage;
import com.enterprise.automation.ui.pages.LoginPage;
import org.openqa.selenium.By;

/** The top bar on every signed-in page: current user, cart badge, logout. */
public class HeaderComponent extends BaseComponent {

    private static final By CURRENT_USER = TestId.of("current-user");
    private static final By CART_COUNT = TestId.of("cart-count");
    private static final By CART_LINK = TestId.of("cart-link");
    private static final By LOGOUT = TestId.of("logout-button");

    public HeaderComponent() {
        super(TestId.of("header"));
    }

    /** E.g. "Alex Admin (ADMIN)". */
    public String currentUser() {
        return child(CURRENT_USER).getText().trim();
    }

    public int cartCount() {
        return Integer.parseInt(child(CART_COUNT).getText().trim());
    }

    /** Waits until the cart badge shows {@code expected} (it updates after a click). */
    public HeaderComponent waitForCartCount(int expected) {
        String text = String.valueOf(expected);
        wait.until(d -> text.equals(d.findElement(CART_COUNT).getText().trim()), "cart count " + text);
        return this;
    }

    public CheckoutPage openCart() {
        actions.click(CART_LINK);
        return new CheckoutPage().waitUntilLoaded();
    }

    public LoginPage logout() {
        actions.click(LOGOUT);
        return new LoginPage().waitUntilLoaded();
    }
}
