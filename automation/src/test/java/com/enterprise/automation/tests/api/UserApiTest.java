package com.enterprise.automation.tests.api;

import static com.enterprise.automation.api.ApiAssertions.error;
import static com.enterprise.automation.api.ApiAssertions.expectStatus;
import static com.enterprise.automation.api.ApiAssertions.matchesSchema;
import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.ApiSession;
import com.enterprise.automation.api.JsonMapper;
import com.enterprise.automation.api.models.ErrorResponse;
import com.enterprise.automation.api.models.LoginResponse;
import com.enterprise.automation.api.models.UserRequest;
import com.enterprise.automation.api.models.UserResponse;
import com.enterprise.automation.config.Credentials;
import com.enterprise.automation.data.CleanupRegistry;
import com.enterprise.automation.data.TestDataFactory;
import com.enterprise.automation.data.UserData;
import com.enterprise.automation.tests.base.BaseApiTest;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;

/** {@code /api/users}: account management is admin-only, and roles take effect immediately. */
@Test(groups = {"api", "regression"})
public class UserApiTest extends BaseApiTest {

    /** Creates an account through the API and registers its deletion. */
    private UserResponse givenUser(UserData user) {
        UserResponse created = admin().users().createUser(user);
        CleanupRegistry.register("delete user " + created.username(), () -> admin().users().deleteUser(created.id()));
        return created;
    }

    /** An admin creates a user who can then sign in with the given role; the password is never returned. */
    public void adminCreatesAUserWhoCanSignInWithTheirRole() {
        UserData user = TestDataFactory.newUser("VIEWER");

        Response response = admin().users().create(UserRequest.from(user));

        expectStatus(response, 201);
        matchesSchema(response, "user");
        UserResponse created = JsonMapper.fromJson(response.asString(), UserResponse.class);
        CleanupRegistry.register("delete user " + created.username(), () -> admin().users().deleteUser(created.id()));
        assertThat(response.asString()).as("password never returned").doesNotContain(user.password());

        // The new account can sign in, sees itself, and has the VIEWER rights it was given
        ApiSession session = ApiSession.as(new Credentials(user.username(), user.password()));
        assertThat(session.users().currentUser().fullName()).isEqualTo(user.fullName());
        expectStatus(session.customers().create(Map.of()), 403);
    }

    /** Promoting a user to ADMIN gives a new login access to the admin-only users list. */
    public void promotingAUserGrantsWriteAccess() {
        UserData user = TestDataFactory.newUser("VIEWER");
        UserResponse created = givenUser(user);

        UserResponse promoted = JsonMapper.fromJson(
                expectStatus(admin().users().update(created.id(), Map.of("role", "ADMIN")), 200).asString(),
                UserResponse.class);

        // Promoted: a new login (role is read at login) can now use the admin-only users list
        assertThat(promoted.role()).isEqualTo("ADMIN");
        ApiSession session = ApiSession.as(new Credentials(user.username(), user.password()));
        expectStatus(session.users().list(), 200);
    }

    /** The users list holds at least the two seeded accounts. */
    public void adminListsUsers() {
        List<?> users = JsonMapper.fromJson(expectStatus(admin().users().list(), 200).asString(), List.class);

        // At least the two seeded accounts (other tests may add more in parallel)
        assertThat(users).hasSizeGreaterThanOrEqualTo(2);
    }

    /** A short password and an unknown role are 400; an existing username is refused too. */
    public void invalidAndDuplicateUsersAreRejected() {
        // Too-short password and a role that does not exist
        ErrorResponse invalid = error(expectStatus(admin().users().create(
                new UserRequest("someone", "short", "Some One", "OWNER")), 400));
        assertThat(invalid.fieldErrors())
                .containsEntry("password", "Password must be at least 8 characters")
                .containsKey("role");

        // A valid request with an existing username
        ErrorResponse duplicate = error(expectStatus(admin().users().create(
                new UserRequest("admin", "Long-enough-1", "Another Admin", "ADMIN")), 409));
        assertThat(duplicate.fieldErrors()).containsEntry("username", "Username is already taken");
    }

    /** An admin cannot delete their own account (409): that would lock them out. */
    public void adminCannotDeleteTheirOwnAccount() {
        // The admin's own id, via /users/me
        long ownId = admin().users().currentUser().id();

        // Refused: deleting yourself would lock you out
        ErrorResponse error = error(expectStatus(admin().users().delete(ownId), 409));

        assertThat(error.message()).isEqualTo("You cannot delete your own account");
    }

