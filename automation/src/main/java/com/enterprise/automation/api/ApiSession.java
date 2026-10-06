package com.enterprise.automation.api;

import com.enterprise.automation.api.services.AuthService;
import com.enterprise.automation.api.services.CustomerService;
import com.enterprise.automation.api.services.OrderService;
import com.enterprise.automation.api.services.ProductService;
import com.enterprise.automation.api.services.UserService;
import com.enterprise.automation.config.ConfigManager;
import com.enterprise.automation.config.Credentials;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The API as seen by one account: all services, authenticated with that account's token.
 *
 * <pre>{@code
 * ApiSession admin = ApiSession.admin();
 * CustomerResponse created = admin.customers().createCustomer(TestDataFactory.newCustomer());
 * }</pre>
 *
 * <p>Tokens are cached per username for the run (the API issues them for an hour), so thousands
 * of calls do not mean thousands of logins. The cache is thread-safe; a session object holds no
 * mutable state.
 */
public final class ApiSession {

    // username -> bearer token, shared by all threads of the run
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    // Authenticated (or anonymous) client used by every service of this session
    private final ApiClient client;

    private ApiSession(ApiClient client) {
        this.client = client;
    }

    /** Signed in as the configured admin. */
    public static ApiSession admin() {
        return as(ConfigManager.config().admin());
    }

    /** Signed in as the configured read-only viewer. */
    public static ApiSession viewer() {
        return as(ConfigManager.config().viewer());
    }

    /** Signed in as {@code credentials} (token reused if this account already signed in). */
    public static ApiSession as(Credentials credentials) {
        // computeIfAbsent is atomic: parallel threads asking for the same account log in only once
        String token = TOKENS.computeIfAbsent(credentials.username(),
                user -> new AuthService(ApiClient.anonymous()).loginAs(credentials).token());
        return new ApiSession(ApiClient.anonymous().withToken(token));
    }

    /** With an explicit token, e.g. an invalid or logged-out one for negative tests. */
    public static ApiSession withToken(String token) {
        return new ApiSession(ApiClient.anonymous().withToken(token));
    }

    /** Not signed in at all. */
    public static ApiSession anonymous() {
        return new ApiSession(ApiClient.anonymous());
    }

    /** Forgets a cached token, e.g. after a test logged it out. */
    public static void forgetToken(String username) {
        TOKENS.remove(username);
    }

    /** The authenticated client itself, for protocol-level negative tests (malformed ids or JSON). */
    public ApiClient client() {
        return client;
    }

    // Services are tiny wrappers around the client: created on demand, nothing to cache

    public AuthService auth() {
        return new AuthService(client);
    }

    public UserService users() {
        return new UserService(client);
    }

    public CustomerService customers() {
        return new CustomerService(client);
    }

    public ProductService products() {
        return new ProductService(client);
    }

    public OrderService orders() {
        return new OrderService(client);
    }
}
