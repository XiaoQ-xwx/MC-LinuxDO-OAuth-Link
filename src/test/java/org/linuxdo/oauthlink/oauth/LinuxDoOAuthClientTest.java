package org.linuxdo.oauthlink.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linuxdo.oauthlink.config.OAuthConfig;
import org.linuxdo.oauthlink.model.LinuxDoProfile;
import org.linuxdo.oauthlink.model.OAuthTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Integration-light tests for LinuxDoOAuthClient.
 *
 * <p>Uses a real JDK HttpServer (no WireMock needed) to test
 * the HTTP code path end-to-end. Covers happy path, error
 * responses, and malformed JSON.
 */
@ExtendWith(MockitoExtension.class)
class LinuxDoOAuthClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger("LinuxDoOAuthClientTest");

    private HttpServer server;
    private int port;

    @Mock
    private OAuthConfig config;

    private HttpClient httpClient;
    private LinuxDoOAuthClient client;

    @BeforeEach
    void setUp() throws IOException {
        // Start a real HTTP server on an ephemeral port
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();

        // Real HttpClient (no mocking — real network stack on loopback)
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        client = new LinuxDoOAuthClient(httpClient, MAPPER, config, LOGGER);

        // Default config pointing at our local server
        lenient().when(config.getClientId()).thenReturn("test-client-id");
        lenient().when(config.getClientSecret()).thenReturn("test-client-secret");
        lenient().when(config.getRedirectUri()).thenReturn("http://127.0.0.1:2790/oauth/callback");
        lenient().when(config.getTokenUrl()).thenReturn("http://127.0.0.1:" + port + "/oauth/token");
        lenient().when(config.getUserInfoUrl()).thenReturn("http://127.0.0.1:" + port + "/api/user");
        lenient().when(config.getAuthorizationUrl()).thenReturn("https://connect.linux.do/oauth2/authorize");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // buildAuthorizationUri
    // ═══════════════════════════════════════════════════════════

    @Test
    void buildAuthorizationUri_ShouldContainAllRequiredParams() {
        URI uri = client.buildAuthorizationUri("test-state-abc");

        assertNotNull(uri);
        String uriStr = uri.toString();
        assertTrue(uriStr.contains("response_type=code"));
        assertTrue(uriStr.contains("client_id=test-client-id"));
        assertTrue(uriStr.contains("state=test-state-abc"));
        assertTrue(uriStr.contains("redirect_uri="));
    }

    // ═══════════════════════════════════════════════════════════
    // exchangeCode — happy path
    // ═══════════════════════════════════════════════════════════

    @Test
    void exchangeCode_ValidResponse_ShouldReturnTokens() throws Exception {
        // Serve a valid token response
        server.createContext("/oauth/token", exchange -> {
            String json = "{\"access_token\":\"tok_deadbeef\",\"expires_in\":3600}";
            sendJson(exchange, 200, json);
        });

        CompletableFuture<OAuthTokens> future = client.exchangeCode("auth-code-123");
        OAuthTokens tokens = future.get(5, TimeUnit.SECONDS);

        assertNotNull(tokens);
        assertEquals("tok_deadbeef", tokens.accessToken());
        assertNotNull(tokens.expiresAt());
    }

    // ═══════════════════════════════════════════════════════════
    // exchangeCode — error paths
    // ═══════════════════════════════════════════════════════════

    @Test
    void exchangeCode_Http400_ShouldThrowOAuthException() throws Exception {
        server.createContext("/oauth/token", exchange ->
                sendJson(exchange, 400, "{\"error\":\"invalid_grant\"}"));

        CompletableFuture<OAuthTokens> future = client.exchangeCode("bad-code");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(OAuthException.class, ex.getCause());
        assertEquals(OAuthError.TOKEN_EXCHANGE_FAILED, ((OAuthException) ex.getCause()).getError());
    }

    @Test
    void exchangeCode_Http500_ShouldThrowOAuthException() throws Exception {
        server.createContext("/oauth/token", exchange ->
                sendJson(exchange, 500, "{\"error\":\"server_error\"}"));

        CompletableFuture<OAuthTokens> future = client.exchangeCode("any-code");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(OAuthException.class, ex.getCause());
        assertEquals(OAuthError.TOKEN_EXCHANGE_FAILED, ((OAuthException) ex.getCause()).getError());
    }

    @Test
    void exchangeCode_MalformedJson_ShouldThrowOAuthException() throws Exception {
        server.createContext("/oauth/token", exchange ->
                sendText(exchange, 200, "this is not json {{{"));

        CompletableFuture<OAuthTokens> future = client.exchangeCode("any-code");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(OAuthException.class, ex.getCause());
        assertEquals(OAuthError.TOKEN_EXCHANGE_FAILED, ((OAuthException) ex.getCause()).getError());
    }

    @Test
    void exchangeCode_MissingAccessTokenField_ShouldThrowOAuthException() throws Exception {
        server.createContext("/oauth/token", exchange ->
                sendJson(exchange, 200, "{\"expires_in\":3600}"));

        CompletableFuture<OAuthTokens> future = client.exchangeCode("any-code");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(OAuthException.class, ex.getCause());
        assertEquals(OAuthError.TOKEN_EXCHANGE_FAILED, ((OAuthException) ex.getCause()).getError());
    }

    // ═══════════════════════════════════════════════════════════
    // fetchProfile — happy path
    // ═══════════════════════════════════════════════════════════

    @Test
    void fetchProfile_ValidResponse_ShouldReturnProfile() throws Exception {
        String rawJson = "{"
                + "\"id\":42,"
                + "\"username\":\"linuxuser\","
                + "\"name\":\"Linux User\","
                + "\"trust_level\":3,"
                + "\"likes_received\":128"
                + "}";
        server.createContext("/api/user", exchange ->
                sendJson(exchange, 200, rawJson));

        CompletableFuture<LinuxDoProfile> future = client.fetchProfile("tok_valid");
        LinuxDoProfile profile = future.get(5, TimeUnit.SECONDS);

        assertNotNull(profile);
        assertEquals("42", profile.id());
        assertEquals("linuxuser", profile.username());
        assertEquals("Linux User", profile.displayName());
        assertEquals(3, profile.trustLevel());
        assertEquals(128, profile.likesReceived());
        assertEquals(rawJson, profile.rawJson());
    }

    @Test
    void fetchProfile_MinimalResponse_ShouldUseDefaults() throws Exception {
        // Only id and username provided — name defaults to username,
        // trust_level and likes_received default to -1
        server.createContext("/api/user", exchange ->
                sendJson(exchange, 200, "{\"id\":7,\"username\":\"minimal\"}"));

        CompletableFuture<LinuxDoProfile> future = client.fetchProfile("tok_min");
        LinuxDoProfile profile = future.get(5, TimeUnit.SECONDS);

        assertEquals("7", profile.id());
        assertEquals("minimal", profile.username());
        assertEquals("minimal", profile.displayName(), "displayName should default to username");
        assertEquals(-1, profile.trustLevel(), "missing trust_level should be -1");
        assertEquals(-1, profile.likesReceived(), "missing likes_received should be -1");
    }

    // ═══════════════════════════════════════════════════════════
    // fetchProfile — error paths
    // ═══════════════════════════════════════════════════════════

    @Test
    void fetchProfile_Http401_ShouldThrowOAuthException() throws Exception {
        server.createContext("/api/user", exchange ->
                sendJson(exchange, 401, "{\"error\":\"invalid_token\"}"));

        CompletableFuture<LinuxDoProfile> future = client.fetchProfile("expired_token");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(OAuthException.class, ex.getCause());
        assertEquals(OAuthError.USERINFO_FAILED, ((OAuthException) ex.getCause()).getError());
    }

    @Test
    void fetchProfile_MalformedJson_ShouldThrowOAuthException() throws Exception {
        server.createContext("/api/user", exchange ->
                sendText(exchange, 200, "<<<broken>>>"));

        CompletableFuture<LinuxDoProfile> future = client.fetchProfile("any_token");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(OAuthException.class, ex.getCause());
        assertEquals(OAuthError.USERINFO_FAILED, ((OAuthException) ex.getCause()).getError());
    }

    @Test
    void fetchProfile_EmptyResponse_ShouldThrowOAuthException() throws Exception {
        server.createContext("/api/user", exchange ->
                sendText(exchange, 200, ""));

        CompletableFuture<LinuxDoProfile> future = client.fetchProfile("any_token");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(OAuthException.class, ex.getCause());
        assertEquals(OAuthError.USERINFO_FAILED, ((OAuthException) ex.getCause()).getError());
    }

    @Test
    void fetchProfile_ServerReturns500_ShouldThrowOAuthException() throws Exception {
        server.createContext("/api/user", exchange ->
                sendText(exchange, 500, "Internal Server Error"));

        CompletableFuture<LinuxDoProfile> future = client.fetchProfile("any_token");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(OAuthException.class, ex.getCause());
        assertEquals(OAuthError.USERINFO_FAILED, ((OAuthException) ex.getCause()).getError());
    }

    // ═══════════════════════════════════════════════════════════
    // helpers
    // ═══════════════════════════════════════════════════════════

    private static void sendJson(HttpExchange exchange, int statusCode, String json)
            throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int statusCode, String text)
            throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
