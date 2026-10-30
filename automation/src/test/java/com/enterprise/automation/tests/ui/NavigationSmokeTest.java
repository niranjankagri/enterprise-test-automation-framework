package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.ui.components.NavigationComponent;
import com.enterprise.automation.ui.pages.DashboardPage;
import org.testng.annotations.Test;

/** Every section opens from the side menu, loads its data and highlights its menu entry. */
@Test(groups = {"ui", "smoke"})
public class NavigationSmokeTest extends BaseTest {

    /** Every section opens from the menu with its heading and highlighted entry. */
    public void tc110_everySectionOpensFromTheMenu() {
        DashboardPage dashboard = loginAsAdmin();
        // The same menu component is used for every hop (it re-finds its elements on each page)
        NavigationComponent menu = dashboard.navigation();

        // Each open...() waits until the page has loaded its data; then heading and highlight are checked
        assertThat(menu.openCustomers().heading()).isEqualTo("Customers");
        assertThat(menu.activeItem()).isEqualTo("Customers");

        assertThat(menu.openProducts().heading()).isEqualTo("Products");
        assertThat(menu.activeItem()).isEqualTo("Products");

        assertThat(menu.openOrders().heading()).isEqualTo("Orders");
        assertThat(menu.activeItem()).isEqualTo("Orders");

        assertThat(menu.openCheckout().heading()).isEqualTo("Checkout");
        assertThat(menu.activeItem()).isEqualTo("Checkout");

        // And back to where we started
        assertThat(menu.openDashboard().heading()).isEqualTo("Dashboard");
    }

    /** The dashboard shows its figures and the recent-orders table with the right columns. */
    public void tc111_dashboardShowsTheKeyFigures() {
        DashboardPage dashboard = loginAsAdmin();

        // Figures are checked loosely (other tests change them in parallel); the shape is checked exactly
        assertThat(Integer.parseInt(dashboard.stat("products"))).isPositive();
        assertThat(Integer.parseInt(dashboard.stat("customers"))).isPositive();
        assertThat(dashboard.stat("revenue")).startsWith("$");
        assertThat(dashboard.recentOrders().headers()).containsExactly("Order", "Customer", "Total", "Status", "Date");
    }

    /** The dashboard and shared navigation pass the selected automated axe rules. */
    public void tc112_dashboardNavigationHasNoAxeViolations() {
        loginAsAdmin();

        AccessibilityAssertions.pageHasNoViolations(DriverManager.getDriver(), "dashboard navigation");
    }
}
