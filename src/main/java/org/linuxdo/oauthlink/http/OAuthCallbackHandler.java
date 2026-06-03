package org.linuxdo.oauthlink.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.linuxdo.oauthlink.model.LinuxDoProfile;
import org.linuxdo.oauthlink.model.OAuthTokens;
import org.linuxdo.oauthlink.oauth.LinuxDoOAuthClient;
import org.linuxdo.oauthlink.oauth.PendingOAuthRegistry;
import org.linuxdo.oauthlink.oauth.PendingOAuthRegistry.StateEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles the OAuth callback from LinuxDO.
 * Auto mode: validates state → exchanges code → bind directly → show success page.
 * Manual fallback: stores link code for /linkLD <code>.
 */
public class OAuthCallbackHandler implements HttpHandler {

    private final PendingOAuthRegistry registry;
    private final LinuxDoOAuthClient oauthClient;
    private final OAuthBindingCallback bindingCallback;
    private final Logger logger;

    /** Result of an auto-bind attempt — determines which HTML page to show the user. */
    public enum BindResult {
        /** Binding succeeded — account saved, events fired. */
        SUCCESS,
        /** Deterministic conflict — no manual code generated, show conflict page. */
        CONFLICT,
        /** Transient failure — manual code provided for /linkld <code>. */
        TRANSIENT_FAILURE
    }

    @FunctionalInterface
    public interface OAuthBindingCallback {
        /** Attempts auto-bind. The result determines the callback page shown to the user. */
        BindResult bind(UUID playerId, String playerName, LinuxDoProfile profile, OAuthTokens tokens);
    }

    public OAuthCallbackHandler(PendingOAuthRegistry registry,
                                 LinuxDoOAuthClient oauthClient,
                                 OAuthBindingCallback bindingCallback,
                                 Logger logger) {
        this.registry = registry;
        this.oauthClient = oauthClient;
        this.bindingCallback = bindingCallback;
        this.logger = logger;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI());
        String code = params.get("code");
        String state = params.get("state");

        if (code == null || state == null) {
            sendHtml(exchange, 400, buildErrorPage("参数缺失",
                    "缺少 code 或 state 参数，请重新尝试。"));
            return;
        }

        // Consume state → get player identity
        StateEntry stateEntry = registry.consumeState(state);
        if (stateEntry == null) {
            logger.log(Level.WARNING, "无效或过期的 OAuth state");
            sendHtml(exchange, 400, buildErrorPage("会话已过期",
                    "授权会话已过期或无效。<br>请回到游戏重新输入 /linkLD 获取新的授权链接。"));
            return;
        }

        final UUID playerId = stateEntry.playerId();
        final String playerName = stateEntry.playerName();

