package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.data.ProductData;
import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.tests.base.TestDataProviders;
import com.enterprise.automation.ui.pages.ProductPage;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.testng.annotations.Test;

/** The catalogue shown in the UI matches the reference data in {@code testdata/products.json}. */
@Test(groups = {"ui", "regression"})
public class ProductCatalogTest extends BaseTest {

    // One run per product of testdata/products.json
    @Test(dataProvider = "catalogue", dataProviderClass = TestDataProviders.class)
    public void productIsListedWithItsDetails(String sku, ProductData expected) {
        ProductPage products = loginAsAdmin().navigation().openProducts().search(sku);

        // The row of exactly this SKU (a search can match more than one) shows the reference values
        Map<String, String> row = products.table().rows().stream().filter(r -> sku.equals(r.get("SKU")))
                .findFirst().orElseThrow(() -> new AssertionError(sku + " not found"));
        assertThat(row).containsEntry("Name", expected.name())
                .containsEntry("Category", expected.category())
                .containsEntry("Price", expected.displayPrice());
    }

    /** Each category filter shows exactly its seeded products and no product of another category. */
    @Test(dataProvider = "categories", dataProviderClass = TestDataProviders.class)
    public void categoryFilterShowsExactlyItsProducts(String category, List<String> expectedNames) {
        ProductPage products = loginAsAdmin().navigation().openProducts().filterByCategory(category);

        // Seeded products of the category: exactly the expected ones; any listed row: that category only
        assertThat(products.catalogueRows()).extracting(r -> r.get("Name")).containsExactlyInAnyOrderElementsOf(expectedNames);
        assertThat(products.table().column("Category")).as("no product of another category").containsOnly(category);
    }

    /** A search without a match shows the empty placeholder and a zero count. */
    public void searchWithoutMatchShowsAnEmptyList() {
        ProductPage products = loginAsAdmin().navigation().openProducts().search("no-such-product-xyz");

        // The "no products" placeholder row and a zero count
        assertThat(products.table().isEmpty()).isTrue();
        assertThat(products.resultCount()).isEqualTo("0 products");
    }

    /** A failed catalogue request is announced, and Retry reloads the current search. */
    @Test(groups = "smoke")
    public void catalogueFailureShowsRetryAndRecovers() {
        ProductPage products = loginAsAdmin().navigation().openProducts();
        var driver = DriverManager.getDriver();
        BrowserRequestSimulator.gateNetworkFailure(driver, "GET", "/api/products");
        try {
            products.beginSearch("Wireless");
            BrowserRequestSimulator.awaitPending(driver, config().explicitWait());

            assertThat(((JavascriptExecutor) driver).executeScript("return document.body.dataset.busy"))
                    .isEqualTo("true");
            assertThat(driver.findElement(By.cssSelector("[data-testid='loader']")).isDisplayed()).isTrue();

            BrowserRequestSimulator.release(driver);
            products.waitUntilLoaded();

            assertThat(products.hasLoadError()).isTrue();
            assertThat(products.loadErrorMessage()).contains("Products could not be loaded");
            assertThat(driver.findElement(By.cssSelector("[data-testid='product-load-retry']")).isEnabled()).isTrue();

            BrowserRequestSimulator.gatePassThrough(driver, "GET", "/api/products");
            products.beginRetryLoading();
            BrowserRequestSimulator.awaitPending(driver, config().explicitWait());
            assertThat(products.hasLoadError()).isFalse();
            assertThat(((JavascriptExecutor) driver).executeScript("return document.body.dataset.busy"))
                    .isEqualTo("true");

            BrowserRequestSimulator.release(driver);
            products.waitUntilLoaded();

            assertThat(products.hasLoadError()).isFalse();
            assertThat(products.table().column("Name")).contains("Wireless Keyboard", "Wireless Mouse");
        } finally {
            BrowserRequestSimulator.restore(driver);
        }
    }
}