    /** Deletion revokes an active token and prevents another login. */
    public void deletedUserCannotSignIn() {
        UserData user = TestDataFactory.newUser("VIEWER");
        UserResponse created = admin().users().createUser(user);
        registerUserCleanup(created);
        Credentials credentials = new Credentials(user.username(), user.password());
        String firstToken = anonymous().auth().loginAs(credentials).token();
        String secondToken = anonymous().auth().loginAs(credentials).token();
        assertThat(secondToken).as("each login creates an independent active session").isNotEqualTo(firstToken);
        ApiSession firstSession = ApiSession.withToken(firstToken);
        ApiSession secondSession = ApiSession.withToken(secondToken);
        expectStatus(firstSession.customers().list(null), 200);
        expectStatus(secondSession.customers().list(null), 200);

        expectStatus(admin().users().delete(created.id()), 204);

        // The issued bearer token and old credentials both stop working
        expectStatus(firstSession.customers().list(null), 401);
        expectStatus(secondSession.customers().list(null), 401);
        expectStatus(anonymous().auth().login(Map.of("username", user.username(), "password", user.password())), 401);
    }

    /** Login and token authentication racing deletion may finish first, but cannot remain valid afterward. */
    @Test(groups = "regression")
    public void concurrentLoginAndAuthenticationWithDeletionCannotPreserveAccess() throws Exception {
        UserData user = TestDataFactory.newUser("VIEWER");
        UserResponse created = admin().users().createUser(user);
        registerUserCleanup(created);
        String token = anonymous().auth().loginAs(new Credentials(user.username(), user.password())).token();
        ApiSession userSession = ApiSession.withToken(token);
        expectStatus(userSession.customers().list(null), 200);

        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService requests = Executors.newFixedThreadPool(3);
        try {
            Future<Response> authentication = requests.submit(() -> {
                awaitConcurrentStart(ready, start);
                return userSession.customers().list(null);
            });
            Future<Response> login = requests.submit(() -> {
                awaitConcurrentStart(ready, start);
                return anonymous().auth().login(Map.of("username", user.username(), "password", user.password()));
            });
            Future<Response> deletion = requests.submit(() -> {
                awaitConcurrentStart(ready, start);
                return admin().users().delete(created.id());
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).as("all requests are ready").isTrue();
            start.countDown();

            int authenticationStatus = authentication.get(10, TimeUnit.SECONDS).statusCode();
            Response loginResponse = login.get(10, TimeUnit.SECONDS);
            expectStatus(deletion.get(10, TimeUnit.SECONDS), 204);
            assertThat(authenticationStatus).isIn(200, 401);
            assertThat(loginResponse.statusCode()).isIn(200, 401);
            if (loginResponse.statusCode() == 200) {
                String racedToken = JsonMapper.fromJson(loginResponse.asString(), LoginResponse.class).token();
                expectStatus(ApiSession.withToken(racedToken).customers().list(null), 401);
            }
            expectStatus(userSession.customers().list(null), 401);
        } finally {
            start.countDown();
            requests.shutdownNow();
        }
    }

    private void registerUserCleanup(UserResponse user) {
        CleanupRegistry.register("delete user " + user.username(), () -> {
            Response current = admin().users().get(user.id());
            if (current.statusCode() == 200) {
                assertThat(admin().users().delete(user.id()).statusCode()).isIn(204, 404);
            } else {
                assertThat(current.statusCode()).isEqualTo(404);
            }
        });
    }

    private static void awaitConcurrentStart(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to race login/authentication with deletion");
        }
    }
    public void roleDemotionRevokesEveryExistingAdminSession() {
        UserData user = TestDataFactory.newUser("ADMIN");
        UserResponse created = givenUser(user);
        Credentials credentials = new Credentials(user.username(), user.password());
        ApiSession first = ApiSession.withToken(anonymous().auth().loginAs(credentials).token());
        ApiSession second = ApiSession.withToken(anonymous().auth().loginAs(credentials).token());
        expectStatus(first.users().list(), 200);
        expectStatus(admin().users().update(created.id(), Map.of("role", "VIEWER")), 200);
        expectStatus(first.users().list(), 401);
        expectStatus(second.users().list(), 401);
        ApiSession fresh = ApiSession.withToken(anonymous().auth().loginAs(credentials).token());
        expectStatus(fresh.users().list(), 403);
        expectStatus(fresh.customers().list(null), 200);
    }

}