        // Exchange code → fetch profile → bind directly
        oauthClient.exchangeCode(code)
                .thenCompose(tokens -> oauthClient.fetchProfile(tokens.accessToken())
                        .thenApply(profile -> new TokenAndProfile(tokens, profile)))
                .thenAccept(result -> {
                    try {
                        BindResult bindResult = bindingCallback.bind(
                                playerId, playerName, result.profile, result.tokens);
                        switch (bindResult) {
                            case SUCCESS:
                                sendHtml(exchange, 200,
                                        buildAutoSuccessPage(result.profile.username()));
                                break;
                            case CONFLICT:
                                // Deterministic conflict — don't create a useless manual code
                                sendHtml(exchange, 409, buildErrorPage("绑定冲突",
                                        "该 LinuxDO 账号或你的 Minecraft 账号已绑定其他用户。"
                                        + "<br>请检查后重试，或联系服务器管理员。"));
                                break;
                            case TRANSIENT_FAILURE:
                                // Store link code so the player can retry manually
                                String linkCode = registry.storeAuthorization(
                                        result.profile, result.tokens);
                                sendHtml(exchange, 200, buildManualFallbackPage(linkCode));
                                break;
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "自动绑定失败", e);
                        // Fallback: store link code for manual mode
                        String linkCode = registry.storeAuthorization(
                                result.profile, result.tokens);
                        sendHtml(exchange, 200, buildManualFallbackPage(linkCode));
                    }
                })
                .exceptionally(throwable -> {
                    logger.log(Level.WARNING, "OAuth 回调处理失败", throwable);
                    String detail = extractErrorDetail(throwable);
                    sendHtml(exchange, 502, buildErrorPage("授权失败",
                            "获取用户信息时发生错误。<br>请回到游戏重试。", detail));
                    return null;
                });
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, String> params = new HashMap<>();
        String query = uri.getQuery();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }

    private void sendResponse(HttpExchange exchange, int status, String contentType, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "发送 HTTP 响应失败", e);
        }
    }

    private void sendHtml(HttpExchange exchange, int status, String html) {
        sendResponse(exchange, status, "text/html", html);
    }

    private String buildAutoSuccessPage(String username) {
        return "<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>绑定成功 - LinuxDO OAuth</title>"
                + "<style>body{font-family:-apple-system,system-ui,sans-serif;display:flex;"
                + "justify-content:center;align-items:center;min-height:100vh;margin:0;"
                + "background:#f5f5f5;color:#333}.card{background:#fff;border-radius:12px;"
                + "padding:40px;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,.1);max-width:400px}"
                + "h1{color:#4caf50}.name{color:#00bcd4;font-weight:bold}</style></head>"
                + "<body><div class=\"card\"><h1>🎉 绑定成功！</h1>"
                + "<p>你的 LinuxDO 账号 <span class=\"name\">@" + escapeHtml(username) + "</span> 已绑定</p>"
                + "<p style=\"color:#666;font-size:14px\">可以关闭此页面，回到游戏继续游玩。</p>"
                + "</div></body></html>";
    }

    private String buildManualFallbackPage(String linkCode) {
        return "<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>验证码 - LinuxDO OAuth</title>"
                + "<style>body{font-family:-apple-system,system-ui,sans-serif;display:flex;"
                + "justify-content:center;align-items:center;min-height:100vh;margin:0;"
                + "background:#f5f5f5;color:#333}.card{background:#fff;border-radius:12px;"
                + "padding:40px;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,.1);max-width:400px}"
                + ".code{font-size:32px;font-weight:bold;letter-spacing:4px;color:#f57c00}"
                + "h1{color:#f57c00}</style></head>"
                + "<body><div class=\"card\"><h1>⚠️ 自动绑定失败</h1>"
                + "<p>请在游戏中输入以下指令手动完成绑定：</p>"
                + "<div class=\"code\">" + escapeHtml(linkCode) + "</div>"
                + "<p><code>/linkLD " + escapeHtml(linkCode) + "</code></p>"
                + "</div></body></html>";
    }

    private String buildErrorPage(String title, String message) {
        return buildErrorPage(title, message, null);
    }

    /**
     * Builds an error page with optional expandable technical details
     * for server administrators to diagnose issues.
     */
    private String buildErrorPage(String title, String message, String errorDetail) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(escapeHtml(title)).append("</title>")
                .append("<style>body{font-family:-apple-system,system-ui,sans-serif;display:flex;")
                .append("justify-content:center;align-items:center;min-height:100vh;margin:0;")
                .append("background:#f5f5f5;color:#333}.card{background:#fff;border-radius:12px;")
                .append("padding:40px;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,.1);")
                .append("max-width:500px}.error{color:#e53935}")
                .append("details{margin-top:16px;text-align:left}")
                .append("summary{cursor:pointer;color:#888;font-size:13px;user-select:none}")
                .append("summary:hover{color:#555}")
                .append("pre{background:#fafafa;border:1px solid #e8e8e8;border-radius:8px;")
                .append("padding:12px;margin-top:8px;font-size:12px;color:#e53935;")
                .append("white-space:pre-wrap;word-break:break-all;")
                .append("max-height:240px;overflow-y:auto;line-height:1.5}")
                .append("</style></head>")
                .append("<body><div class=\"card\"><h1 class=\"error\">❌ ")
                .append(escapeHtml(title)).append("</h1>")
                .append("<p>").append(message).append("</p>");

        if (errorDetail != null && !errorDetail.isBlank()) {
            html.append("<details><summary>🔍 错误详情（服务器管理员参考）</summary>")
                    .append("<pre>").append(escapeHtml(errorDetail)).append("</pre>")
                    .append("</details>");
        }

        html.append("</div></body></html>");
        return html.toString();
    }

    /**
     * Extracts a human-readable error detail from a Throwable for diagnostic display.
     * Truncated at 600 chars to keep the page lightweight.
     */
    private String extractErrorDetail(Throwable throwable) {
        if (throwable == null) return null;
        Throwable cause = throwable.getCause();
        String msg = (cause != null) ? cause.toString() : throwable.toString();
        if (msg.length() > 600) {
            msg = msg.substring(0, 600) + "...";
        }
        return msg;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private record TokenAndProfile(OAuthTokens tokens, LinuxDoProfile profile) {}
}
