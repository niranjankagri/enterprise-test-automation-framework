package com.enterprise.automation.ui.pages;

import com.enterprise.automation.ui.TestId;
import com.enterprise.automation.ui.components.ModalComponent;
import com.enterprise.automation.ui.components.TableComponent;
import org.openqa.selenium.By;

/** Order list: filter by status, view details, cancel. Rows are identified as "#&lt;id&gt;". */
public class OrderPage extends ShopPage<OrderPage> {

    // Status select above the list
    private static final By STATUS_FILTER = TestId.of("order-status-filter");
    // Column that identifies an order row ("#12")
    private static final String ORDER = "Order";

    @Override
    protected String pageId() {
        return "orders";
    }

    @Override
    protected String path() {
        return "/orders.html";
    }

    /** The order list, newest first. */
    public TableComponent table() {
        return new TableComponent(TestId.of("orders-table"));
    }

    /** {@code "All statuses"} clears the filter. */
    public OrderPage filterByStatus(String status) {
        actions.selectByVisibleText(STATUS_FILTER, status);
        return waitUntilLoaded();
    }

    /** The order id from the "Order #12 placed successfully" toast shown after checkout. */
    public long placedOrderId() {
        String message = toast().waitForMessage("placed successfully");
        // Non-digits -> spaces; the first number left is the order id
        return Long.parseLong(message.replaceAll("\\D+", " ").trim().split(" ")[0]);
    }

    /** Opens the detail dialog of order {@code id}. */
    public ModalComponent viewOrder(long id) {
        table().clickInRow(ORDER, "#" + id, TestId.of("view"));
        return new ModalComponent();
    }

    /** Cancels a PLACED order through its detail dialog ("Cancel order" is the dialog's submit). */
    public OrderPage cancelOrder(long id) {
        viewOrder(id).submitAndWaitUntilClosed();
        return waitUntilLoaded();
    }

    /** Status shown in the row of order {@code id}. */
    public String statusOf(long id) {
        return table().row(ORDER, "#" + id).get("Status");
    }
}
