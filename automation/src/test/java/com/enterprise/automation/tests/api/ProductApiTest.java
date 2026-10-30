package com.enterprise.automation.tests.api;

import static com.enterprise.automation.api.ApiAssertions.error;
import static com.enterprise.automation.api.ApiAssertions.expectStatus;
import static com.enterprise.automation.api.ApiAssertions.matchesSchema;
import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.JsonMapper;
import com.enterprise.automation.api.models.ErrorResponse;
import com.enterprise.automation.api.models.ProductRequest;
import com.enterprise.automation.api.models.ProductResponse;
import com.enterprise.automation.api.services.ProductService;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.ProductData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.db.ShopDatabase;
import com.enterprise.automation.tests.base.BaseApiTest;
import com.enterprise.automation.tests.base.TestDataProviders;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.Map;
import org.testng.annotations.Test;

/** {@code /api/products}. */
@Test(groups = {"api", "regression"})
public class ProductApiTest extends BaseApiTest {

    private ProductService products() {
        return admin().products();
    }

    /** Products are only deactivated by DELETE; that is the clean-up the API offers. */
    private ProductResponse givenProduct(ProductData data) {
        ProductResponse created = products().createProduct(data);
        CleanupRegistry.register("deactivate product " + created.sku(), () -> products().deleteProduct(created.id()));
        return created;
    }

    private ErrorResponse expectInvalidPrice(ProductData data) {
        Response response = products().create(ProductRequest.from(data));
        if (response.statusCode() == 201) {
            ProductResponse unexpected = JsonMapper.fromJson(response.asString(), ProductResponse.class);
            CleanupRegistry.register("remove unexpectedly accepted product " + unexpected.sku(),
                    () -> ShopDatabase.fromConfig().deleteUnusedProduct(unexpected.id()));
        }
        return error(expectStatus(response, 400));
    }

    /** Each seeded product matches the reference data and the single-product contract. */
    @Test(dataProvider = "catalogue", dataProviderClass = TestDataProviders.class, groups = "smoke")
    public void tc246_catalogueMatchesTheReferenceData(String sku, ProductData expected) {
        // Look the product up by SKU, then fetch it by id to check the single-product contract
        ProductResponse product = products().getBySku(sku);

        matchesSchema(expectStatus(products().get(product.id()), 200), "product");
        // Values from testdata/products.json; prices compared by value (1199 == 1199.00)
        assertThat(product.name()).isEqualTo(expected.name());
        assertThat(product.category()).isEqualTo(expected.category());
        assertThat(product.price()).isEqualByComparingTo(expected.price());
        assertThat(product.active()).isTrue();
    }

    /** A new product is stored as sent and found through its category. */
    public void tc247_createdProductIsListedInItsCategory() {
        ProductData data = TestDataFactory.newProduct();

        ProductResponse created = givenProduct(data);

        // Stored as sent, and found through the category filter
        assertThat(created.sku()).isEqualTo(data.sku());
        assertThat(created.price()).isEqualByComparingTo(data.price());
        assertThat(products().findProducts(Map.of("category", data.category())))
                .extracting(ProductResponse::sku).contains(data.sku());
    }

    /** PATCH changes price and stock; a stock of 0 is valid, and the name stays. */
    public void tc248_patchUpdatesPriceAndStock() {
        ProductResponse created = givenProduct(TestDataFactory.newProduct());

        // Change two fields; stock 0 is valid (sold out), not "missing"
        ProductResponse updated = JsonMapper.fromJson(expectStatus(
                products().update(created.id(), Map.of("price", 19.99, "stock", 0)), 200).asString(), ProductResponse.class);

        // The two fields changed, the name did not
        assertThat(updated.price()).isEqualByComparingTo("19.99");
        assertThat(updated.stock()).isZero();
        assertThat(updated.name()).isEqualTo(created.name());
    }

    /** PUT replaces the product; a fresh GET shows the new values. */
    public void tc249_putReplacesTheProduct() {
        ProductResponse created = givenProduct(TestDataFactory.newProduct());
        ProductData replacement = TestDataFactory.newProduct();

        expectStatus(products().replace(created.id(), ProductRequest.from(replacement)), 200);

        // A fresh GET shows the replacement (the clean-up still deactivates the same id)
        ProductResponse current = products().getProduct(created.id());
        assertThat(current.sku()).isEqualTo(replacement.sku());
        assertThat(current.name()).isEqualTo(replacement.name());
    }

    /** DELETE deactivates a product: it stays readable by id for order history but leaves the catalogue. */
    public void tc250_deleteDeactivatesInsteadOfRemoving() {
        ProductResponse created = products().createProduct(TestDataFactory.newProduct());

        expectStatus(products().delete(created.id()), 204);

        // Still readable by id (old orders need it), but no longer in the catalogue
        assertThat(products().getProduct(created.id()).active()).as("kept for order history").isFalse();
        assertThat(products().findProducts(Map.of("search", created.sku()))).as("hidden from the catalogue").isEmpty();
    }

