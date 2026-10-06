package com.enterprise.automation.tests.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.data.ProductData;
import com.enterprise.automation.tests.base.BaseTest;
import com.enterprise.automation.tests.base.TestDataProviders;
import com.enterprise.automation.ui.pages.ProductPage;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

/** The catalogue shown in the UI matches the reference data in {@code testdata/products.json}. */
@Test(groups = {"ui", "regression"})
public class ProductCatalogTest extends BaseTest {

    @Test(dataProvider = "catalogue", dataProviderClass = TestDataProviders.class)
    public void productIsListedWithItsDetails(String sku, ProductData expected) {
        ProductPage products = loginAsAdmin().navigation().openProducts().search(sku);

        Map<String, String> row = products.table().rows().stream().filter(r -> sku.equals(r.get("SKU")))
                .findFirst().orElseThrow(() -> new AssertionError(sku + " not found"));
        assertThat(row).containsEntry("Name", expected.name())
                .containsEntry("Category", expected.category())
                .containsEntry("Price", expected.displayPrice());
    }

    @Test(dataProvider = "categories", dataProviderClass = TestDataProviders.class)
    public void categoryFilterShowsExactlyItsProducts(String category, List<String> expectedNames) {
        ProductPage products = loginAsAdmin().navigation().openProducts().filterByCategory(category);

        assertThat(products.catalogueRows()).extracting(r -> r.get("Name")).containsExactlyInAnyOrderElementsOf(expectedNames);
        assertThat(products.table().column("Category")).as("no product of another category").containsOnly(category);
    }

    public void searchWithoutMatchShowsAnEmptyList() {
        ProductPage products = loginAsAdmin().navigation().openProducts().search("no-such-product-xyz");

        assertThat(products.table().isEmpty()).isTrue();
        assertThat(products.resultCount()).isEqualTo("0 products");
    }
}
