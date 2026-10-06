package com.enterprise.automation.ui.pages;

import com.enterprise.automation.ui.TestId;
import com.enterprise.automation.ui.components.TableComponent;
import org.openqa.selenium.By;

/** Landing page after sign-in: welcome message, key figures and recent orders. */
public class DashboardPage extends ShopPage<DashboardPage> {

    private static final By WELCOME = TestId.of("welcome-message");

    @Override
    protected String pageId() {
        return "dashboard";
    }

    @Override
    protected String path() {
        return "/dashboard.html";
    }

    public String welcomeMessage() {
        return actions.text(WELCOME);
    }

    /** A figure from the stat cards: {@code customers}, {@code products}, {@code orders} or {@code revenue}. */
    public String stat(String name) {
        return actions.text(TestId.of("stat-" + name));
    }

    public TableComponent recentOrders() {
        return new TableComponent(TestId.of("recent-orders-table"));
    }
}
