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

    public Response me() {
        return client.get(PATH + "/me");
    }

    public Response list() {
        return client.get(PATH);
    }

    public Response get(long id) {
        return client.get(PATH + "/" + id);
    }

    public Response create(Object body) {
        return client.post(PATH, body);
    }

    public Response update(long id, Map<String, ?> changes) {
        return client.patch(PATH + "/" + id, changes);
    }

    public Response delete(long id) {
        return client.delete(PATH + "/" + id);
    }

    // ---- typed happy paths ----

    public UserResponse createUser(UserData user) {
        return expect(create(UserRequest.from(user)), 201, UserResponse.class);
    }

    public UserResponse currentUser() {
        return expect(me(), 200, UserResponse.class);
    }

    public void deleteUser(long id) {
        ApiAssertions.expectStatus(delete(id), 204);
    }
}
