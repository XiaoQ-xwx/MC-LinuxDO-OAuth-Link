package org.OAuth_Framework.oAuth_Framework.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.OAuth_Framework.oAuth_Framework.oauth.OAuthError;
import org.OAuth_Framework.oAuth_Framework.oauth.OAuthException;

/**
 * Immutable validated runtime configuration loaded from config.yml.
 */
public final class OAuthConfig {

    private final String clientId;
    private final String clientSecret;
    private final String authorizationUrl;
    private final String tokenUrl;
    private final String userInfoUrl;
    private final String redirectUri;
    private final String callbackHost;
    private final int callbackPort;
    private final String callbackPath;
    private final int stateTtlSeconds;
    private final int linkCodeTtlSeconds;
    private final int tokenExpirySkewSeconds;
    private final String storageFile;

    private OAuthConfig(FileConfiguration config) {
        this.clientId = config.getString("oauth.client-id", "");
        this.clientSecret = config.getString("oauth.client-secret", "");
        this.authorizationUrl = config.getString("oauth.authorization-url", "https://connect.linux.do/oauth2/authorize");
        this.tokenUrl = config.getString("oauth.token-url", "https://connect.linux.do/oauth2/token");
        this.userInfoUrl = config.getString("oauth.user-info-url", "https://connect.linux.do/api/user");
        this.redirectUri = config.getString("oauth.redirect-uri", "http://127.0.0.1:8181/oauth/callback");
        this.callbackHost = config.getString("callback.host", "127.0.0.1");
        this.callbackPort = config.getInt("callback.port", 8181);
        this.callbackPath = config.getString("callback.path", "/oauth/callback");
        this.stateTtlSeconds = config.getInt("security.state-ttl-seconds", 300);
        this.linkCodeTtlSeconds = config.getInt("security.link-code-ttl-seconds", 300);
        this.tokenExpirySkewSeconds = config.getInt("security.token-expiry-skew-seconds", 60);
        this.storageFile = config.getString("storage.file", "data.yml");
    }

    /**
     * Loads and validates configuration from the plugin's config.yml.
     */
    public static OAuthConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        OAuthConfig config = new OAuthConfig(plugin.getConfig());
        config.validate();
        return config;
    }

    /**
     * Validates required configuration fields.
     * Throws OAuthException if critical fields are missing or invalid.
     */
    public void validate() {
        if (clientId == null || clientId.isBlank() || "your_id_here".equals(clientId)) {
            throw new OAuthException(OAuthError.CONFIG_INVALID, "oauth.client-id 未配置");
        }
        if (clientSecret == null || clientSecret.isBlank() || "your_secret_here".equals(clientSecret)) {
            throw new OAuthException(OAuthError.CONFIG_INVALID, "oauth.client-secret 未配置");
        }
        if (callbackPort < 1 || callbackPort > 65535) {
            throw new OAuthException(OAuthError.CONFIG_INVALID, "callback.port 必须在 1-65535 之间");
        }
        if (stateTtlSeconds < 30) {
            throw new OAuthException(OAuthError.CONFIG_INVALID, "security.state-ttl-seconds 不能小于 30");
        }
        if (linkCodeTtlSeconds < 30) {
            throw new OAuthException(OAuthError.CONFIG_INVALID, "security.link-code-ttl-seconds 不能小于 30");
        }
    }

    // Getters

    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getAuthorizationUrl() { return authorizationUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public String getUserInfoUrl() { return userInfoUrl; }
    public String getRedirectUri() { return redirectUri; }
    public String getCallbackHost() { return callbackHost; }
    public int getCallbackPort() { return callbackPort; }
    public String getCallbackPath() { return callbackPath; }
    public int getStateTtlSeconds() { return stateTtlSeconds; }
    public int getLinkCodeTtlSeconds() { return linkCodeTtlSeconds; }
    public int getTokenExpirySkewSeconds() { return tokenExpirySkewSeconds; }
    public String getStorageFile() { return storageFile; }
}
