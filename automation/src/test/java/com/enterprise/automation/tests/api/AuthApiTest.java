package com.enterprise.automation.tests.api;

import static com.enterprise.automation.api.ApiAssertions.error;
import static com.enterprise.automation.api.ApiAssertions.expectStatus;
import static com.enterprise.automation.api.ApiAssertions.matchesSchema;
import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.ApiClient;
import com.enterprise.automation.api.ApiSession;
import com.enterprise.automation.api.JsonMapper;
import com.enterprise.automation.api.models.ErrorResponse;
import com.enterprise.automation.api.models.LoginRequest;
import com.enterprise.automation.api.models.LoginResponse;
import com.enterprise.automation.tests.base.BaseApiTest;
import io.restassured.response.Response;
import java.util.Map;
import org.testng.annotations.Test;

/** Authentication (who are you?) and authorization (what may you do?). */
@Test(groups = {"api", "regression"})
public class AuthApiTest extends BaseApiTest {

    /** Login with the admin credentials returns a Bearer token that matches the login contract. */
    @Test(groups = "smoke")
    public void tc201_adminLogsInAndGetsABearerToken() {
        // Log in without any token, with the configured admin credentials
        Response response = anonymous().auth().login(LoginRequest.of(config().admin()));

        // Status, contract (schema), content type, then the values that matter
        expectStatus(response, 200);
        matchesSchema(response, "login-response");
        assertThat(response.getContentType()).startsWith("application/json");
        LoginResponse login = JsonMapper.fromJson(response.asString(), LoginResponse.class);
        assertThat(login.tokenType()).isEqualTo("Bearer");
        assertThat(login.role()).isEqualTo("ADMIN");
        assertThat(login.expiresIn()).isEqualTo(3600);
    }

    /**
     * A wrong password and an unknown user get the same 401 message, so the API does not reveal which usernames
     * exist.
     */
    public void tc202_wrongPasswordGives401WithoutRevealingWhichPartWasWrong() {
        // A known user with a wrong password, and an unknown user
        ErrorResponse wrongPassword = error(expectStatus(
                anonymous().auth().login(new LoginRequest("admin", "wrong")), 401));
        ErrorResponse unknownUser = error(expectStatus(
                anonymous().auth().login(new LoginRequest("nobody", "wrong")), 401));

        // Same message for both: an attacker cannot find out which usernames exist
        assertThat(wrongPassword.message()).isEqualTo("Invalid username or password")
                .isEqualTo(unknownUser.message());
    }

    /** A login without username and password reports both missing fields in one 400. */
    public void tc203_missingCredentialsGive400PerField() {
        // An empty JSON object: no username, no password
        ErrorResponse error = error(expectStatus(anonymous().auth().login(Map.of()), 400));

        // Both problems reported in one response
        assertThat(error.fieldErrors()).containsEntry("username", "Username is required")
                .containsEntry("password", "Password is required");
    }

    /**
     * The client's X-Request-Id comes back for correlation; an unsafe value is replaced by an id the server makes.
     */
    public void tc204_theClientsRequestIdIsUsedForCorrelation() {
        // ApiClient sends an X-Request-Id with every call; the application logs and returns it
        Response traced = ApiClient.anonymous().withRequestId("trace-auth-0001").get("/customers");
        // An id that is not a safe token is not echoed back: the application makes its own
        Response unsafe = ApiClient.anonymous().withRequestId("<script>alert(1)</script>").get("/customers");

        assertThat(traced.getHeader(ApiClient.REQUEST_ID)).isEqualTo("trace-auth-0001");
        assertThat(unsafe.getHeader(ApiClient.REQUEST_ID)).matches("[0-9a-f-]{36}");
    }

    /** A protected endpoint without a token is 401, naming the problem and the path. */
    public void tc205_requestsWithoutTokenGet401() {
        // A protected endpoint called without Authorization header
        ErrorResponse error = error(expectStatus(anonymous().customers().list(null), 401));

        // The error names the problem and the path
        assertThat(error.message()).isEqualTo("Missing bearer token");
        assertThat(error.path()).isEqualTo("/api/customers");
    }

    /** A token the server never issued is 401. */
    public void tc206_invalidTokenGets401() {
        // A well-formed header with a token the server never issued
        ErrorResponse unknown = error(expectStatus(ApiSession.withToken("not-a-real-token").customers().list(null), 401));

        assertThat(unknown.message()).isEqualTo("Invalid or expired token");

        // A malformed token is rejected with the same public response and no parser details.
        ErrorResponse malformed = error(expectStatus(ApiSession.withToken("%%%not-a-token").customers().list(null), 401));
        assertThat(malformed.message()).isEqualTo("Invalid or expired token");
    }

    /** After logout the same token is rejected. */
    public void tc207_loggedOutTokenStopsWorking() {
        // A fresh token of its own (not the cached one other tests use), proven to work
        String token = anonymous().auth().loginAs(config().viewer()).token();
        ApiSession session = ApiSession.withToken(token);
        expectStatus(session.customers().list(null), 200);

        // Log out with it
        expectStatus(session.auth().logout(), 204);

        // The same token is rejected from now on
        expectStatus(session.customers().list(null), 401);
    }

    /** The viewer role can read but every write, and the users list, is 403. */
    @Test(groups = "sanity")
    public void tc208_viewerCanReadButNotWrite() {
        // Reads are allowed
        expectStatus(viewer().customers().list(null), 200);
        expectStatus(viewer().products().list(Map.of()), 200);

        // Every kind of write, and the admin-only users list, is forbidden (403 comes before validation)
        ErrorResponse error = error(expectStatus(viewer().customers().create(Map.of("firstName", "X")), 403));
        assertThat(error.message()).isEqualTo("This action needs the ADMIN role");
        expectStatus(viewer().products().delete(1), 403);
        expectStatus(viewer().orders().changeStatus(1, "SHIPPED"), 403);
        expectStatus(viewer().users().list(), 403);
    }

    /** /users/me returns the signed-in user, and the body never contains a password. */
    public void tc209_currentUserIsReturnedWithoutPassword() {
        Response me = expectStatus(viewer().users().me(), 200);

        // The user schema has no password field, and the raw body contains no password at all
        matchesSchema(me, "user");
        assertThat(me.asString()).doesNotContainIgnoringCase("password");
        assertThat(viewer().users().currentUser().role()).isEqualTo("VIEWER");
    }
}
