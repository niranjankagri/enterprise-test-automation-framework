package com.enterprise.automation.ui.pages;

import com.enterprise.automation.ui.TestId;
import com.enterprise.automation.ui.components.TableComponent;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.By;

/** Product catalogue: search, filter by category, add to cart. */
public class ProductPage extends ShopPage<ProductPage> {

    // Toolbar: search box, search button, category select, "N products" counter
    private static final By SEARCH = TestId.of("product-search");
    private static final By SEARCH_BUTTON = TestId.of("product-search-button");
    private static final By CATEGORY = TestId.of("category-filter");
    private static final By RESULT_COUNT = TestId.of("result-count");

    @Override
    protected String pageId() {
        return "products";
    }

    @Override
    protected String path() {
        return "/products.html";
    }

    /** The product list. */
    public TableComponent table() {
        return new TableComponent(TestId.of("products-table"));
    }

    /** Searches by name or SKU and waits for the filtered list. */
    public ProductPage search(String term) {
        actions.type(SEARCH, term);
        actions.click(SEARCH_BUTTON);
        return waitUntilLoaded();
    }

    /** {@code "All categories"} clears the filter. */
    public ProductPage filterByCategory(String category) {
        // The page reloads the list as soon as the selection changes
        actions.selectByVisibleText(CATEGORY, category);
        return waitUntilLoaded();
    }

    /**
     * Rows of the seeded catalogue only. Tests that create products use {@code TST-} SKUs; when
     * tests run in parallel those can appear in any listing, so catalogue checks ignore them.
     */
    public List<Map<String, String>> catalogueRows() {
        return table().rows().stream().filter(r -> !r.getOrDefault("SKU", "").startsWith("TST-")).toList();
    }

    /** E.g. "8 products". */
    public String resultCount() {
        return actions.text(RESULT_COUNT);
    }

    /** Adds one unit of the product named {@code name}; waits for the confirmation toast. */
    public ProductPage addToCart(String name) {
        table().clickInRow("Name", name, TestId.of("add-to-cart"));
        // The toast proves the click was handled (and the cart updated) before the test goes on
        toast().waitForMessage(name + " added to cart");
        return this;
    }
}
