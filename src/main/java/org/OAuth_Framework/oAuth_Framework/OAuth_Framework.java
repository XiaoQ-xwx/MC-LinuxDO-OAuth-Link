package org.OAuth_Framework.oAuth_Framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.OAuth_Framework.oAuth_Framework.command.LinkCommand;
import org.OAuth_Framework.oAuth_Framework.command.OAuthFrameworkCommand;
import org.OAuth_Framework.oAuth_Framework.config.OAuthConfig;
import org.OAuth_Framework.oAuth_Framework.http.CallbackHttpServer;
import org.OAuth_Framework.oAuth_Framework.http.OAuthCallbackHandler;
import org.OAuth_Framework.oAuth_Framework.oauth.LinuxDoOAuthClient;
import org.OAuth_Framework.oAuth_Framework.oauth.OAuthError;
import org.OAuth_Framework.oAuth_Framework.oauth.OAuthException;
import org.OAuth_Framework.oAuth_Framework.oauth.PendingOAuthRegistry;
import org.OAuth_Framework.oAuth_Framework.service.OAuthFrameworkService;
import org.OAuth_Framework.oAuth_Framework.storage.YamlLinkRepository;
import org.OAuth_Framework.oAuth_Framework.util.OAuthCodeGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * LinuxDO OAuth Framework Plugin for Minecraft (Spigot/Paper).
 *
 * <p>Provides OAuth2 authentication via LinuxDO Connect to downstream plugins
 * through Bukkit Events and a static API.
 */
public final class OAuth_Framework extends JavaPlugin {

    private ExecutorService ioExecutor;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private YamlLinkRepository repository;
    private OAuthFrameworkService service;

    @Override
    public void onEnable() {
        Logger logger = getLogger();

        try {
            // 1. Load configuration
            OAuthConfig config = OAuthConfig.load(this);
            logger.info("配置加载完成");

            // 2. Create infrastructure
            this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "OAuth-Framework-IO");
                t.setDaemon(true);
                return t;
            });

            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
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

            OAuthCallbackHandler callbackHandler = new OAuthCallbackHandler(
                    registry, oauthClient, logger);

            CallbackHttpServer callbackServer = new CallbackHttpServer(
                    config, callbackHandler, logger);

            // 4. Create service
            this.service = new OAuthFrameworkService(
                    this, config, repository, registry, oauthClient,
                    callbackServer, clock, logger);

            // 5. Initialize service (loads data, starts callback server, registers API)
            service.onEnable();

            // 6. Register commands
            Objects.requireNonNull(getCommand("link"))
                    .setExecutor(new LinkCommand(service, logger));
            Objects.requireNonNull(getCommand("oauthframework"))
                    .setExecutor(new OAuthFrameworkCommand(this, service, logger));

            logger.info("OAuth_Framework 已启动");

        } catch (OAuthException e) {
            logger.severe("配置验证失败: " + e.getSafeMessage());
            logger.severe("请修改 config.yml 后使用 /oauthfw reload 重新加载");
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

        logger.info("OAuth_Framework 已关闭");
    }
}
