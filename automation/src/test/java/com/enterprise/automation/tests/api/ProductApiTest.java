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
import com.enterprise.automation.tests.base.BaseApiTest;
import com.enterprise.automation.tests.base.TestDataProviders;
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

    @Test(dataProvider = "catalogue", dataProviderClass = TestDataProviders.class, groups = "smoke")
    public void catalogueMatchesTheReferenceData(String sku, ProductData expected) {
        ProductResponse product = products().getBySku(sku);

        matchesSchema(expectStatus(products().get(product.id()), 200), "product");
        assertThat(product.name()).isEqualTo(expected.name());
        assertThat(product.category()).isEqualTo(expected.category());
        assertThat(product.price()).isEqualByComparingTo(expected.price());
        assertThat(product.active()).isTrue();
    }

    public void createdProductIsListedInItsCategory() {
        ProductData data = TestDataFactory.newProduct();

        ProductResponse created = givenProduct(data);

        assertThat(created.sku()).isEqualTo(data.sku());
        assertThat(created.price()).isEqualByComparingTo(data.price());
        assertThat(products().findProducts(Map.of("category", data.category())))
                .extracting(ProductResponse::sku).contains(data.sku());
    }

    public void patchUpdatesPriceAndStock() {
        ProductResponse created = givenProduct(TestDataFactory.newProduct());

        ProductResponse updated = JsonMapper.fromJson(expectStatus(
                products().update(created.id(), Map.of("price", 19.99, "stock", 0)), 200).asString(), ProductResponse.class);

        assertThat(updated.price()).isEqualByComparingTo("19.99");
        assertThat(updated.stock()).isZero();
        assertThat(updated.name()).isEqualTo(created.name());
    }

    public void putReplacesTheProduct() {
        ProductResponse created = givenProduct(TestDataFactory.newProduct());
        ProductData replacement = TestDataFactory.newProduct();

        expectStatus(products().replace(created.id(), ProductRequest.from(replacement)), 200);

        ProductResponse current = products().getProduct(created.id());
        assertThat(current.sku()).isEqualTo(replacement.sku());
        assertThat(current.name()).isEqualTo(replacement.name());
    }

    public void deleteDeactivatesInsteadOfRemoving() {
        ProductResponse created = products().createProduct(TestDataFactory.newProduct());

        expectStatus(products().delete(created.id()), 204);

        assertThat(products().getProduct(created.id()).active()).as("kept for order history").isFalse();
        assertThat(products().findProducts(Map.of("search", created.sku()))).as("hidden from the catalogue").isEmpty();
    }

    public void invalidValuesGive400PerField() {
        ErrorResponse error = error(expectStatus(products().create(
                new ProductRequest("", null, "Audio", new BigDecimal("-1"), -5)), 400));

        assertThat(error.fieldErrors())
                .containsEntry("sku", "SKU is required")
                .containsEntry("name", "Name is required")
                .containsEntry("price", "Price must be a positive number")
                .containsEntry("stock", "Stock must be a whole number of 0 or more");
    }

    public void duplicateSkuGives409() {
        ProductData duplicate = TestDataFactory.newProduct();
        ProductRequest request = new ProductRequest("ACC-3002", duplicate.name(), duplicate.category(),
                duplicate.price(), duplicate.stock());

        ErrorResponse error = error(expectStatus(products().create(request), 409));

        assertThat(error.fieldErrors()).containsEntry("sku", "SKU is already in use");
    }
}
