package org.OAuth_Framework.oAuth_Framework.service;

import org.OAuth_Framework.oAuth_Framework.api.OAuthFrameworkAPI;
import org.OAuth_Framework.oAuth_Framework.api.OAuthFrameworkProvider;
import org.OAuth_Framework.oAuth_Framework.config.OAuthConfig;
import org.OAuth_Framework.oAuth_Framework.event.PlayerOAuthFailEvent;
import org.OAuth_Framework.oAuth_Framework.event.PlayerOAuthSuccessEvent;
import org.OAuth_Framework.oAuth_Framework.http.CallbackHttpServer;
import org.OAuth_Framework.oAuth_Framework.model.LinkedAccount;
import org.OAuth_Framework.oAuth_Framework.model.PendingAuthorization;
import org.OAuth_Framework.oAuth_Framework.oauth.LinuxDoOAuthClient;
import org.OAuth_Framework.oAuth_Framework.oauth.OAuthError;
import org.OAuth_Framework.oAuth_Framework.oauth.OAuthException;
import org.OAuth_Framework.oAuth_Framework.oauth.PendingOAuthRegistry;
import org.OAuth_Framework.oAuth_Framework.storage.LinkRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core business logic orchestrator.
 * Implements OAuthFrameworkProvider for the public API.
 * Coordinates OAuth flow, storage, events, and callback server.
 */
public class OAuthFrameworkService implements OAuthFrameworkProvider {

    private final JavaPlugin plugin;
    private final OAuthConfig config;
    private final LinkRepository repository;
    private final PendingOAuthRegistry registry;
    private final LinuxDoOAuthClient oauthClient;
    private final CallbackHttpServer callbackServer;
    private final Clock clock;
    private final Logger logger;

    public OAuthFrameworkService(JavaPlugin plugin, OAuthConfig config,
                                  LinkRepository repository, PendingOAuthRegistry registry,
                                  LinuxDoOAuthClient oauthClient, CallbackHttpServer callbackServer,
                                  Clock clock, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.registry = registry;
        this.oauthClient = oauthClient;
        this.callbackServer = callbackServer;
        this.clock = clock;
        this.logger = logger;
    }

    // === Lifecycle ===

    public void onEnable() {
        // Load persisted data
        repository.load().join();
        logger.info("用户数据加载完成");

        // Start callback server
        try {
            callbackServer.start();
        } catch (IOException e) {
            logger.log(Level.WARNING, "HTTP 回调服务器启动失败: " + e.getMessage());
            logger.info("插件将以手动模式运行（/link <code> 可用）");
        }

        // Register public API
        OAuthFrameworkAPI.register(this);
        Bukkit.getServicesManager().register(OAuthFrameworkProvider.class, this, plugin, ServicePriority.Normal);
        logger.info("OAuth Framework API 已注册");
    }

    public void onDisable() {
        // Unregister API
        OAuthFrameworkAPI.unregister(this);
        Bukkit.getServicesManager().unregister(this);

        // Stop callback server
        callbackServer.stop();

        // Flush data
        repository.flush().join();
    }

    // === OAuthFrameworkProvider implementation ===

    @Override
    public boolean isLinked(@NotNull UUID playerId) {
        Optional<LinkedAccount> account = repository.findByPlayer(playerId);
        if (account.isEmpty()) return false;
        Duration skew = Duration.ofSeconds(config.getTokenExpirySkewSeconds());
        return !account.get().isTokenExpired(clock, skew);
    }

    @Override
    @NotNull
    public Optional<LinkedAccount> getLinkedAccount(@NotNull UUID playerId) {
        Optional<LinkedAccount> account = repository.findByPlayer(playerId);
        if (account.isEmpty()) return Optional.empty();
        Duration skew = Duration.ofSeconds(config.getTokenExpirySkewSeconds());
        if (account.get().isTokenExpired(clock, skew)) {
            return Optional.empty();
        }
        return account;
    }

    @Override
    @NotNull
    public CompletableFuture<Void> unlink(@NotNull UUID playerId) {
        return repository.delete(playerId);
    }

    // === OAuth Flow ===

    /**
     * Creates an authorization URI for a player to initiate OAuth.
     */
    public URI createAuthorizationUri(UUID playerId) {
        String state = registry.createState();
        return oauthClient.buildAuthorizationUri(state);
    }

    /**
     * Links a player using a manual link code from the callback.
     * Completes asynchronously and fires events on the main thread.
     */
    public CompletableFuture<LinkedAccount> linkPlayer(UUID playerId, String playerName,
                                                        String linkCode) {
        return CompletableFuture.supplyAsync(() -> {
            // Validate link code
            PendingAuthorization pending = registry.consumeCode(linkCode);
            if (pending == null) {
                throw new OAuthException(OAuthError.INVALID_LINK_CODE);
            }
            if (pending.isExpired(clock)) {
                throw new OAuthException(OAuthError.EXPIRED_LINK_CODE);
            }

            // Check if player already linked to a different LinuxDO account
            Optional<LinkedAccount> existing = repository.findByPlayer(playerId);
            if (existing.isPresent()
                    && !existing.get().linuxDoId().equals(pending.profile().id())
                    && !existing.get().isTokenExpired(clock,
                        Duration.ofSeconds(config.getTokenExpirySkewSeconds()))) {
                throw new OAuthException(OAuthError.ALREADY_LINKED);
            }

            // Check if LinuxDO account already linked to a different player
            Optional<LinkedAccount> byLdoId = repository.findByLinuxDoId(pending.profile().id());
            if (byLdoId.isPresent() && !byLdoId.get().playerId().equals(playerId)) {
                throw new OAuthException(OAuthError.ALREADY_LINKED,
                        "该 LinuxDO 账号已绑定玩家: " + byLdoId.get().playerName());
            }

            // Build linked account
            Instant now = Instant.now(clock);
            LinkedAccount account = new LinkedAccount(
                    playerId,
                    playerName,
                    pending.profile().id(),
                    pending.profile().username(),
                    now,
                    pending.tokens().expiresAt());

            // Persist
            repository.save(account).join();

            // Fire success event on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerOAuthSuccessEvent event = new PlayerOAuthSuccessEvent(account);
                Bukkit.getPluginManager().callEvent(event);
            });

            return account;
        }).exceptionallyCompose(throwable -> {
            OAuthError error;
            String safeMessage;
            if (throwable.getCause() instanceof OAuthException oa) {
                error = oa.getError();
                safeMessage = oa.getSafeMessage();
            } else if (throwable instanceof OAuthException oa) {
                error = oa.getError();
                safeMessage = oa.getSafeMessage();
            } else {
                error = OAuthError.NETWORK_FAILED;
                safeMessage = error.defaultMessage();
                logger.log(Level.WARNING, "绑定账号时发生未知错误", throwable);
            }

            // Fire fail event on main thread
            String finalMessage = safeMessage;
            OAuthError finalError = error;
            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerOAuthFailEvent event = new PlayerOAuthFailEvent(playerId, finalError, finalMessage);
                Bukkit.getPluginManager().callEvent(event);
            });

            return CompletableFuture.failedFuture(
                    new OAuthException(error, safeMessage, throwable));
        });
    }
}
