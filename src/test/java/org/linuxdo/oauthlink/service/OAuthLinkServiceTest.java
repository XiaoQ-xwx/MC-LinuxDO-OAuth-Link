package org.linuxdo.oauthlink.service;

import org.linuxdo.oauthlink.config.OAuthConfig;
import org.linuxdo.oauthlink.event.PlayerOAuthFailEvent;
import org.linuxdo.oauthlink.event.PlayerOAuthSuccessEvent;
import org.linuxdo.oauthlink.event.PlayerOAuthUnlinkEvent;
import org.linuxdo.oauthlink.http.CallbackHttpServer;
import org.linuxdo.oauthlink.model.LinkedAccount;
import org.linuxdo.oauthlink.model.LinuxDoProfile;
import org.linuxdo.oauthlink.model.OAuthTokens;
import org.linuxdo.oauthlink.model.PendingAuthorization;
import org.linuxdo.oauthlink.oauth.LinuxDoOAuthClient;
import org.linuxdo.oauthlink.oauth.OAuthError;
import org.linuxdo.oauthlink.oauth.OAuthException;
import org.linuxdo.oauthlink.oauth.PendingOAuthRegistry;
import org.linuxdo.oauthlink.storage.LinkRepository;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OAuthLinkService — the business orchestrator.
 *
 * <p>Covers all five public API methods plus the two binding paths
 * (auto-bind and manual link code). Uses Mockito mocks for all
 * dependencies and MockedStatic for Bukkit static methods.
 */
