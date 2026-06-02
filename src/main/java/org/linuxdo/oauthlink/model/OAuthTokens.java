package org.linuxdo.oauthlink.model;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Short-lived in-memory token state.
 * INTERNAL ONLY — accessToken must never be exposed through public API.
 */
public record OAuthTokens(String accessToken, Instant expiresAt) {

    /**
     * Checks if the token is expired given a clock and skew duration.
     */
    public boolean isExpired(Clock clock, Duration skew) {
        return Instant.now(clock).isAfter(expiresAt.plus(skew));
    }
}
