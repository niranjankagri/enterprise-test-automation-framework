package com.enterprise.automation.api.services;

import com.enterprise.automation.api.ApiAssertions;
import com.enterprise.automation.api.ApiClient;
import com.enterprise.automation.api.models.ProductRequest;
import com.enterprise.automation.api.models.ProductResponse;
import com.enterprise.automation.data.ProductData;
import com.fasterxml.jackson.core.type.TypeReference;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;

/** {@code /api/products}. */
public final class ProductService extends BaseService {

    private static final String PATH = "/products";

    public ProductService(ApiClient client) {
        super(client);
    }

    // ---- raw calls ----

    public Response list(Map<String, ?> query) {
        return client.get(PATH, query);
    }

    public Response get(long id) {
        return client.get(PATH + "/" + id);
    }

    public Response create(Object body) {
        return client.post(PATH, body);
    }

    public Response replace(long id, Object body) {
        return client.put(PATH + "/" + id, body);
    }

    public Response update(long id, Map<String, ?> changes) {
        return client.patch(PATH + "/" + id, changes);
    }

    public Response delete(long id) {
        return client.delete(PATH + "/" + id);
    }

    // ---- typed happy paths ----

    public ProductResponse createProduct(ProductData product) {
        return expect(create(ProductRequest.from(product)), 201, ProductResponse.class);
    }

    public ProductResponse getProduct(long id) {
        return expect(get(id), 200, ProductResponse.class);
    }

    public List<ProductResponse> findProducts(Map<String, ?> query) {
        return expect(list(query), 200, new TypeReference<List<ProductResponse>>() { });
    }

    /** A catalogue product by SKU (active or not). */
    public ProductResponse getBySku(String sku) {
        return findProducts(Map.of("search", sku, "includeInactive", true)).stream()
                .filter(p -> p.sku().equals(sku)).findFirst()
                .orElseThrow(() -> new AssertionError("No product with SKU " + sku));
    }

    /** Deactivates the product (the API keeps products for order history). */
    public void deleteProduct(long id) {
        ApiAssertions.expectStatus(delete(id), 204);
    }
}
