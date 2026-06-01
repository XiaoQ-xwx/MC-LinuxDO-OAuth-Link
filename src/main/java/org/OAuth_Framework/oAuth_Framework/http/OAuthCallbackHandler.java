package org.OAuth_Framework.oAuth_Framework.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.OAuth_Framework.oAuth_Framework.oauth.LinuxDoOAuthClient;
import org.OAuth_Framework.oAuth_Framework.oauth.PendingOAuthRegistry;
import org.OAuth_Framework.oAuth_Framework.model.LinuxDoProfile;
import org.OAuth_Framework.oAuth_Framework.model.OAuthTokens;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles the OAuth callback from LinuxDO.
 * Validates the state parameter, exchanges the authorization code,
 * stores the result, and returns a link code to the browser.
 */
public class OAuthCallbackHandler implements HttpHandler {

    private final PendingOAuthRegistry registry;
    private final LinuxDoOAuthClient oauthClient;
    private final Logger logger;

    public OAuthCallbackHandler(PendingOAuthRegistry registry,
                                 LinuxDoOAuthClient oauthClient,
                                 Logger logger) {
        this.registry = registry;
        this.oauthClient = oauthClient;
        this.logger = logger;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Only handle GET
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI());

        String code = params.get("code");
        String state = params.get("state");

        if (code == null || state == null) {
            sendHtml(exchange, 400, buildErrorPage("授权参数缺失",
                    "缺少 code 或 state 参数，请重新尝试登录。"));
            return;
        }

        // Validate state
        if (!registry.validateAndConsumeState(state)) {
            logger.log(Level.WARNING, "无效或过期的 OAuth state");
            sendHtml(exchange, 400, buildErrorPage("会话已过期",
                    "授权会话已过期或无效。<br>请返回游戏重新输入 /link 获取新的授权链接。"));
            return;
        }

        // Exchange code for tokens and fetch profile
        oauthClient.exchangeCode(code)
                .thenCompose(tokens -> oauthClient.fetchProfile(tokens.accessToken())
                        .thenApply(profile -> new TokenAndProfile(tokens, profile)))
                .thenAccept(result -> {
                    try {
                        String linkCode = registry.storeAuthorization(result.profile, result.tokens);
                        sendHtml(exchange, 200, buildSuccessPage(linkCode));
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "存储授权结果失败", e);
                        sendHtml(exchange, 502, buildErrorPage("服务器错误",
                                "保存授权信息失败，请稍后重试。"));
                    }
                })
                .exceptionally(throwable -> {
                    logger.log(Level.WARNING, "OAuth 回调处理失败", throwable);
                    sendHtml(exchange, 502, buildErrorPage("授权失败",
                            "获取用户信息时发生错误。<br>请返回游戏重试。"));
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

    private String buildSuccessPage(String linkCode) {
        return "<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>授权成功 - LinuxDO OAuth</title>"
                + "<style>body{font-family:-apple-system,system-ui,sans-serif;display:flex;"
                + "justify-content:center;align-items:center;min-height:100vh;margin:0;"
                + "background:#f5f5f5;color:#333}.card{background:#fff;border-radius:12px;"
                + "padding:40px;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,.1);max-width:400px}"
                + ".code{font-size:32px;font-weight:bold;letter-spacing:4px;color:#00bcd4;"
                + "margin:20px 0;user-select:all}.hint{color:#666;font-size:14px}</style></head>"
                + "<body><div class=\"card\"><h1>✅ 授权成功</h1>"
                + "<p>请在游戏中输入以下指令完成绑定：</p>"
                + "<div class=\"code\">" + escapeHtml(linkCode) + "</div>"
                + "<p class=\"hint\"><code>/link " + escapeHtml(linkCode) + "</code></p>"
                + "<p class=\"hint\">此验证码 " + escapeHtml(linkCode) + " 在一定时间内有效</p>"
                + "</div></body></html>";
    }

    private String buildErrorPage(String title, String message) {
        return "<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escapeHtml(title) + "</title>"
                + "<style>body{font-family:-apple-system,system-ui,sans-serif;display:flex;"
                + "justify-content:center;align-items:center;min-height:100vh;margin:0;"
                + "background:#f5f5f5;color:#333}.card{background:#fff;border-radius:12px;"
                + "padding:40px;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,.1);max-width:400px}"
                + ".error{color:#e53935}</style></head>"
                + "<body><div class=\"card\"><h1 class=\"error\">❌ " + escapeHtml(title) + "</h1>"
                + "<p>" + message + "</p></div></body></html>";
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    // Internal pair for async result passing
    private record TokenAndProfile(OAuthTokens tokens, LinuxDoProfile profile) {}
}
