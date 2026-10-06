package com.enterprise.automation.ui.pages;

import com.enterprise.automation.ui.TestId;
import com.enterprise.automation.ui.components.TableComponent;
import org.openqa.selenium.By;

/** Product catalogue: search, filter by category, add to cart. */
public class ProductPage extends ShopPage<ProductPage> {

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

    public TableComponent table() {
        return new TableComponent(TestId.of("products-table"));
    }

    public ProductPage search(String term) {
        actions.type(SEARCH, term);
        actions.click(SEARCH_BUTTON);
        return waitUntilLoaded();
    }

    /** {@code "All categories"} clears the filter. */
    public ProductPage filterByCategory(String category) {
        actions.selectByVisibleText(CATEGORY, category);
        return waitUntilLoaded();
    }

    public String resultCount() {
        return actions.text(RESULT_COUNT);
    }

    /** Adds one unit of the product named {@code name}; waits for the confirmation toast. */
    public ProductPage addToCart(String name) {
        table().clickInRow("Name", name, TestId.of("add-to-cart"));
        toast().waitForMessage(name + " added to cart");
        return this;
    }
}
