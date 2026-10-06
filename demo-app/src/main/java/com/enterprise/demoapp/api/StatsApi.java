package com.enterprise.demoapp.api;

import com.enterprise.demoapp.Database;
import com.enterprise.demoapp.http.Request;
import com.enterprise.demoapp.http.Response;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@code GET /api/stats}: the dashboard figures. Revenue ignores cancelled orders. */
public final class StatsApi {

    private final Database database;

    public StatsApi(Database database) {
        this.database = database;
    }

    public Response get(Request request) {
        return Sql.run(database, c -> {
            Map<String, Object> stats = new LinkedHashMap<>();
            // Four figures in one round trip, as scalar sub-queries
            try (PreparedStatement ps = c.prepareStatement("SELECT"
                    + " (SELECT COUNT(*) FROM customers),"
                    + " (SELECT COUNT(*) FROM products WHERE active),"
                    + " (SELECT COUNT(*) FROM orders),"
                    // COALESCE: no orders yet -> revenue 0 instead of NULL
                    + " (SELECT COALESCE(SUM(total), 0) FROM orders WHERE status <> 'CANCELLED')");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                stats.put("customers", rs.getLong(1));
                stats.put("products", rs.getLong(2));
                stats.put("orders", rs.getLong(3));
                stats.put("revenue", rs.getBigDecimal(4));
            }
            return Response.ok(stats);
        });
    }
}
