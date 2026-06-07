package org.linuxdo.oauthlink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linuxdo.oauthlink.command.LinkCommand;
import org.linuxdo.oauthlink.command.OAuthLinkCommand;
import org.linuxdo.oauthlink.config.OAuthConfig;
import org.linuxdo.oauthlink.http.CallbackHttpServer;
import org.linuxdo.oauthlink.http.OAuthCallbackHandler;
import org.linuxdo.oauthlink.oauth.LinuxDoOAuthClient;
import org.linuxdo.oauthlink.oauth.OAuthError;
import org.linuxdo.oauthlink.oauth.OAuthException;
import org.linuxdo.oauthlink.oauth.PendingOAuthRegistry;
import org.linuxdo.oauthlink.service.OAuthLinkService;
import org.linuxdo.oauthlink.storage.YamlLinkRepository;
import org.linuxdo.oauthlink.util.OAuthCodeGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * MC-LinuxDO-OAuth-Link Plugin for Minecraft (Spigot/Paper).
 *
 * <p>Provides OAuth2 authentication via LinuxDO Connect to downstream plugins
 * through Bukkit Events and a static API.
 */
public final class OAuthLink extends JavaPlugin {

    private ExecutorService ioExecutor;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private YamlLinkRepository repository;
    private OAuthLinkService service;

    @Override
    public void onEnable() {
        Logger logger = getLogger();

        try {
            // 1. Load configuration
            OAuthConfig config = OAuthConfig.load(this);
            logger.info("配置加载完成");

            // 2. Create infrastructure
            this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "OAuthLink-IO");
                t.setDaemon(true);
                return t;
            });

            // JDK 21 TLS 1.3 ClientHello triggers Cloudflare edge to terminate
            // the handshake (Remote host terminated the handshake). Force TLS 1.2
            // only to avoid JDK 21 ↔ Cloudflare TLS 1.3 incompatibility.
            // Must be set BEFORE creating any HttpClient — the JDK reads it once
            // and caches the default SSLContext on first use.
            System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
            System.setProperty("https.protocols", "TLSv1.2");

            SSLContext sslContext;
            try {
                sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(null, null, null);
            } catch (NoSuchAlgorithmException e) {
                // Fallback to default (may still fail if default is TLS 1.3)
                sslContext = SSLContext.getDefault();
                logger.warning("TLSv1.2 SSLContext 不可用，回退到默认: " + e.getMessage());
            }

            // Cloudflare-hosted servers may need extra handshake time for
            // OCSP stapling and intermediate CA chain resolution.
            Duration handshakeTimeout = Duration.ofSeconds(30);

            this.httpClient = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(handshakeTimeout)
                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                    .build();

            this.objectMapper = new ObjectMapper();
            this.objectMapper.configure(
                    com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // 3. Create components
            Clock clock = Clock.systemDefaultZone();
            Path dataPath = getDataFolder().toPath().resolve(config.getStorageFile());

            // Ensure data folder exists
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            OAuthCodeGenerator codeGenerator = new OAuthCodeGenerator();

            this.repository = new YamlLinkRepository(dataPath, ioExecutor, logger);

            Duration stateTtl = Duration.ofSeconds(config.getStateTtlSeconds());
            Duration linkCodeTtl = Duration.ofSeconds(config.getLinkCodeTtlSeconds());
            PendingOAuthRegistry registry = new PendingOAuthRegistry(codeGenerator, clock, stateTtl, linkCodeTtl);

            LinuxDoOAuthClient oauthClient = new LinuxDoOAuthClient(
                    httpClient, objectMapper, config, logger);

            // 4. Create service (needed by callback handler for auto-bind)
            this.service = new OAuthLinkService(
                    this, config, repository, registry, oauthClient,
                    null, clock, logger); // callbackServer set after creation

            OAuthCallbackHandler callbackHandler = new OAuthCallbackHandler(
                    registry, oauthClient,
                    service::onAutoBind, // <-- auto-bind callback
                    logger);

            CallbackHttpServer callbackServer = new CallbackHttpServer(
                    config, callbackHandler, logger);

            service.setCallbackServer(callbackServer);

            // 5. Initialize service (loads data, starts callback server, registers API)
            service.onEnable();

            // 6. Register commands
            Objects.requireNonNull(getCommand("linkld"))
                    .setExecutor(new LinkCommand(this, service, logger));
            Objects.requireNonNull(getCommand("oauthlink"))
                    .setExecutor(new OAuthLinkCommand(this, service, logger));

            logger.info("OAuthLink 已启动");

        } catch (OAuthException e) {
            logger.severe("配置验证失败: " + e.getSafeMessage());
            logger.severe("请修改 config.yml 后使用 /oauthlink reload 重新加载");
            // Keep plugin enabled so admin can fix config and reload
        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "插件启动时发生未预期错误", e);
        }
    }

    @Override
    public void onDisable() {
        Logger logger = getLogger();

        try {
            if (service != null) {
                service.onDisable();
            }
        } catch (Exception e) {
            logger.log(java.util.logging.Level.WARNING, "关闭服务时发生错误", e);
        }

        if (ioExecutor != null) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("OAuthLink 已关闭");
    }

    /**
     * Reloads configuration and propagates changes to all running components.
     * Called by /oauthlink reload.
     *
     * <p>Named reloadOAuthConfig (not reloadConfig) to avoid overriding
     * {@link org.bukkit.plugin.java.JavaPlugin#reloadConfig()}.
     */
    public void reloadOAuthConfig() {
        Logger logger = getLogger();
        try {
            OAuthConfig newConfig = OAuthConfig.load(this);
            if (service != null) {
                service.reloadConfig(newConfig);
                // reloadConfig() logs success; no double-log here
            } else {
                logger.info("配置已重新加载（服务未启动，仅校验通过）");
            }
        } catch (OAuthException e) {
            logger.warning("配置重新加载失败: " + e.getSafeMessage());
            throw e;
        } catch (Exception e) {
            logger.log(java.util.logging.Level.WARNING, "配置重新加载时发生未预期错误", e);
            throw new RuntimeException("配置重新加载失败: " + e.getMessage(), e);
        }
    }
}
