package com.enterprise.automation.tests.db;

import static com.enterprise.automation.db.DatabaseAssertions.assertNoRow;
import static com.enterprise.automation.db.DatabaseAssertions.assertRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.automation.data.ProductData;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.db.ShopDatabase;
import com.enterprise.automation.tests.base.TestDataProviders;
import java.util.List;
import java.util.Map;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** The database layer itself: connection, parameterized queries, assertions. */
@Test(groups = {"db", "regression"})
public class DatabaseLayerTest {

    private ShopDatabase db;

    /** In a configuration method, so "no database access" skips the class instead of failing it. */
    @BeforeClass(alwaysRun = true)
    public void connect() {
        db = ShopDatabase.fromConfig();
    }

    @Test(groups = "smoke")
    public void connectsAndReadsTheSchema() {
        List<Map<String, Object>> tables = db.sql().queryForList(
                "SELECT LOWER(table_name) AS name FROM information_schema.tables WHERE table_schema = 'PUBLIC'"
                        + " ORDER BY name");

        assertThat(tables).extracting(t -> t.get("name"))
                .containsExactly("app_users", "customers", "order_items", "orders", "products");
    }

    @Test(dataProvider = "catalogue", dataProviderClass = TestDataProviders.class)
    public void catalogueRowsMatchTheReferenceData(String sku, ProductData expected) {
        assertRow(db.productBySku(sku), "product " + sku)
                .hasValue("name", expected.name())
                .hasValue("category", expected.category())
                .hasValue("price", expected.price())
                .hasValue("active", true);
    }

    public void parametersAreBoundNotConcatenated() {
        String hostile = "x' OR '1'='1";

        assertNoRow(db.customerByEmail(hostile), "customer with a quote in the email");
        assertThat(db.sql().count("SELECT COUNT(*) FROM customers WHERE email = ?", hostile)).isZero();
    }

    public void queryForOneRejectsSeveralRows() {
        assertThatThrownBy(() -> db.sql().queryForOne("SELECT * FROM products"))
                .hasMessageContaining("at most one row");
    }

    public void assertionFailuresShowTheRow() {
        assertThatThrownBy(() -> assertRow(db.productBySku("ACC-3002"), "product ACC-3002").hasValue("name", "Wrong"))
                .hasMessageContaining("product ACC-3002.name")
                .hasMessageContaining("Wireless Mouse");
    }

    public void catalogueSizeMatches() {
        long seeded = db.sql().count("SELECT COUNT(*) FROM products WHERE sku NOT LIKE 'TST-%'");

        assertThat(seeded).isEqualTo(TestDataFactory.catalogue().size());
    }
}
