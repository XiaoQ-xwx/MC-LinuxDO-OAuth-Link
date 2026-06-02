package org.linuxdo.oauthlink.oauth;

import org.linuxdo.oauthlink.model.LinuxDoProfile;
import org.linuxdo.oauthlink.model.OAuthTokens;
import org.linuxdo.oauthlink.model.PendingAuthorization;
import org.linuxdo.oauthlink.util.OAuthCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PendingOAuthRegistry — state machine logic.
 *
 * <p>Uses a mutable Clock for deterministic, controllable time-based assertions.
 */
class PendingOAuthRegistryTest {

    private static final Instant BASE_TIME = Instant.parse("2026-06-01T12:00:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Duration STATE_TTL = Duration.ofSeconds(300);
    private static final Duration LINK_CODE_TTL = Duration.ofSeconds(300);

    private MutableClock clock;
    private OAuthCodeGenerator generator;
    private PendingOAuthRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(BASE_TIME, UTC);
        generator = new OAuthCodeGenerator();
        registry = new PendingOAuthRegistry(generator, clock, STATE_TTL, LINK_CODE_TTL);
    }

    // ── createState ──────────────────────────────────────────

    @Test
    void createState_ShouldReturnNonEmptyString() {
        String state = registry.createState(UUID.randomUUID(), "testPlayer");

        assertNotNull(state);
        assertFalse(state.isEmpty());
    }

    @Test
    void createState_ShouldProduceUniqueValues() {
        String state1 = registry.createState(UUID.randomUUID(), "player1");
        String state2 = registry.createState(UUID.randomUUID(), "player2");

        assertNotEquals(state1, state2);
    }

    @Test
    void createState_ShouldStorePlayerIdentity() {
        UUID playerId = UUID.randomUUID();
        String playerName = "Notch";

        String state = registry.createState(playerId, playerName);
        PendingOAuthRegistry.StateEntry entry = registry.consumeState(state);

        assertNotNull(entry);
        assertEquals(playerId, entry.playerId());
        assertEquals(playerName, entry.playerName());
    }

    // ── consumeState ─────────────────────────────────────────

    @Test
    void consumeState_ValidState_ShouldReturnStateEntry() {
        UUID playerId = UUID.randomUUID();
        String state = registry.createState(playerId, "testPlayer");

        PendingOAuthRegistry.StateEntry entry = registry.consumeState(state);

        assertNotNull(entry);
        assertEquals(playerId, entry.playerId());
        assertEquals("testPlayer", entry.playerName());
    }

    @Test
    void consumeState_UnknownState_ShouldReturnNull() {
        assertNull(registry.consumeState("nonexistent-state"));
    }

    @Test
    void consumeState_ConsumedState_SecondCallReturnsNull() {
        String state = registry.createState(UUID.randomUUID(), "testPlayer");

        assertNotNull(registry.consumeState(state));
        // State should be consumed — second call fails
        assertNull(registry.consumeState(state));
    }

    @Test
    void consumeState_ExpiredState_ShouldReturnNull() {
        String state = registry.createState(UUID.randomUUID(), "testPlayer");

        // Advance clock past TTL
        clock.advance(STATE_TTL.plusSeconds(1));

        assertNull(registry.consumeState(state),
                "Expired state should return null");
    }

    @Test
    void consumeState_JustWithinTtl_ShouldReturnEntry() {
        String state = registry.createState(UUID.randomUUID(), "testPlayer");

        // Advance clock to just within TTL
        clock.advance(STATE_TTL.minusSeconds(1));

        PendingOAuthRegistry.StateEntry entry = registry.consumeState(state);
        assertNotNull(entry, "State just within TTL should still be valid");
    }

    // ── storeAuthorization ───────────────────────────────────

    @Test
    void storeAuthorization_ShouldReturnLinkCode() {
        LinuxDoProfile profile = createProfile("user-1", "testuser");
        OAuthTokens tokens = createTokens("token-abc");

        String linkCode = registry.storeAuthorization(profile, tokens);

        assertNotNull(linkCode);
        assertEquals(8, linkCode.length());
    }

    @Test
    void storeAuthorization_MultipleCalls_ShouldProduceUniqueCodes() {
        LinuxDoProfile profile = createProfile("user-1", "testuser");
        OAuthTokens tokens = createTokens("token-abc");

        String code1 = registry.storeAuthorization(profile, tokens);
        String code2 = registry.storeAuthorization(profile, tokens);

        assertNotEquals(code1, code2);
    }

    // ── consumeCode ──────────────────────────────────────────

    @Test
    void consumeCode_ValidCode_ShouldReturnPendingAuthorization() {
        LinuxDoProfile profile = createProfile("user-1", "testuser");
        OAuthTokens tokens = createTokens("token-abc");
        String linkCode = registry.storeAuthorization(profile, tokens);

        PendingAuthorization pending = registry.consumeCode(linkCode);

        assertNotNull(pending);
        assertEquals(linkCode, pending.linkCode());
        assertEquals(profile, pending.profile());
        assertEquals(tokens, pending.tokens());
    }

    @Test
    void consumeCode_InvalidCode_ShouldReturnNull() {
        assertNull(registry.consumeCode("INVALID1"));
    }

    @Test
    void consumeCode_NullCode_ShouldThrowNullPointerException() {
        // ConcurrentHashMap.remove(null) throws NPE — documented behavior
        assertThrows(NullPointerException.class, () -> registry.consumeCode(null));
    }

