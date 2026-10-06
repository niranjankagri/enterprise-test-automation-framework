package com.enterprise.automation.tests.api;

import static com.enterprise.automation.api.ApiAssertions.error;
import static com.enterprise.automation.api.ApiAssertions.expectStatus;
import static com.enterprise.automation.api.ApiAssertions.matchesSchema;
import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.ApiSession;
import com.enterprise.automation.api.JsonMapper;
import com.enterprise.automation.api.models.ErrorResponse;
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
import org.testng.annotations.Test;

/** {@code /api/users}: account management is admin-only, and roles take effect immediately. */
@Test(groups = {"api", "regression"})
public class UserApiTest extends BaseApiTest {

    private UserResponse givenUser(UserData user) {
        UserResponse created = admin().users().createUser(user);
        CleanupRegistry.register("delete user " + created.username(), () -> admin().users().deleteUser(created.id()));
        return created;
    }

    public void adminCreatesAUserWhoCanSignInWithTheirRole() {
        UserData user = TestDataFactory.newUser("VIEWER");

        Response response = admin().users().create(UserRequest.from(user));

        expectStatus(response, 201);
        matchesSchema(response, "user");
        UserResponse created = JsonMapper.fromJson(response.asString(), UserResponse.class);
        CleanupRegistry.register("delete user " + created.username(), () -> admin().users().deleteUser(created.id()));
        assertThat(response.asString()).as("password never returned").doesNotContain(user.password());

        ApiSession session = ApiSession.as(new Credentials(user.username(), user.password()));
        assertThat(session.users().currentUser().fullName()).isEqualTo(user.fullName());
        expectStatus(session.customers().create(Map.of()), 403);
    }

    public void promotingAUserGrantsWriteAccess() {
        UserData user = TestDataFactory.newUser("VIEWER");
        UserResponse created = givenUser(user);

        UserResponse promoted = JsonMapper.fromJson(
                expectStatus(admin().users().update(created.id(), Map.of("role", "ADMIN")), 200).asString(),
                UserResponse.class);

        assertThat(promoted.role()).isEqualTo("ADMIN");
        ApiSession session = ApiSession.as(new Credentials(user.username(), user.password()));
        expectStatus(session.users().list(), 200);
    }

    public void adminListsUsers() {
        List<?> users = JsonMapper.fromJson(expectStatus(admin().users().list(), 200).asString(), List.class);

        assertThat(users).hasSizeGreaterThanOrEqualTo(2);
    }

    public void invalidAndDuplicateUsersAreRejected() {
        ErrorResponse invalid = error(expectStatus(admin().users().create(
                new UserRequest("someone", "short", "Some One", "OWNER")), 400));
        assertThat(invalid.fieldErrors())
                .containsEntry("password", "Password must be at least 8 characters")
                .containsKey("role");

        ErrorResponse duplicate = error(expectStatus(admin().users().create(
                new UserRequest("admin", "Long-enough-1", "Another Admin", "ADMIN")), 409));
        assertThat(duplicate.fieldErrors()).containsEntry("username", "Username is already taken");
    }

    public void adminCannotDeleteTheirOwnAccount() {
        long ownId = admin().users().currentUser().id();

        ErrorResponse error = error(expectStatus(admin().users().delete(ownId), 409));

        assertThat(error.message()).isEqualTo("You cannot delete your own account");
    }

    public void deletedUserCannotSignIn() {
        UserData user = TestDataFactory.newUser("VIEWER");
        UserResponse created = admin().users().createUser(user);

        expectStatus(admin().users().delete(created.id()), 204);

        expectStatus(anonymous().auth().login(Map.of("username", user.username(), "password", user.password())), 401);
    }
}
