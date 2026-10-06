package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.ui.pages.CheckoutPage;
import com.enterprise.automation.ui.pages.CustomerPage;
import com.enterprise.automation.ui.pages.ProductPage;
import org.testng.annotations.Test;

/**
 * Read-only checks on the customer and product lists plus the cart. They rely only on the
 * seeded catalogue (which no test modifies) and on the browser's own session, so they never
 * interfere with other tests.
 */
@Test(groups = {"ui", "smoke"})
public class CatalogSmokeTest extends BaseTest {

    public void customerSearchNarrowsTheList() {
        CustomerPage customers = loginAsAdmin().navigation().openCustomers();
        // How many customers are listed before searching
        int all = customers.table().rowCount();

        // Search for one seeded customer's unique email
        customers.search("ava.patel@example.com");

        // Exactly that customer remains, with the seeded values
        assertThat(customers.table().rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.get("Name")).isEqualTo("Ava Patel");
                    assertThat(row.get("City")).isEqualTo("Austin");
                });
        assertThat(all).isGreaterThan(1);
        assertThat(customers.resultCount()).isEqualTo("1 customer");
    }

    public void productSearchAndCategoryFilter() {
        ProductPage products = loginAsAdmin().navigation().openProducts();

        // Category filter: only monitors are listed
        products.filterByCategory("Monitors");
        assertThat(products.table().column("Category")).isNotEmpty().containsOnly("Monitors");

        // Reset the filter, then search by name: the seeded keyboard is found, every hit matches the term
        products.filterByCategory("All categories").search("keyboard");
        assertThat(products.catalogueRows()).extracting(r -> r.get("Name")).containsExactly("Wireless Keyboard");
        assertThat(products.table().column("Name")).allSatisfy(n -> assertThat(n).containsIgnoringCase("keyboard"));
    }

    public void addingProductsFillsTheCart() {
        ProductPage products = loginAsAdmin().navigation().openProducts();

        // Two mice and one dock: three units, two lines
        products.addToCart("Wireless Mouse").addToCart("Wireless Mouse").addToCart("USB-C Dock");
        products.header().waitForCartCount(3);
        CheckoutPage checkout = products.header().openCart();

        // Lines, merged quantity and total (2 x $49 + $189 = $287)
        assertThat(checkout.cart().column("Product")).containsExactly("Wireless Mouse", "USB-C Dock");
        assertThat(checkout.quantityOf("Wireless Mouse")).isEqualTo(2);
        assertThat(checkout.total()).isEqualTo("$287.00");
    }
}
