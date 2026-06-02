package org.linuxdo.oauthlink.model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PendingAuthorization record.
 */
class PendingAuthorizationTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneId.of("UTC"));

    private static LinuxDoProfile createProfile() {
        return new LinuxDoProfile("user-123", "testuser", "Test User");
    }

    private static OAuthTokens createTokens() {
        return new OAuthTokens("token-abc", Instant.now(FIXED_CLOCK).plusSeconds(3600));
    }

    @Test
    void isExpired_FutureExpiry_ShouldReturnFalse() {
        Instant futureExpiry = Instant.now(FIXED_CLOCK).plusSeconds(300);
        PendingAuthorization pending = new PendingAuthorization(
                "ABCD1234", createProfile(), createTokens(), futureExpiry);

        assertFalse(pending.isExpired(FIXED_CLOCK));
    }

    @Test
    void isExpired_PastExpiry_ShouldReturnTrue() {
        Instant pastExpiry = Instant.now(FIXED_CLOCK).minusSeconds(1);
        PendingAuthorization pending = new PendingAuthorization(
                "ABCD1234", createProfile(), createTokens(), pastExpiry);

        assertTrue(pending.isExpired(FIXED_CLOCK));
    }

    @Test
    void isExpired_ExactExpiry_ShouldReturnFalse() {
        // At exact expiry, isAfter is false, so not yet expired
        Instant exactExpiry = Instant.now(FIXED_CLOCK);
        PendingAuthorization pending = new PendingAuthorization(
                "ABCD1234", createProfile(), createTokens(), exactExpiry);

        assertFalse(pending.isExpired(FIXED_CLOCK));
    }

    @Test
    void record_AllFields_ShouldBeAccessible() {
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens();
        Instant expiresAt = Instant.now(FIXED_CLOCK).plusSeconds(300);

        PendingAuthorization pending = new PendingAuthorization("CODE1234", profile, tokens, expiresAt);

        assertEquals("CODE1234", pending.linkCode());
        assertSame(profile, pending.profile());
        assertSame(tokens, pending.tokens());
        assertEquals(expiresAt, pending.expiresAt());
    }
}
