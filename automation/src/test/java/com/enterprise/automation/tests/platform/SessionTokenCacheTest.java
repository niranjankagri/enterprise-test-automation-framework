package com.enterprise.automation.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.automation.api.SessionTokenCache;
import com.enterprise.automation.api.models.LoginResponse;
import com.enterprise.automation.config.Credentials;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.testng.annotations.Test;

/** Session reuse must respect expiry, endpoints, changed credentials and explicit invalidation. */
@Test(groups = "unit")
public class SessionTokenCacheTest {

    public void cacheExpiresAtDeadlineAndIsScopedToIdentityAndEndpoint() {
        MutableClock clock = new MutableClock();
        SessionTokenCache cache = new SessionTokenCache(clock);
        AtomicInteger logins = new AtomicInteger();
        Supplier<LoginResponse> login = () -> new LoginResponse("synthetic-" + logins.incrementAndGet(),
                "Bearer", 10, "cache-user", "Cache User", "VIEWER");
        URI endpoint = URI.create("http://one.example.test/api");
        Credentials user = new Credentials("cache-user", "synthetic-one");
        String first = cache.token(endpoint, user, login);
        assertThat(cache.token(endpoint, user, login)).isEqualTo(first);
        clock.now = clock.now.plusSeconds(10);
        assertThat(cache.token(endpoint, user, login)).isNotEqualTo(first);
        cache.token(URI.create("http://two.example.test/api"), user, login);
        cache.token(endpoint, new Credentials("cache-user", "synthetic-two"), login);
        assertThat(logins).hasValue(4);
        cache.forget(user.username());
        cache.token(endpoint, user, login);
        assertThat(logins).hasValue(5);
        cache.clear();
        cache.token(endpoint, user, login);
        assertThat(logins).hasValue(6);
    }

    public void simultaneousRequestsLoginOnlyOncePerIdentity() {
        SessionTokenCache cache = new SessionTokenCache(Clock.systemUTC());
        AtomicInteger count = new AtomicInteger();
        URI endpoint = URI.create("http://parallel.example.test/api");
        Credentials user = new Credentials("parallel-user", "synthetic-cache-value");
        java.util.Set<String> results = java.util.stream.IntStream.range(0, 20).parallel()
                .mapToObj(index -> cache.token(endpoint, user,
                        () -> new LoginResponse("synthetic-" + count.incrementAndGet(), "Bearer", 60,
                                "parallel-user", "Parallel User", "VIEWER")))
                .collect(java.util.stream.Collectors.toSet());
        assertThat(results).hasSize(1);
        assertThat(count).hasValue(1);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
