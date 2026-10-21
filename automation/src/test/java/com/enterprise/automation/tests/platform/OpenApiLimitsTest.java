package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.demoapp.Json;
import com.enterprise.demoapp.http.Request;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import org.testng.annotations.Test;

/** Request bounds in the checked-in API contract stay aligned with runtime limits. */
@Test(groups = "unit")
public class OpenApiLimitsTest {
    public void specificationRecordsStorageAndRequestBounds() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/static/openapi.json")) {
            assertThat(input).isNotNull();
            JsonNode schemas = Json.MAPPER.readTree(input).path("components").path("schemas");
            for (String schema : java.util.List.of("ProductInput", "ProductPatch")) {
                JsonNode price = schemas.path(schema).path("properties").path("price");
                assertThat(price.path("maximum").decimalValue()).isEqualByComparingTo("99999999.99");
                assertThat(price.path("multipleOf").decimalValue()).isEqualByComparingTo("0.01");
            }
            JsonNode items = schemas.path("OrderInput").path("properties").path("items");
            assertThat(items.path("maxItems").asInt()).isEqualTo(1000);
            assertThat(items.path("items").path("properties").path("quantity").path("maximum").asInt())
                    .isEqualTo(Integer.MAX_VALUE);
            assertThat(schemas.path("OrderInput").path("description").asText())
                    .contains(Integer.toString(Request.MAX_BODY_BYTES));
        }
    }
}
