package com.enterprise.automation.ui.components;

import com.enterprise.automation.ui.BaseComponent;
import com.enterprise.automation.ui.TestId;
import com.enterprise.automation.ui.pages.CheckoutPage;
import com.enterprise.automation.ui.pages.CustomerPage;
import com.enterprise.automation.ui.pages.DashboardPage;
import com.enterprise.automation.ui.pages.OrderPage;
import com.enterprise.automation.ui.pages.ProductPage;
import org.openqa.selenium.By;

/** The side menu. Each method clicks a link and returns the loaded page. */
public class NavigationComponent extends BaseComponent {

    // The highlighted entry has the CSS class "active"
    private static final By ACTIVE = By.cssSelector("a.active");

    /** The side navigation of the signed-in app. */
    public NavigationComponent() {
        super(TestId.of("navigation"));
    }

    // Each open...() clicks its menu link and returns the page once it has loaded its data

    public DashboardPage openDashboard() {
        actions.click(TestId.of("nav-dashboard"));
        return new DashboardPage().waitUntilLoaded();
    }

    /** Opens Customers and waits until its data has loaded. */
    public CustomerPage openCustomers() {
        actions.click(TestId.of("nav-customers"));
        return new CustomerPage().waitUntilLoaded();
    }

    /** Opens Products and waits until its data has loaded. */
    public ProductPage openProducts() {
        actions.click(TestId.of("nav-products"));
        return new ProductPage().waitUntilLoaded();
    }

    /** Opens Orders and waits until its data has loaded. */
    public OrderPage openOrders() {
        actions.click(TestId.of("nav-orders"));
        return new OrderPage().waitUntilLoaded();
    }

    /** Opens Checkout and waits until its data has loaded. */
    public CheckoutPage openCheckout() {
        actions.click(TestId.of("nav-checkout"));
        return new CheckoutPage().waitUntilLoaded();
    }

    /** Label of the highlighted menu entry, e.g. "Customers". */
    public String activeItem() {
        return child(ACTIVE).getText().trim();
    }
}
