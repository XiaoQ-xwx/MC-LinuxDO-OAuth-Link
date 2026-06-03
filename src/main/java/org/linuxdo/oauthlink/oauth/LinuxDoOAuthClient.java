package org.linuxdo.oauthlink.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linuxdo.oauthlink.config.OAuthConfig;
import org.linuxdo.oauthlink.model.LinuxDoProfile;
import org.linuxdo.oauthlink.model.OAuthTokens;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * All LinuxDO OAuth2 HTTP communication.
 * Uses java.net.http.HttpClient for async requests.
 * Never logs tokens or secrets.
 */
public class LinuxDoOAuthClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile OAuthConfig config;
    private final Logger logger;

    public LinuxDoOAuthClient(HttpClient httpClient, ObjectMapper objectMapper,
                               OAuthConfig config, Logger logger) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.config = config;
        this.logger = logger;
    }

    /**
     * Updates the configuration reference for runtime reload.
     * Thread-safe via volatile.
     */
    public void updateConfig(OAuthConfig newConfig) {
        this.config = newConfig;
    }

    /**
     * Builds the LinuxDO authorization URL for the OAuth2 Authorization Code flow.
     */
    public URI buildAuthorizationUri(String state) {
        String url = config.getAuthorizationUrl()
                + "?response_type=code"
                + "&client_id=" + urlEncode(config.getClientId())
                + "&redirect_uri=" + urlEncode(config.getRedirectUri())
                + "&state=" + urlEncode(state);
        return URI.create(url);
    }

    /**
     * Exchanges an authorization code for OAuth tokens.
     * Captures a local config snapshot to avoid inconsistent multi-field reads during reload.
     */
    public CompletableFuture<OAuthTokens> exchangeCode(String code) {
        OAuthConfig cfg = this.config; // atomic snapshot
        String body = "grant_type=authorization_code"
                + "&code=" + urlEncode(code)
                + "&redirect_uri=" + urlEncode(cfg.getRedirectUri())
                + "&client_id=" + urlEncode(cfg.getClientId())
                + "&client_secret=" + urlEncode(cfg.getClientSecret());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cfg.getTokenUrl()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("User-Agent", "OAuthLink/1.0 (Minecraft)")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.log(Level.WARNING, "Token 交换失败, HTTP {0}", response.statusCode());
                        throw new OAuthException(OAuthError.TOKEN_EXCHANGE_FAILED,
                                "Token 交换失败 (HTTP " + response.statusCode() + ")");
                    }
                    try {
                        JsonNode json = objectMapper.readTree(response.body());
                        String accessToken = json.get("access_token").asText();
                        long expiresIn = json.get("expires_in").asLong(3600);
                        Instant expiresAt = Instant.now().plusSeconds(expiresIn);
                        return new OAuthTokens(accessToken, expiresAt);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Token 响应解析失败", e);
                        throw new OAuthException(OAuthError.TOKEN_EXCHANGE_FAILED,
                                "Token 响应解析失败");
                    }
                });
    }

    /**
     * Fetches the user profile from LinuxDO using an access token.
     * Captures a local config snapshot to avoid inconsistent multi-field reads during reload.
     */
    public CompletableFuture<LinuxDoProfile> fetchProfile(String accessToken) {
        OAuthConfig cfg = this.config; // atomic snapshot
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cfg.getUserInfoUrl()))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .header("User-Agent", "OAuthLink/1.0 (Minecraft)")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.log(Level.WARNING, "获取用户信息失败, HTTP {0}", response.statusCode());
                        throw new OAuthException(OAuthError.USERINFO_FAILED,
                                "获取用户信息失败 (HTTP " + response.statusCode() + ")");
                    }
                    try {
                        String rawJson = response.body();
                        JsonNode json = objectMapper.readTree(rawJson);
                        String id = json.has("id") ? String.valueOf(json.get("id").asLong()) : "";
                        String username = json.has("username") ? json.get("username").asText() : "";
                        String displayName = json.has("name") ? json.get("name").asText(username) : username;
                        int trustLevel = json.has("trust_level") ? json.get("trust_level").asInt(-1) : -1;
                        int likesReceived = json.has("likes_received") ? json.get("likes_received").asInt(-1) : -1;
                        return new LinuxDoProfile(id, username, displayName, trustLevel, likesReceived, rawJson);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "用户信息解析失败", e);
                        throw new OAuthException(OAuthError.USERINFO_FAILED,
                                "用户信息解析失败");
                    }
                });
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
