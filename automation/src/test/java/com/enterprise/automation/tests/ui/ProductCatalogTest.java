package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.automation.data.ProductData;
import com.enterprise.automation.driver.DriverManager;
import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.tests.base.TestDataProviders;
import com.enterprise.automation.ui.ElementActions;
import com.enterprise.automation.ui.TestId;
import com.enterprise.automation.ui.pages.ProductPage;
import com.enterprise.automation.utils.WaitUtils;
import io.qameta.allure.Allure;
import io.qameta.allure.model.StepResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.testng.annotations.Test;

/** The catalogue shown in the UI matches the reference data in {@code testdata/products.json}. */
@Test(groups = {"ui", "regression"})
public class ProductCatalogTest extends BaseTest {

    // One run per product of testdata/products.json
    @Test(dataProvider = "catalogue", dataProviderClass = TestDataProviders.class)
    public void tc116_productIsListedWithItsDetails(String sku, ProductData expected) {
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
    public void tc117_categoryFilterShowsExactlyItsProducts(String category, List<String> expectedNames) {
        ProductPage products = loginAsAdmin().navigation().openProducts().filterByCategory(category);

        // Seeded products of the category: exactly the expected ones; any listed row: that category only
        assertThat(products.catalogueRows()).extracting(r -> r.get("Name")).containsExactlyInAnyOrderElementsOf(expectedNames);
        assertThat(products.table().column("Category")).as("no product of another category").containsOnly(category);
    }

    /** A search without a match shows the empty placeholder and a zero count. */
    public void tc118_searchWithoutMatchShowsAnEmptyList() {
        ProductPage products = loginAsAdmin().navigation().openProducts().search("no-such-product-xyz");

        // The "no products" placeholder row and a zero count
        assertThat(products.table().isEmpty()).isTrue();
        assertThat(products.resultCount()).isEqualTo("0 products");
    }

    /** A failed catalogue request is announced, and Retry reloads the current search. */
    @Test(groups = "smoke")
    public void tc119_catalogueFailureShowsRetryAndRecovers() {
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

    /** A rejected select value is omitted from both the thrown error and retained Allure details. */
    @Test(groups = "smoke")
    public void missingCategoryOptionDoesNotPersistRequestedValue() {
        loginAsAdmin().navigation().openProducts();
        String marker = "synthetic-select-value-" + UUID.randomUUID();
        ElementActions actions = new ElementActions(DriverManager.getDriver(),
                new WaitUtils(DriverManager.getDriver(), config().explicitWait()));

        assertThatThrownBy(() -> actions.selectByVisibleText(TestId.of("category-filter"), marker))
                .isInstanceOf(NoSuchElementException.class)
                .hasNoCause()
                .hasMessageNotContaining(marker);

        StringBuilder evidence = new StringBuilder();
        Allure.getLifecycle().updateTest(testCase -> appendStepEvidence(testCase.getSteps(), evidence));
        assertThat(evidence).contains("Select option in category-filter").doesNotContain(marker);
    }

    private static void appendStepEvidence(List<StepResult> steps, StringBuilder evidence) {
        for (StepResult step : steps) {
            evidence.append(step.getName()).append('\n');
            if (step.getStatusDetails() != null) {
                evidence.append(step.getStatusDetails().getMessage()).append('\n')
                        .append(step.getStatusDetails().getTrace()).append('\n')
                        .append(step.getStatusDetails().getActual()).append('\n')
                        .append(step.getStatusDetails().getExpected()).append('\n');
            }
            appendStepEvidence(step.getSteps(), evidence);
        }
    }
}
