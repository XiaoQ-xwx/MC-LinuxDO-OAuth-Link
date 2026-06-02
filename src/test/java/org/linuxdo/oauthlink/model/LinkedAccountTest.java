package org.linuxdo.oauthlink.model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LinkedAccount record.
 */
class LinkedAccountTest {

    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneId.of("UTC"));
    private static final Duration SKEW = Duration.ofSeconds(60);

    @Test
    void isTokenExpired_ExpiredToken_ShouldReturnTrue() {
        Instant pastExpiry = Instant.now(FIXED_CLOCK).minusSeconds(3600);
        LinkedAccount account = new LinkedAccount(
                PLAYER_ID, "TestPlayer", "linuxdo-123", "testuser",
                Instant.now(FIXED_CLOCK).minusSeconds(7200), pastExpiry);

        assertTrue(account.isTokenExpired(FIXED_CLOCK, SKEW));
    }

    @Test
    void isTokenExpired_FutureToken_ShouldReturnFalse() {
        Instant futureExpiry = Instant.now(FIXED_CLOCK).plusSeconds(3600);
        LinkedAccount account = new LinkedAccount(
                PLAYER_ID, "TestPlayer", "linuxdo-123", "testuser",
                Instant.now(FIXED_CLOCK), futureExpiry);

        assertFalse(account.isTokenExpired(FIXED_CLOCK, SKEW));
    }

    @Test
    void isTokenExpired_JustExpiredBeyondSkew_ShouldReturnTrue() {
        // Token expired exactly at current time, but skew pushes it past
        Instant exactExpiry = Instant.now(FIXED_CLOCK).minusSeconds(61); // 61s ago, skew is 60s
        LinkedAccount account = new LinkedAccount(
                PLAYER_ID, "TestPlayer", "linuxdo-123", "testuser",
                Instant.now(FIXED_CLOCK), exactExpiry);

        assertTrue(account.isTokenExpired(FIXED_CLOCK, SKEW));
    }

    @Test
    void isTokenExpired_ExpiredButWithinSkew_ShouldReturnFalse() {
        // Token expired 30s ago but skew is 60s — still considered valid
        Instant withinSkew = Instant.now(FIXED_CLOCK).minusSeconds(30);
        LinkedAccount account = new LinkedAccount(
                PLAYER_ID, "TestPlayer", "linuxdo-123", "testuser",
                Instant.now(FIXED_CLOCK), withinSkew);

        assertFalse(account.isTokenExpired(FIXED_CLOCK, SKEW));
    }

    @Test
    void isActive_ActiveAccount_ShouldReturnTrue() {
        Instant futureExpiry = Instant.now(FIXED_CLOCK).plusSeconds(3600);
        LinkedAccount account = new LinkedAccount(
                PLAYER_ID, "TestPlayer", "linuxdo-123", "testuser",
                Instant.now(FIXED_CLOCK), futureExpiry);

        assertTrue(account.isActive(FIXED_CLOCK, SKEW));
    }

    @Test
    void isActive_ExpiredAccount_ShouldReturnFalse() {
        Instant pastExpiry = Instant.now(FIXED_CLOCK).minusSeconds(3600);
        LinkedAccount account = new LinkedAccount(
                PLAYER_ID, "TestPlayer", "linuxdo-123", "testuser",
                Instant.now(FIXED_CLOCK), pastExpiry);

        assertFalse(account.isActive(FIXED_CLOCK, SKEW));
    }

    @Test
    void record_AllFields_ShouldBeAccessible() {
        Instant linkedAt = Instant.now(FIXED_CLOCK);
        Instant expiresAt = linkedAt.plusSeconds(3600);
        LinkedAccount account = new LinkedAccount(
                PLAYER_ID, "TestPlayer", "ld-456", "testuser", linkedAt, expiresAt);

        assertEquals(PLAYER_ID, account.playerId());
        assertEquals("TestPlayer", account.playerName());
        assertEquals("ld-456", account.linuxDoId());
        assertEquals("testuser", account.linuxDoUsername());
        assertEquals(linkedAt, account.linkedAt());
        assertEquals(expiresAt, account.tokenExpiresAt());
    }
}