    /** Empty SKU, missing name, negative price and negative stock are each reported in one 400. */
    public void tc251_invalidValuesGive400PerField() {
        // Empty SKU, no name, negative price and negative stock in one request
        ErrorResponse error = error(expectStatus(products().create(
                new ProductRequest("", null, "Audio", new BigDecimal("-1"), -5)), 400));

        assertThat(error.fieldErrors())
                .containsEntry("sku", "SKU is required")
                .containsEntry("name", "Name is required")
                .containsEntry("price", "Price must be a positive number")
                .containsEntry("stock", "Stock must be a whole number of 0 or more");
    }

    /** A SKU already in use is a 409 on the sku field. */
    public void tc252_duplicateSkuGives409() {
        // A valid new product that reuses a seeded SKU
        ProductData duplicate = TestDataFactory.newProduct();
        ProductRequest request = new ProductRequest("ACC-3002", duplicate.name(), duplicate.category(),
                duplicate.price(), duplicate.stock());

        ErrorResponse error = error(expectStatus(products().create(request), 409));

        assertThat(error.fieldErrors()).containsEntry("sku", "SKU is already in use");
    }

    /** SQL-shaped search text is treated as data and cannot broaden a catalogue query. */
    public void tc253_sqlInjectionShapedSearchIsBoundAsData() {
        assertThat(products().findProducts(Map.of("search", "' OR '1'='1 --")))
                .as("search text cannot turn a filter into an always-true SQL predicate")
                .isEmpty();
    }

    /** Prices with a non-zero fractional digit beyond cents are rejected without inserting a rounded value. */
    @Test(groups = "integration")
    public void tc254_priceWithMoreThanTwoDecimalPlacesIsRejectedWithoutWriting() {
        ProductData data = TestDataFactory.newProduct().withPrice(new BigDecimal("1.001"));

        ErrorResponse error = expectInvalidPrice(data);

        assertThat(error.fieldErrors()).containsEntry("price",
                "Price must be no greater than 99999999.99 and representable to 2 decimal places");
        assertThat(ShopDatabase.fromConfig().productBySku(data.sku())).as("rejected price leaves no product row")
                .isEmpty();
    }

    /** Product prices above the DECIMAL(10,2) maximum are rejected before the database write. */
    @Test(groups = "integration")
    public void tc255_priceAboveDatabaseMaximumIsRejectedWithoutWriting() {
        ProductData data = TestDataFactory.newProduct().withPrice(new BigDecimal("100000000.00"));

        ErrorResponse error = expectInvalidPrice(data);

        assertThat(error.fieldErrors()).containsEntry("price",
                "Price must be no greater than 99999999.99 and representable to 2 decimal places");
        assertThat(ShopDatabase.fromConfig().productBySku(data.sku())).as("rejected price leaves no product row")
                .isEmpty();
    }

    /** The DECIMAL(10,2) maximum is accepted and stored without precision loss. */
    @Test(groups = "integration")
    public void tc256_maximumSupportedProductPriceIsStoredExactly() {
        ProductData data = TestDataFactory.newProduct().withPrice(new BigDecimal("99999999.99"));

        ProductResponse created = JsonMapper.fromJson(expectStatus(
                products().create(ProductRequest.from(data)), 201).asString(), ProductResponse.class);
        ShopDatabase database = ShopDatabase.fromConfig();
        CleanupRegistry.register("remove boundary product " + created.sku(),
                () -> database.deleteUnusedProduct(created.id()));

        assertThat(created.price()).isEqualByComparingTo("99999999.99");
        assertThat(database.productBySku(data.sku()).orElseThrow()).containsEntry("price", new BigDecimal("99999999.99"));
    }

    /** Extra zeroes after cents are accepted because they do not require numeric rounding. */
    @Test(groups = "integration")
    public void tc257_insignificantTrailingPriceZeroesAreAcceptedAndNormalized() {
        ProductData data = TestDataFactory.newProduct().withPrice(new BigDecimal("1.000"));

        ProductResponse created = JsonMapper.fromJson(expectStatus(
                products().create(ProductRequest.from(data)), 201).asString(), ProductResponse.class);
        ShopDatabase database = ShopDatabase.fromConfig();
        CleanupRegistry.register("remove trailing-zero product " + created.sku(),
                () -> database.deleteUnusedProduct(created.id()));

        assertThat(database.productBySku(data.sku()).orElseThrow()).containsEntry("price", new BigDecimal("1.00"));
    }

    /** A PATCH with a non-cent value is rejected and does not alter the existing database price. */
    @Test(groups = "integration")
    public void tc258_unsupportedPricePrecisionPatchLeavesStoredValueUnchanged() {
        ProductData data = TestDataFactory.newProduct().withPrice(new BigDecimal("10.00"));
        ProductResponse created = products().createProduct(data);
        ShopDatabase database = ShopDatabase.fromConfig();
        CleanupRegistry.register("remove patch precision product " + created.sku(),
                () -> database.deleteUnusedProduct(created.id()));

        Response response = products().update(created.id(), Map.of("price", new BigDecimal("10.001")));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(error(response).fieldErrors()).containsEntry("price",
                "Price must be no greater than 99999999.99 and representable to 2 decimal places");
        assertThat(database.productBySku(data.sku()).orElseThrow()).containsEntry("price", new BigDecimal("10.00"));
    }
}
