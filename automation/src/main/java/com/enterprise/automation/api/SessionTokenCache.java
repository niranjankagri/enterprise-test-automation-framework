package com.enterprise.automation.api;

import com.enterprise.automation.api.models.LoginResponse;
import com.enterprise.automation.config.Credentials;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Expiry-aware tokens scoped to endpoint and credentials. No request is automatically replayed. */
public final class SessionTokenCache {

    private record Key(URI endpoint, Credentials credentials) {
    }

    private record Cached(String token, Instant expiresAt) {
    }

    private final Map<Key, Cached> tokens = new ConcurrentHashMap<>();
    private final Clock clock;

    /** An injectable clock makes expiry boundaries deterministic without sleeping. */
    public SessionTokenCache(Clock clock) {
        this.clock = clock;
    }

    /** Reuses a live token, or logs in once atomically per identity. */
    public String token(URI endpoint, Credentials credentials, Supplier<LoginResponse> login) {
        Instant now = clock.instant();
        tokens.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        return tokens.compute(new Key(endpoint, credentials), (key, previous) -> {
            if (previous != null && previous.expiresAt().isAfter(clock.instant())) {
                return previous;
            }
            Instant issuedAt = clock.instant();
            LoginResponse response = login.get();
            if (response.expiresIn() <= 0) {
                throw new IllegalStateException("Login returned a nonpositive token lifetime");
            }
            return new Cached(response.token(), issuedAt.plusSeconds(response.expiresIn()));
        }).token();
    }

    /** Explicit invalidation after logout, role changes or an externally restarted application. */
    public void forget(String username) {
        tokens.keySet().removeIf(key -> key.credentials().username().equals(username));
    }

    /** Clears all cached identities at an application lifecycle boundary. */
    public void clear() {
        tokens.clear();
    }
}
