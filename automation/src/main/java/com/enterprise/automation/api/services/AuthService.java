package com.enterprise.automation.api.services;

import com.enterprise.automation.api.ApiClient;
import com.enterprise.automation.api.models.LoginRequest;
import com.enterprise.automation.api.models.LoginResponse;
import com.enterprise.automation.config.Credentials;
import io.restassured.response.Response;
import java.util.Map;

/** {@code /api/auth}: login and logout. */
public final class AuthService extends BaseService {

    public AuthService(ApiClient client) {
        super(client);
    }

    /** {@code POST /auth/login} with username and password. */
    public Response login(LoginRequest request) {
        return client.post("/auth/login", request);
    }

    /** Raw body, e.g. {@code Map.of("username", "admin")} to leave the password out. */
    public Response login(Map<String, ?> body) {
        return client.post("/auth/login", body);
    }

    /** Logs in, expects 200, returns the token and user data. */
    public LoginResponse loginAs(Credentials credentials) {
        return expect(login(LoginRequest.of(credentials)), 200, LoginResponse.class);
    }

    /** {@code POST /auth/logout}: revokes the client's token. */
    public Response logout() {
        // The endpoint takes no data; an empty JSON object is sent as the body
        return client.post("/auth/logout", Map.of());
    }
}
