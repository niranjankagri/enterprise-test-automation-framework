package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.ui.components.NavigationComponent;
import com.enterprise.automation.ui.pages.DashboardPage;
import org.testng.annotations.Test;

/** Every section opens from the side menu, loads its data and highlights its menu entry. */
@Test(groups = {"ui", "smoke"})
public class NavigationSmokeTest extends BaseTest {

    public void everySectionOpensFromTheMenu() {
        DashboardPage dashboard = loginAsAdmin();
        NavigationComponent menu = dashboard.navigation();

        assertThat(menu.openCustomers().heading()).isEqualTo("Customers");
        assertThat(menu.activeItem()).isEqualTo("Customers");

        assertThat(menu.openProducts().heading()).isEqualTo("Products");
        assertThat(menu.activeItem()).isEqualTo("Products");

        assertThat(menu.openOrders().heading()).isEqualTo("Orders");
        assertThat(menu.activeItem()).isEqualTo("Orders");

        assertThat(menu.openCheckout().heading()).isEqualTo("Checkout");
        assertThat(menu.activeItem()).isEqualTo("Checkout");

        assertThat(menu.openDashboard().heading()).isEqualTo("Dashboard");
    }

    public void dashboardShowsTheKeyFigures() {
        DashboardPage dashboard = loginAsAdmin();

        assertThat(Integer.parseInt(dashboard.stat("products"))).isPositive();
        assertThat(Integer.parseInt(dashboard.stat("customers"))).isPositive();
        assertThat(dashboard.stat("revenue")).startsWith("$");
        assertThat(dashboard.recentOrders().headers()).containsExactly("Order", "Customer", "Total", "Status", "Date");
    }
}