    @Test
    void consumeCode_ConsumedCode_SecondCallReturnsNull() {
        LinuxDoProfile profile = createProfile("user-1", "testuser");
        OAuthTokens tokens = createTokens("token-abc");
        String linkCode = registry.storeAuthorization(profile, tokens);

        assertNotNull(registry.consumeCode(linkCode));
        // Code should be consumed
        assertNull(registry.consumeCode(linkCode));
    }

    @Test
    void consumeCode_ExpiredCode_ShouldReturnNull() {
        LinuxDoProfile profile = createProfile("user-1", "testuser");
        OAuthTokens tokens = createTokens("token-abc");
        String linkCode = registry.storeAuthorization(profile, tokens);

        // Advance clock past link code TTL
        clock.advance(LINK_CODE_TTL.plusSeconds(1));

        assertNull(registry.consumeCode(linkCode),
                "Expired link code should return null");
    }

    @Test
    void consumeCode_JustWithinCodeTtl_ShouldReturnEntry() {
        LinuxDoProfile profile = createProfile("user-1", "testuser");
        OAuthTokens tokens = createTokens("token-abc");
        String linkCode = registry.storeAuthorization(profile, tokens);

        // Advance clock to just within link code TTL
        clock.advance(LINK_CODE_TTL.minusSeconds(1));

        PendingAuthorization pending = registry.consumeCode(linkCode);
        assertNotNull(pending, "Link code just within TTL should still be valid");
    }

    // ── purgeExpired ─────────────────────────────────────────

    @Test
    void purgeExpired_ShouldRemoveExpiredStateEntries() {
        String state = registry.createState(UUID.randomUUID(), "testPlayer");

        // Advance past TTL and purge
        clock.advance(STATE_TTL.plusSeconds(10));
        registry.purgeExpired();

        assertNull(registry.consumeState(state),
                "Expired state should not be consumable after purge");
    }

    @Test
    void purgeExpired_ShouldNotRemoveValidEntries() {
        String state = registry.createState(UUID.randomUUID(), "testPlayer");

        // Advance only half TTL — state should still be valid
        clock.advance(Duration.ofSeconds(150));
        registry.purgeExpired();

        assertNotNull(registry.consumeState(state),
                "Non-expired state should survive purge");
    }

    @Test
    void purgeExpired_ShouldRemoveExpiredLinkCodes() {
        LinuxDoProfile profile = createProfile("user-1", "testuser");
        OAuthTokens tokens = createTokens("token-abc");
        String linkCode = registry.storeAuthorization(profile, tokens);

        // Advance past link code TTL and purge
        clock.advance(LINK_CODE_TTL.plusSeconds(10));
        registry.purgeExpired();

        assertNull(registry.consumeCode(linkCode),
                "Expired link code should not be consumable after purge");
    }

    @Test
    void purgeExpired_ShouldNotRemoveValidLinkCodes() {
        LinuxDoProfile profile = createProfile("user-1", "testuser");
        OAuthTokens tokens = createTokens("token-abc");
        String linkCode = registry.storeAuthorization(profile, tokens);

        // Advance only half TTL — code should still be valid
        clock.advance(Duration.ofSeconds(150));
        registry.purgeExpired();

        assertNotNull(registry.consumeCode(linkCode),
                "Non-expired link code should survive purge");
    }

    // ── integration: create → consume flow ──────────────────

    @Test
    void fullStateFlow_ShouldWorkEndToEnd() {
        UUID playerId = UUID.randomUUID();

        // 1. Create state for CSRF
        String state = registry.createState(playerId, "player1");
        assertNotNull(state);

        // 2. Consume it (simulating callback return)
        PendingOAuthRegistry.StateEntry entry = registry.consumeState(state);
        assertNotNull(entry);
        assertEquals(playerId, entry.playerId());

        // 3. Store authorization result
        LinuxDoProfile profile = createProfile("user-42", "player1");
        OAuthTokens tokens = createTokens("access-token-xyz");
        String linkCode = registry.storeAuthorization(profile, tokens);

        // 4. Player submits link code
        PendingAuthorization pending = registry.consumeCode(linkCode);
        assertNotNull(pending);
        assertEquals("player1", pending.profile().username());
        assertEquals("access-token-xyz", pending.tokens().accessToken());

        // 5. Code should be consumed (one-time use)
        assertNull(registry.consumeCode(linkCode));
    }

    @Test
    void fullFlow_ExpiredState_ThenManualCodeStillWorks() {
        UUID playerId = UUID.randomUUID();

        // Create state and let it expire
        String state = registry.createState(playerId, "player1");
        clock.advance(STATE_TTL.plusSeconds(1));

        // State should be expired
        assertNull(registry.consumeState(state));

        // But manual link code flow should still work independently
        LinuxDoProfile profile = createProfile("user-99", "player1");
        OAuthTokens tokens = createTokens("manual-token");
        String linkCode = registry.storeAuthorization(profile, tokens);

        PendingAuthorization pending = registry.consumeCode(linkCode);
        assertNotNull(pending);
        assertEquals("user-99", pending.profile().id());
    }

    // ── helpers ──────────────────────────────────────────────

    private LinuxDoProfile createProfile(String id, String username) {
        return new LinuxDoProfile(id, username, "Display " + username);
    }

    private OAuthTokens createTokens(String accessToken) {
        return new OAuthTokens(accessToken, BASE_TIME.plusSeconds(3600));
    }

    // ── mutable clock for deterministic time control ─────────

    /**
     * A Clock whose instant can be advanced programmatically,
     * enabling deterministic testing of time-based expiry logic.
     */
    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            MutableClock copy = new MutableClock(instant, zone);
            copy.instant = this.instant;
            return copy;
        }
    }
}
