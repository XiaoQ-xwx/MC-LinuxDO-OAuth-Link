package org.linuxdo.oauthlink.config;

import org.linuxdo.oauthlink.oauth.OAuthError;
import org.linuxdo.oauthlink.oauth.OAuthException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OAuthConfig — validation and defaults.
 *
 * <p>Tests the public {@code load()} entry point using Mockito mocks
 * for the Bukkit JavaPlugin and FileConfiguration.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuthConfigTest {

    @Mock
    private JavaPlugin plugin;

    @Mock
    private FileConfiguration fileConfig;

    // ── Happy path ──────────────────────────────────────────

    @Test
    void load_WithValidConfig_ShouldReturnConfig() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn("valid-client-id");
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn("valid-secret");
        when(fileConfig.getInt("callback.port", 2790)).thenReturn(2790);
        when(fileConfig.getInt("security.state-ttl-seconds", 300)).thenReturn(300);
        when(fileConfig.getInt("security.link-code-ttl-seconds", 300)).thenReturn(300);

        OAuthConfig config = OAuthConfig.load(plugin);

        assertNotNull(config);
        assertEquals("valid-client-id", config.getClientId());
        assertEquals("valid-secret", config.getClientSecret());
    }

    // ── client-id validation ─────────────────────────────────

    @Test
    void validate_NullClientId_ShouldThrowOAuthException() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn(null);
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn("valid-secret");

        OAuthException ex = assertThrows(OAuthException.class, () -> OAuthConfig.load(plugin));

        assertEquals(OAuthError.CONFIG_INVALID, ex.getError());
        assertTrue(ex.getSafeMessage().contains("client-id"));
    }

    @Test
    void validate_BlankClientId_ShouldThrowOAuthException() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn("   ");
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn("valid-secret");

        OAuthException ex = assertThrows(OAuthException.class, () -> OAuthConfig.load(plugin));

        assertEquals(OAuthError.CONFIG_INVALID, ex.getError());
    }

    @Test
    void validate_PlaceholderClientId_ShouldThrowOAuthException() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn("your_id_here");
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn("valid-secret");

        OAuthException ex = assertThrows(OAuthException.class, () -> OAuthConfig.load(plugin));

        assertEquals(OAuthError.CONFIG_INVALID, ex.getError());
    }

    // ── client-secret validation ─────────────────────────────

    @Test
    void validate_NullClientSecret_ShouldThrowOAuthException() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn("valid-client-id");
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn(null);

        OAuthException ex = assertThrows(OAuthException.class, () -> OAuthConfig.load(plugin));

        assertEquals(OAuthError.CONFIG_INVALID, ex.getError());
        assertTrue(ex.getSafeMessage().contains("client-secret"));
    }

    @Test
    void validate_BlankClientSecret_ShouldThrowOAuthException() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn("valid-client-id");
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn("");

        OAuthException ex = assertThrows(OAuthException.class, () -> OAuthConfig.load(plugin));

        assertEquals(OAuthError.CONFIG_INVALID, ex.getError());
    }

    @Test
    void validate_PlaceholderClientSecret_ShouldThrowOAuthException() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn("valid-client-id");
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn("your_secret_here");

        OAuthException ex = assertThrows(OAuthException.class, () -> OAuthConfig.load(plugin));

        assertEquals(OAuthError.CONFIG_INVALID, ex.getError());
    }

    // ── port validation ──────────────────────────────────────

    @Test
    void validate_PortZero_ShouldThrowOAuthException() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn("valid-client-id");
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn("valid-secret");
        when(fileConfig.getInt("callback.port", 2790)).thenReturn(0);

        OAuthException ex = assertThrows(OAuthException.class, () -> OAuthConfig.load(plugin));

        assertEquals(OAuthError.CONFIG_INVALID, ex.getError());
    }

    @Test
    void validate_PortTooHigh_ShouldThrowOAuthException() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn("valid-client-id");
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn("valid-secret");
        when(fileConfig.getInt("callback.port", 2790)).thenReturn(65536);

        OAuthException ex = assertThrows(OAuthException.class, () -> OAuthConfig.load(plugin));

        assertEquals(OAuthError.CONFIG_INVALID, ex.getError());
    }

    @Test
    void validate_NegativePort_ShouldThrowOAuthException() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn("valid-client-id");
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn("valid-secret");
        when(fileConfig.getInt("callback.port", 2790)).thenReturn(-1);

        OAuthException ex = assertThrows(OAuthException.class, () -> OAuthConfig.load(plugin));

        assertEquals(OAuthError.CONFIG_INVALID, ex.getError());
    }

    // ── TTL validation ───────────────────────────────────────

    @Test
    void validate_StateTtlTooLow_ShouldThrowOAuthException() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn("valid-client-id");
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn("valid-secret");
        when(fileConfig.getInt("callback.port", 2790)).thenReturn(2790);
        when(fileConfig.getInt("security.state-ttl-seconds", 300)).thenReturn(10);

        OAuthException ex = assertThrows(OAuthException.class, () -> OAuthConfig.load(plugin));

        assertEquals(OAuthError.CONFIG_INVALID, ex.getError());
    }

    @Test
    void validate_LinkCodeTtlTooLow_ShouldThrowOAuthException() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn("valid-client-id");
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn("valid-secret");
        when(fileConfig.getInt("callback.port", 2790)).thenReturn(2790);
        when(fileConfig.getInt("security.state-ttl-seconds", 300)).thenReturn(300);
        when(fileConfig.getInt("security.link-code-ttl-seconds", 300)).thenReturn(29);

        OAuthException ex = assertThrows(OAuthException.class, () -> OAuthConfig.load(plugin));

        assertEquals(OAuthError.CONFIG_INVALID, ex.getError());
    }

    // ── custom values ─────────────────────────────────────────

    @Test
    void load_CustomValues_ShouldOverrideDefaults() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn("my-client");
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn("my-secret");
        when(fileConfig.getString("oauth.authorization-url", "https://connect.linux.do/oauth2/authorize"))
                .thenReturn("https://custom.example.com/authorize");
        when(fileConfig.getString("oauth.token-url", "https://connect.linux.do/oauth2/token"))
                .thenReturn("https://custom.example.com/token");
        when(fileConfig.getString("oauth.user-info-url", "https://connect.linux.do/api/user"))
                .thenReturn("https://custom.example.com/user");
        when(fileConfig.getString("oauth.redirect-uri", "http://127.0.0.1:2790/oauth/callback"))
                .thenReturn("https://my.server.com/callback");
        when(fileConfig.getString("callback.host", "127.0.0.1")).thenReturn("0.0.0.0");
        when(fileConfig.getInt("callback.port", 2790)).thenReturn(9090);
        when(fileConfig.getString("callback.path", "/oauth/callback")).thenReturn("/custom/callback");
        when(fileConfig.getInt("security.state-ttl-seconds", 300)).thenReturn(600);
        when(fileConfig.getInt("security.link-code-ttl-seconds", 300)).thenReturn(600);
        when(fileConfig.getInt("security.token-expiry-skew-seconds", 60)).thenReturn(120);
        when(fileConfig.getString("storage.file", "data.yml")).thenReturn("custom-data.yml");

        OAuthConfig config = OAuthConfig.load(plugin);

        assertEquals("my-client", config.getClientId());
        assertEquals("my-secret", config.getClientSecret());
        assertEquals("https://custom.example.com/authorize", config.getAuthorizationUrl());
        assertEquals("https://custom.example.com/token", config.getTokenUrl());
        assertEquals("https://custom.example.com/user", config.getUserInfoUrl());
        assertEquals("https://my.server.com/callback", config.getRedirectUri());
        assertEquals("0.0.0.0", config.getCallbackHost());
        assertEquals(9090, config.getCallbackPort());
        assertEquals("/custom/callback", config.getCallbackPath());
        assertEquals(600, config.getStateTtlSeconds());
        assertEquals(600, config.getLinkCodeTtlSeconds());
        assertEquals(120, config.getTokenExpirySkewSeconds());
        assertEquals("custom-data.yml", config.getStorageFile());
    }

    @Test
    void load_DefaultValues_ShouldBeApplied() {
        when(plugin.getConfig()).thenReturn(fileConfig);
        when(fileConfig.getString("oauth.client-id", "")).thenReturn("valid-client-id");
        when(fileConfig.getString("oauth.client-secret", "")).thenReturn("valid-secret");
        when(fileConfig.getInt("callback.port", 2790)).thenReturn(2790);
        when(fileConfig.getInt("security.state-ttl-seconds", 300)).thenReturn(300);
        when(fileConfig.getInt("security.link-code-ttl-seconds", 300)).thenReturn(300);
        // These return null/0 from the mock, but the constructor's default
        // value is only used by real FileConfiguration, not mocks.
        // We stub them explicitly here so the config object is fully populated.
        when(fileConfig.getString("oauth.authorization-url", "https://connect.linux.do/oauth2/authorize"))
                .thenReturn("https://connect.linux.do/oauth2/authorize");
        when(fileConfig.getString("oauth.token-url", "https://connect.linux.do/oauth2/token"))
                .thenReturn("https://connect.linux.do/oauth2/token");
        when(fileConfig.getString("oauth.user-info-url", "https://connect.linux.do/api/user"))
                .thenReturn("https://connect.linux.do/api/user");
        when(fileConfig.getString("oauth.redirect-uri", "http://127.0.0.1:2790/oauth/callback"))
                .thenReturn("http://127.0.0.1:2790/oauth/callback");
        when(fileConfig.getString("callback.host", "127.0.0.1")).thenReturn("127.0.0.1");
        when(fileConfig.getString("callback.path", "/oauth/callback")).thenReturn("/oauth/callback");
        when(fileConfig.getInt("security.token-expiry-skew-seconds", 60)).thenReturn(60);
        when(fileConfig.getString("storage.file", "data.yml")).thenReturn("data.yml");

        OAuthConfig config = OAuthConfig.load(plugin);

        assertEquals("https://connect.linux.do/oauth2/authorize", config.getAuthorizationUrl());
        assertEquals("https://connect.linux.do/oauth2/token", config.getTokenUrl());
        assertEquals("https://connect.linux.do/api/user", config.getUserInfoUrl());
        assertEquals("http://127.0.0.1:2790/oauth/callback", config.getRedirectUri());
        assertEquals("127.0.0.1", config.getCallbackHost());
        assertEquals("/oauth/callback", config.getCallbackPath());
        assertEquals(300, config.getStateTtlSeconds());
        assertEquals(300, config.getLinkCodeTtlSeconds());
        assertEquals(60, config.getTokenExpirySkewSeconds());
        assertEquals("data.yml", config.getStorageFile());
    }
}
