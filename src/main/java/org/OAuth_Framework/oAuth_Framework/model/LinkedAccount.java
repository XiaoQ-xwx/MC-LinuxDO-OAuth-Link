package org.OAuth_Framework.oAuth_Framework.model;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted account link metadata.
 * Exposed to downstream plugins via the public API.
 * Does NOT contain access tokens.
 */
public record LinkedAccount(
        UUID playerId,
        String playerName,
        String linuxDoId,
        String linuxDoUsername,
        Instant linkedAt,
        Instant tokenExpiresAt) {

    /**
     * Checks if the linked account's token has expired.
     */
    public boolean isTokenExpired(Clock clock, Duration skew) {
        return Instant.now(clock).isAfter(tokenExpiresAt.plus(skew));
    }

    /**
     * Returns true if this account is still considered active (token not expired).
     */
    public boolean isActive(Clock clock, Duration skew) {
        return !isTokenExpired(clock, skew);
    }
}
