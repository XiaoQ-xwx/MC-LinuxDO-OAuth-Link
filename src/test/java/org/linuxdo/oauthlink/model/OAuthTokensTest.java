package org.linuxdo.oauthlink.model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OAuthTokens record.
 */
class OAuthTokensTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneId.of("UTC"));

    @Test
    void isExpired_ExpiredToken_ShouldReturnTrue() {
        Instant pastExpiry = Instant.now(FIXED_CLOCK).minusSeconds(3600);
        OAuthTokens tokens = new OAuthTokens("access-token-abc", pastExpiry);

        assertTrue(tokens.isExpired(FIXED_CLOCK, Duration.ofSeconds(60)));
    }

    @Test
    void isExpired_FutureToken_ShouldReturnFalse() {
        Instant futureExpiry = Instant.now(FIXED_CLOCK).plusSeconds(3600);
        OAuthTokens tokens = new OAuthTokens("access-token-abc", futureExpiry);

        assertFalse(tokens.isExpired(FIXED_CLOCK, Duration.ofSeconds(60)));
    }

    @Test
    void isExpired_WithinSkew_ShouldReturnFalse() {
        // Expired 30s ago, but 60s skew makes it still valid
        Instant withinSkew = Instant.now(FIXED_CLOCK).minusSeconds(30);
        OAuthTokens tokens = new OAuthTokens("access-token-abc", withinSkew);

        assertFalse(tokens.isExpired(FIXED_CLOCK, Duration.ofSeconds(60)));
    }

    @Test
    void isExpired_BeyondSkew_ShouldReturnTrue() {
        // Expired 61s ago, and skew is only 60s
        Instant beyondSkew = Instant.now(FIXED_CLOCK).minusSeconds(61);
        OAuthTokens tokens = new OAuthTokens("access-token-abc", beyondSkew);

        assertTrue(tokens.isExpired(FIXED_CLOCK, Duration.ofSeconds(60)));
    }

    @Test
    void isExpired_ExactExpiryWithoutSkew_ShouldReturnTrue() {
        // Expired exactly at current time with zero skew
        Instant exactExpiry = Instant.now(FIXED_CLOCK);
        OAuthTokens tokens = new OAuthTokens("access-token-abc", exactExpiry);

        // isAfter is strict: now.isAfter(now) is false, but now.isAfter(now.plus(Duration.ZERO)) is false
        // So at exact expiry, it's NOT expired (since !now.isAfter(now))
        assertFalse(tokens.isExpired(FIXED_CLOCK, Duration.ZERO));
    }

    @Test
    void isExpired_ZeroSkew_ShouldNotExtendValidity() {
        Instant pastByOneSec = Instant.now(FIXED_CLOCK).minusSeconds(1);
        OAuthTokens tokens = new OAuthTokens("access-token-abc", pastByOneSec);

        assertTrue(tokens.isExpired(FIXED_CLOCK, Duration.ZERO));
    }

    @Test
    void accessToken_ShouldBeAccessible() {
        OAuthTokens tokens = new OAuthTokens("my-secret-token", Instant.now(FIXED_CLOCK).plusSeconds(3600));

        assertEquals("my-secret-token", tokens.accessToken());
    }
}
