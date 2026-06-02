package org.linuxdo.oauthlink.service;

import org.linuxdo.oauthlink.api.OAuthLinkAPI;
import org.linuxdo.oauthlink.api.OAuthLinkProvider;
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
import org.bukkit.entity.Player;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core business logic orchestrator.
 * Implements OAuthLinkProvider for the public API.
 * Coordinates OAuth flow, storage, events, and callback server.
 */
public class OAuthLinkService implements OAuthLinkProvider {

    private final JavaPlugin plugin;
    private final OAuthConfig config;
    private final LinkRepository repository;
    private final PendingOAuthRegistry registry;
    private final LinuxDoOAuthClient oauthClient;
    private CallbackHttpServer callbackServer;
    private final Clock clock;
    private final Logger logger;
    /** Dedicated thread pool for async I/O — avoids blocking ForkJoinPool.commonPool(). */
    private final Executor asyncExecutor;

    public OAuthLinkService(JavaPlugin plugin, OAuthConfig config,
                                  LinkRepository repository, PendingOAuthRegistry registry,
                                  LinuxDoOAuthClient oauthClient, CallbackHttpServer callbackServer,
                                  Clock clock, Logger logger) {
        this(plugin, config, repository, registry, oauthClient, callbackServer,
                clock, logger, createDefaultAsyncExecutor());
    }

    /** Package-private constructor for testing with injected executor. */
    OAuthLinkService(JavaPlugin plugin, OAuthConfig config,
                           LinkRepository repository, PendingOAuthRegistry registry,
                           LinuxDoOAuthClient oauthClient, CallbackHttpServer callbackServer,
                           Clock clock, Logger logger, Executor asyncExecutor) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.registry = registry;
        this.oauthClient = oauthClient;
        this.callbackServer = callbackServer;
        this.clock = clock;
        this.logger = logger;
        this.asyncExecutor = asyncExecutor;
    }

    private static Executor createDefaultAsyncExecutor() {
        return Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "OAuthLink-Async");
            t.setDaemon(true);
            return t;
        });
    }

    /** Sets the callback server (called after construction, before onEnable). */
    public void setCallbackServer(CallbackHttpServer callbackServer) {
        this.callbackServer = callbackServer;
    }

    // === Lifecycle ===

    public void onEnable() {
        // Load persisted data
        repository.load().join();
        logger.info("用户数据加载完成");

        // Start callback server
        if (callbackServer != null) {
            try {
                callbackServer.start();
            } catch (IOException e) {
                logger.log(Level.WARNING, "HTTP 回调服务器启动失败: " + e.getMessage());
                logger.info("插件将以手动模式运行（/linkLD <code> 可用）");
            }
        }

        // Register public API
        OAuthLinkAPI.register(this);
        Bukkit.getServicesManager().register(OAuthLinkProvider.class, this, plugin, ServicePriority.Normal);
        logger.info("OAuthLink API 已注册");
        logger.info("OAuthLink API 已注册");
    }

    public void onDisable() {
        // Unregister API
        OAuthLinkAPI.unregister(this);
        Bukkit.getServicesManager().unregister(this);

        // Stop callback server
        if (callbackServer != null) {
            callbackServer.stop();
        }

        // Flush data
        repository.flush().join();
    }

    // === OAuthLinkProvider implementation ===

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
        Optional<LinkedAccount> account = repository.findByPlayer(playerId);
        return repository.delete(playerId).thenRun(() -> {
            // Fire unlink event on main thread so downstream plugins can cascade-revoke
            account.ifPresent(acc -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    PlayerOAuthUnlinkEvent event = new PlayerOAuthUnlinkEvent(acc);
                    Bukkit.getPluginManager().callEvent(event);
                });
            });
        });
    }

    // === OAuth Flow ===

    /**
     * Creates an authorization URI bound to the player's identity.
     * The callback will auto-complete the binding without a link code.
     */
    public URI createAuthorizationUri(UUID playerId, String playerName) {
        String state = registry.createState(playerId, playerName);
        return oauthClient.buildAuthorizationUri(state);
    }

    /**
     * Auto-bind callback — called from the HTTP handler thread when OAuth succeeds.
     * Saves the linked account, fires events, and messages the player if online.
     */
    public void onAutoBind(UUID playerId, String playerName, LinuxDoProfile profile, OAuthTokens tokens) {
        // Check if player already linked to a different LinuxDO account
        Optional<LinkedAccount> existing = repository.findByPlayer(playerId);
        if (existing.isPresent()
                && !existing.get().linuxDoId().equals(profile.id())
                && !existing.get().isTokenExpired(clock,
                    Duration.ofSeconds(config.getTokenExpirySkewSeconds()))) {
            OAuthException oa = new OAuthException(OAuthError.ALREADY_LINKED);
            fireFailEvent(playerId, OAuthError.ALREADY_LINKED, oa.getSafeMessage());
            return;
        }

        // Check if LinuxDO account already linked to a different player
        Optional<LinkedAccount> byLdoId = repository.findByLinuxDoId(profile.id());
        if (byLdoId.isPresent() && !byLdoId.get().playerId().equals(playerId)) {
            OAuthException oa = new OAuthException(OAuthError.ALREADY_LINKED,
                    "该 LinuxDO 账号已绑定玩家: " + byLdoId.get().playerName());
            fireFailEvent(playerId, OAuthError.ALREADY_LINKED, oa.getSafeMessage());
            return;
        }

        // Build and persist linked account
        Instant now = Instant.now(clock);
        LinkedAccount account = new LinkedAccount(
                playerId, playerName, profile.id(), profile.username(),
                profile.displayName(), profile.trustLevel(), profile.likesReceived(),
                profile.rawJson(),
                now, tokens.expiresAt());

        repository.save(account).join();

        // Fire success event + message on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerOAuthSuccessEvent event = new PlayerOAuthSuccessEvent(account);
            Bukkit.getPluginManager().callEvent(event);

            // Notify online player
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                String msg = org.bukkit.ChatColor.GREEN + "✔ 已自动绑定 LinuxDO 账号: @"
                        + org.bukkit.ChatColor.WHITE + profile.username();
                player.sendMessage(msg);
            }
        });

        logger.info("玩家 " + playerName + " 绑定 LinuxDO 账号 @"
                + profile.username() + " 成功");
    }

    private void fireFailEvent(UUID playerId, OAuthError error, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerOAuthFailEvent event = new PlayerOAuthFailEvent(playerId, error, message);
            Bukkit.getPluginManager().callEvent(event);
        });
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
                    pending.profile().displayName(),
                    pending.profile().trustLevel(),
                    pending.profile().likesReceived(),
                    pending.profile().rawJson(),
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
        }, asyncExecutor).exceptionallyCompose(throwable -> {
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
