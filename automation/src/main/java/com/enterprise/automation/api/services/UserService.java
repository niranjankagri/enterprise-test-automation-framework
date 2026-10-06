package com.enterprise.automation.api.services;

import com.enterprise.automation.api.ApiAssertions;
import com.enterprise.automation.api.ApiClient;
import com.enterprise.automation.api.models.UserRequest;
import com.enterprise.automation.api.models.UserResponse;
import com.enterprise.automation.data.UserData;
import io.restassured.response.Response;
import java.util.Map;

/** {@code /api/users}: back-office accounts. */
public final class UserService extends BaseService {

    private static final String PATH = "/users";

    public UserService(ApiClient client) {
        super(client);
    }

    // ---- raw calls ----

    /** {@code GET /users/me}: the signed-in account (any role). */
    public Response me() {
        return client.get(PATH + "/me");
    }

    /** {@code GET /users} (ADMIN only). */
    public Response list() {
        return client.get(PATH);
    }

    /** {@code GET /users/{id}} (ADMIN only). */
    public Response get(long id) {
        return client.get(PATH + "/" + id);
    }

    /** {@code POST /users} (ADMIN only). */
    public Response create(Object body) {
        return client.post(PATH, body);
    }

    /** {@code PATCH /users/{id}}: full name and/or role. */
    public Response update(long id, Map<String, ?> changes) {
        return client.patch(PATH + "/" + id, changes);
    }

    /** {@code DELETE /users/{id}}. */
    public Response delete(long id) {
        return client.delete(PATH + "/" + id);
    }

    // ---- typed happy paths ----

    /** Creates the account, expects 201. */
    public UserResponse createUser(UserData user) {
        return expect(create(UserRequest.from(user)), 201, UserResponse.class);
    }

    /** The signed-in account, expects 200. */
    public UserResponse currentUser() {
        return expect(me(), 200, UserResponse.class);
    }

    /** Deletes, expects 204. */
    public void deleteUser(long id) {
        ApiAssertions.expectStatus(delete(id), 204);
    }
}