@ExtendWith(MockitoExtension.class)
class OAuthLinkServiceTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PLAYER_NAME = "TestPlayer";
    private static final String LINUXDO_ID = "user-42";
    private static final String LINUXDO_USERNAME = "linuxuser";
    private static final String LINUXDO_DISPLAY_NAME = "Linux User";
    private static final Instant BASE_TIME = Instant.parse("2026-06-01T12:00:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Duration SKEW = Duration.ofSeconds(60);

    @Mock private JavaPlugin plugin;
    @Mock private OAuthConfig config;
    @Mock private LinkRepository repository;
    @Mock private PendingOAuthRegistry registry;
    @Mock private LinuxDoOAuthClient oauthClient;
    @Mock private CallbackHttpServer callbackServer;
    @Mock private BukkitScheduler scheduler;
    @Mock private PluginManager pluginManager;
    @Mock private Server server;

    private Clock clock;
    private Logger logger;
    private OAuthLinkService service;

    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(BASE_TIME, UTC);
        logger = Logger.getLogger("OAuthLinkServiceTest");

        // Default config stubs
        lenient().when(config.getTokenExpirySkewSeconds()).thenReturn(60);

        // Use synchronous executor so supplyAsync runs on test thread (MockedStatic is thread-local)
        service = new OAuthLinkService(plugin, config, repository, registry,
                oauthClient, callbackServer, clock, logger, Runnable::run);

        // Static Bukkit mocks — scheduler runs tasks synchronously
        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);

        // runTask runs the Runnable synchronously for deterministic tests
        lenient().doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(scheduler).runTask(any(), any(Runnable.class));
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // isLinked
    // ═══════════════════════════════════════════════════════════

    @Test
    void isLinked_PlayerNotInRepository_ShouldReturnFalse() {
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.empty());

        assertFalse(service.isLinked(PLAYER_UUID));
    }

    @Test
    void isLinked_PlayerHasNonExpiredToken_ShouldReturnTrue() {
        Instant futureExpiry = BASE_TIME.plusSeconds(7200);
        LinkedAccount account = createAccount(PLAYER_UUID, PLAYER_NAME,
                LINUXDO_ID, LINUXDO_USERNAME, BASE_TIME, futureExpiry);
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.of(account));

        assertTrue(service.isLinked(PLAYER_UUID));
    }

    @Test
    void isLinked_PlayerTokenExpired_ShouldReturnFalse() {
        Instant pastExpiry = BASE_TIME.minusSeconds(3600);
        LinkedAccount account = createAccount(PLAYER_UUID, PLAYER_NAME,
                LINUXDO_ID, LINUXDO_USERNAME, BASE_TIME, pastExpiry);
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.of(account));

        assertFalse(service.isLinked(PLAYER_UUID));
    }

    @Test
    void isLinked_TokenJustExpiredWithinSkew_ShouldReturnTrue() {
        // Token expired exactly at BASE_TIME, skew is 60s — still considered valid
        Instant justExpired = BASE_TIME;
        LinkedAccount account = createAccount(PLAYER_UUID, PLAYER_NAME,
                LINUXDO_ID, LINUXDO_USERNAME, BASE_TIME, justExpired);
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.of(account));

        assertTrue(service.isLinked(PLAYER_UUID),
                "Token expired at now should still be valid within skew tolerance");
    }

    @Test
    void isLinked_TokenExpiredPastSkew_ShouldReturnFalse() {
        // Token expired 61 seconds ago, skew is 60s — expired
        Instant pastSkew = BASE_TIME.minusSeconds(61);
        LinkedAccount account = createAccount(PLAYER_UUID, PLAYER_NAME,
                LINUXDO_ID, LINUXDO_USERNAME, BASE_TIME, pastSkew);
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.of(account));

        assertFalse(service.isLinked(PLAYER_UUID));
    }

    // ═══════════════════════════════════════════════════════════
    // getLinkedAccount
    // ═══════════════════════════════════════════════════════════

    @Test
    void getLinkedAccount_PlayerNotLinked_ShouldReturnEmpty() {
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.empty());

        assertTrue(service.getLinkedAccount(PLAYER_UUID).isEmpty());
    }

    @Test
    void getLinkedAccount_ActiveAccount_ShouldReturnAccount() {
        Instant futureExpiry = BASE_TIME.plusSeconds(7200);
        LinkedAccount account = createAccount(PLAYER_UUID, PLAYER_NAME,
                LINUXDO_ID, LINUXDO_USERNAME, BASE_TIME, futureExpiry);
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.of(account));

        Optional<LinkedAccount> result = service.getLinkedAccount(PLAYER_UUID);

        assertTrue(result.isPresent());
        assertEquals(LINUXDO_USERNAME, result.get().linuxDoUsername());
    }

    @Test
    void getLinkedAccount_ExpiredToken_ShouldReturnEmpty() {
        Instant pastExpiry = BASE_TIME.minusSeconds(61); // past skew
        LinkedAccount account = createAccount(PLAYER_UUID, PLAYER_NAME,
                LINUXDO_ID, LINUXDO_USERNAME, BASE_TIME, pastExpiry);
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.of(account));

        assertTrue(service.getLinkedAccount(PLAYER_UUID).isEmpty());
    }

    // ═══════════════════════════════════════════════════════════
    // unlink
    // ═══════════════════════════════════════════════════════════

    @Test
    void unlink_ExistingPlayer_ShouldDeleteAndFireUnlinkEvent() throws Exception {
        Instant futureExpiry = BASE_TIME.plusSeconds(7200);
        LinkedAccount account = createAccount(PLAYER_UUID, PLAYER_NAME,
                LINUXDO_ID, LINUXDO_USERNAME, BASE_TIME, futureExpiry);
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.of(account));
        when(repository.delete(PLAYER_UUID)).thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<Void> future = service.unlink(PLAYER_UUID);
        future.get(5, TimeUnit.SECONDS);

        verify(repository).delete(PLAYER_UUID);
        verify(pluginManager).callEvent(any(PlayerOAuthUnlinkEvent.class));
    }

    @Test
    void unlink_UnknownPlayer_ShouldNotFireEvent() throws Exception {
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.empty());
        when(repository.delete(PLAYER_UUID)).thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<Void> future = service.unlink(PLAYER_UUID);
        future.get(5, TimeUnit.SECONDS);

        verify(repository).delete(PLAYER_UUID);
        verify(pluginManager, never()).callEvent(any(PlayerOAuthUnlinkEvent.class));
    }

    // ═══════════════════════════════════════════════════════════
    // createAuthorizationUri
    // ═══════════════════════════════════════════════════════════

    @Test
    void createAuthorizationUri_ShouldCreateStateAndBuildUri() {
        String state = "random-state-abc123";
        URI expectedUri = URI.create("https://connect.linux.do/oauth2/authorize?client_id=test&state=" + state);
        when(registry.createState(PLAYER_UUID, PLAYER_NAME)).thenReturn(state);
        when(oauthClient.buildAuthorizationUri(state)).thenReturn(expectedUri);

        URI result = service.createAuthorizationUri(PLAYER_UUID, PLAYER_NAME);

        assertEquals(expectedUri, result);
        verify(registry).createState(PLAYER_UUID, PLAYER_NAME);
        verify(oauthClient).buildAuthorizationUri(state);
    }

    // ═══════════════════════════════════════════════════════════
    // onAutoBind — success path
    // ═══════════════════════════════════════════════════════════

    @Test
    void onAutoBind_NewPlayer_ShouldSaveAndFireSuccessEvent() throws Exception {
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens(BASE_TIME.plusSeconds(7200));
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.empty());
        when(repository.findByLinuxDoId(LINUXDO_ID)).thenReturn(Optional.empty());
        when(repository.save(any(LinkedAccount.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.onAutoBind(PLAYER_UUID, PLAYER_NAME, profile, tokens);

        verify(repository).save(any(LinkedAccount.class));
        verify(pluginManager).callEvent(any(PlayerOAuthSuccessEvent.class));
        verify(pluginManager, never()).callEvent(any(PlayerOAuthFailEvent.class));
    }

    // ═══════════════════════════════════════════════════════════
    // onAutoBind — already linked: same player
    // ═══════════════════════════════════════════════════════════

    @Test
    void onAutoBind_SamePlayerAlreadyLinked_ShouldFireFailEvent() {
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens(BASE_TIME.plusSeconds(7200));

        // Player already linked to a DIFFERENT LinuxDO account (ld-999)
        LinkedAccount existing = createAccount(PLAYER_UUID, PLAYER_NAME,
                "ld-999", "otheruser", BASE_TIME, BASE_TIME.plusSeconds(7200));
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.of(existing));

        service.onAutoBind(PLAYER_UUID, PLAYER_NAME, profile, tokens);

        verify(repository, never()).save(any());
        verify(pluginManager).callEvent(any(PlayerOAuthFailEvent.class));
    }

    @Test
    void onAutoBind_SamePlayerSameLinuxDoAccount_ShouldReSave() {
        // Player already linked to the SAME LinuxDO account — should update (re-bind)
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens(BASE_TIME.plusSeconds(7200));

        LinkedAccount existing = createAccount(PLAYER_UUID, PLAYER_NAME,
                LINUXDO_ID, LINUXDO_USERNAME, BASE_TIME, BASE_TIME.minusSeconds(3600));
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.of(existing));
        when(repository.save(any(LinkedAccount.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.onAutoBind(PLAYER_UUID, PLAYER_NAME, profile, tokens);

        verify(repository).save(any(LinkedAccount.class));
        verify(pluginManager).callEvent(any(PlayerOAuthSuccessEvent.class));
    }

    @Test
    void onAutoBind_ExpiredOldToken_ShouldAllowNewLink() {
        // Player has an old expired link — should be allowed to re-bind to new LinuxDO
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens(BASE_TIME.plusSeconds(7200));

        LinkedAccount existing = createAccount(PLAYER_UUID, PLAYER_NAME,
                "ld-other", "otheruser", BASE_TIME, BASE_TIME.minusSeconds(7200));
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.of(existing));
        when(repository.save(any(LinkedAccount.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.onAutoBind(PLAYER_UUID, PLAYER_NAME, profile, tokens);

        verify(repository).save(any(LinkedAccount.class));
        verify(pluginManager).callEvent(any(PlayerOAuthSuccessEvent.class));
    }

    // ═══════════════════════════════════════════════════════════
    // onAutoBind — LinuxDO account already bound to other player
    // ═══════════════════════════════════════════════════════════

    @Test
    void onAutoBind_LinuxDoAccountBoundToOtherPlayer_ShouldFireFailEvent() {
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens(BASE_TIME.plusSeconds(7200));

        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.empty());
        // LinuxDO account already bound to a different Minecraft player
        UUID otherPlayer = UUID.fromString("99999999-9999-9999-9999-999999999999");
        LinkedAccount otherAccount = createAccount(otherPlayer, "OtherPlayer",
                LINUXDO_ID, LINUXDO_USERNAME, BASE_TIME, BASE_TIME.plusSeconds(7200));
        when(repository.findByLinuxDoId(LINUXDO_ID)).thenReturn(Optional.of(otherAccount));

        service.onAutoBind(PLAYER_UUID, PLAYER_NAME, profile, tokens);

        verify(repository, never()).save(any());
        verify(pluginManager).callEvent(any(PlayerOAuthFailEvent.class));
    }

    // ═══════════════════════════════════════════════════════════
    // linkPlayer — success
    // ═══════════════════════════════════════════════════════════

    @Test
    void linkPlayer_ValidCode_ShouldSucceedAndFireSuccessEvent() throws Exception {
        String linkCode = "ABCD1234";
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens(BASE_TIME.plusSeconds(7200));
        PendingAuthorization pending = new PendingAuthorization(
                linkCode, profile, tokens, BASE_TIME.plusSeconds(300));

        when(registry.consumeCode(linkCode)).thenReturn(pending);
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.empty());
        when(repository.findByLinuxDoId(LINUXDO_ID)).thenReturn(Optional.empty());
        when(repository.save(any(LinkedAccount.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<LinkedAccount> future = service.linkPlayer(
                PLAYER_UUID, PLAYER_NAME, linkCode);
        LinkedAccount result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals(PLAYER_UUID, result.playerId());
        assertEquals(LINUXDO_USERNAME, result.linuxDoUsername());
        verify(repository).save(any(LinkedAccount.class));
        verify(pluginManager).callEvent(any(PlayerOAuthSuccessEvent.class));
    }

    // ═══════════════════════════════════════════════════════════
    // linkPlayer — invalid code
    // ═══════════════════════════════════════════════════════════

    @Test
    void linkPlayer_InvalidCode_ShouldThrowOAuthException() throws Exception {
        when(registry.consumeCode("INVALID1")).thenReturn(null);

        CompletableFuture<LinkedAccount> future = service.linkPlayer(
                PLAYER_UUID, PLAYER_NAME, "INVALID1");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(OAuthException.class, ex.getCause());
        OAuthException oa = (OAuthException) ex.getCause();
        assertEquals(OAuthError.INVALID_LINK_CODE, oa.getError());
        verify(pluginManager).callEvent(any(PlayerOAuthFailEvent.class));
    }

    @Test
    void linkPlayer_ExpiredCode_ShouldThrowOAuthException() throws Exception {
        String linkCode = "EXPIRED1";
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens(BASE_TIME.plusSeconds(7200));
        // Code expired 1 second ago
        PendingAuthorization pending = new PendingAuthorization(
                linkCode, profile, tokens, BASE_TIME.minusSeconds(1));

        when(registry.consumeCode(linkCode)).thenReturn(pending);

        CompletableFuture<LinkedAccount> future = service.linkPlayer(
                PLAYER_UUID, PLAYER_NAME, linkCode);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(OAuthException.class, ex.getCause());
        OAuthException oa = (OAuthException) ex.getCause();
        assertEquals(OAuthError.EXPIRED_LINK_CODE, oa.getError());
        verify(repository, never()).save(any());
    }

    // ═══════════════════════════════════════════════════════════
    // linkPlayer — already linked
    // ═══════════════════════════════════════════════════════════

    @Test
    void linkPlayer_AlreadyLinkedToDifferentAccount_ShouldThrowOAuthException() throws Exception {
        String linkCode = "ABCD5678";
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens(BASE_TIME.plusSeconds(7200));
        PendingAuthorization pending = new PendingAuthorization(
                linkCode, profile, tokens, BASE_TIME.plusSeconds(300));

        // Player already linked to a different LinuxDO account
        LinkedAccount existing = createAccount(PLAYER_UUID, PLAYER_NAME,
                "ld-other", "otheruser", BASE_TIME, BASE_TIME.plusSeconds(7200));

        when(registry.consumeCode(linkCode)).thenReturn(pending);
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.of(existing));

        CompletableFuture<LinkedAccount> future = service.linkPlayer(
                PLAYER_UUID, PLAYER_NAME, linkCode);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(OAuthException.class, ex.getCause());
        assertEquals(OAuthError.ALREADY_LINKED, ((OAuthException) ex.getCause()).getError());
        verify(repository, never()).save(any());
    }

    @Test
    void linkPlayer_AlreadyLinkedToSameAccount_ShouldReSave() throws Exception {
        String linkCode = "ABCD9012";
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens(BASE_TIME.plusSeconds(7200));
        PendingAuthorization pending = new PendingAuthorization(
                linkCode, profile, tokens, BASE_TIME.plusSeconds(300));

        // Player already linked to the SAME LinuxDO account — re-bind
        LinkedAccount existing = createAccount(PLAYER_UUID, PLAYER_NAME,
                LINUXDO_ID, LINUXDO_USERNAME, BASE_TIME, BASE_TIME.minusSeconds(3600));

        when(registry.consumeCode(linkCode)).thenReturn(pending);
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.of(existing));
        when(repository.save(any(LinkedAccount.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<LinkedAccount> future = service.linkPlayer(
                PLAYER_UUID, PLAYER_NAME, linkCode);
        LinkedAccount result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        verify(repository).save(any(LinkedAccount.class));
    }

    @Test
    void linkPlayer_OldTokenExpired_ShouldAllowNewLink() throws Exception {
        String linkCode = "ABCD3456";
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens(BASE_TIME.plusSeconds(7200));
        PendingAuthorization pending = new PendingAuthorization(
                linkCode, profile, tokens, BASE_TIME.plusSeconds(300));

        // Player has an expired old link — should be allowed
        LinkedAccount existing = createAccount(PLAYER_UUID, PLAYER_NAME,
                "ld-old", "olduser", BASE_TIME, BASE_TIME.minusSeconds(7200));

        when(registry.consumeCode(linkCode)).thenReturn(pending);
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.of(existing));
        when(repository.save(any(LinkedAccount.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<LinkedAccount> future = service.linkPlayer(
                PLAYER_UUID, PLAYER_NAME, linkCode);
        LinkedAccount result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        verify(repository).save(any(LinkedAccount.class));
    }

    @Test
    void linkPlayer_LinuxDoAccountBoundToOtherPlayer_ShouldThrowOAuthException() throws Exception {
        String linkCode = "ABCD7890";
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens(BASE_TIME.plusSeconds(7200));
        PendingAuthorization pending = new PendingAuthorization(
                linkCode, profile, tokens, BASE_TIME.plusSeconds(300));

        // LinuxDO account already bound to a different player
        UUID otherPlayer = UUID.fromString("99999999-9999-9999-9999-999999999999");
        LinkedAccount otherAccount = createAccount(otherPlayer, "OtherPlayer",
                LINUXDO_ID, LINUXDO_USERNAME, BASE_TIME, BASE_TIME.plusSeconds(7200));

        when(registry.consumeCode(linkCode)).thenReturn(pending);
        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.empty());
        when(repository.findByLinuxDoId(LINUXDO_ID)).thenReturn(Optional.of(otherAccount));

        CompletableFuture<LinkedAccount> future = service.linkPlayer(
                PLAYER_UUID, PLAYER_NAME, linkCode);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(OAuthException.class, ex.getCause());
        assertEquals(OAuthError.ALREADY_LINKED, ((OAuthException) ex.getCause()).getError());
        verify(repository, never()).save(any());
    }

    // ═══════════════════════════════════════════════════════════
    // onAutoBind — notifies online player
    // ═══════════════════════════════════════════════════════════

    @Test
    void onAutoBind_PlayerOnline_ShouldSendSuccessMessage() {
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens(BASE_TIME.plusSeconds(7200));
        Player mockPlayer = mock(Player.class);
        when(mockPlayer.isOnline()).thenReturn(true);

        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.empty());
        when(repository.findByLinuxDoId(LINUXDO_ID)).thenReturn(Optional.empty());
        when(repository.save(any(LinkedAccount.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        bukkitMock.when(() -> Bukkit.getPlayer(PLAYER_UUID)).thenReturn(mockPlayer);

        service.onAutoBind(PLAYER_UUID, PLAYER_NAME, profile, tokens);

        verify(mockPlayer).sendMessage(anyString());
    }

    @Test
    void onAutoBind_PlayerOffline_ShouldNotSendMessage() {
        LinuxDoProfile profile = createProfile();
        OAuthTokens tokens = createTokens(BASE_TIME.plusSeconds(7200));

        when(repository.findByPlayer(PLAYER_UUID)).thenReturn(Optional.empty());
        when(repository.findByLinuxDoId(LINUXDO_ID)).thenReturn(Optional.empty());
        when(repository.save(any(LinkedAccount.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        bukkitMock.when(() -> Bukkit.getPlayer(PLAYER_UUID)).thenReturn(null);

        // Should not throw
        assertDoesNotThrow(() ->
                service.onAutoBind(PLAYER_UUID, PLAYER_NAME, profile, tokens));
    }

    // ═══════════════════════════════════════════════════════════
    // helpers
    // ═══════════════════════════════════════════════════════════

    private static LinkedAccount createAccount(UUID playerId, String playerName,
                                                String linuxDoId, String linuxDoUsername,
                                                Instant linkedAt, Instant tokenExpiresAt) {
        return new LinkedAccount(playerId, playerName, linuxDoId, linuxDoUsername,
                linkedAt, tokenExpiresAt);
    }

    private static LinuxDoProfile createProfile() {
        return new LinuxDoProfile(LINUXDO_ID, LINUXDO_USERNAME, LINUXDO_DISPLAY_NAME);
    }

    private static OAuthTokens createTokens(Instant expiresAt) {
        return new OAuthTokens("access-token-secret", expiresAt);
    }
}
