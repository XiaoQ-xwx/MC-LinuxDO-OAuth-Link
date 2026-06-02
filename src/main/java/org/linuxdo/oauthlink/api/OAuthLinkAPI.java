package org.linuxdo.oauthlink.api;

import org.linuxdo.oauthlink.model.LinkedAccount;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Static convenience facade for the OAuthLink API.
 * Delegates all calls to the registered OAuthLinkProvider.
 *
 * <p>Usage from downstream plugins:
 * <pre>
 *   if (OAuthLinkAPI.isLinked(player.getUniqueId())) {
 *       OAuthLinkAPI.getLinkedAccount(player.getUniqueId())
 *           .ifPresent(account -> {
 *               getLogger().info("LinuxDO user: " + account.linuxDoUsername());
 *           });
 *   }
 * </pre>
 */
public final class OAuthLinkAPI {

    private static volatile OAuthLinkProvider provider;

    private OAuthLinkAPI() {
        // Static utility class
    }

    /**
     * Registers the provider. Called by OAuthLink plugin on startup.
     */
    public static void register(@NotNull OAuthLinkProvider provider) {
        OAuthLinkAPI.provider = provider;
    }

    /**
     * Unregisters the provider. Called by OAuthLink plugin on shutdown.
     */
    public static void unregister(@NotNull OAuthLinkProvider provider) {
        if (OAuthLinkAPI.provider == provider) {
            OAuthLinkAPI.provider = null;
        }
    }

    /**
     * Returns whether the player has a valid, linked LinuxDO account.
     *
     * @param playerId Minecraft player UUID
     * @return true if linked, false if not linked or provider not registered
     */
    public static boolean isLinked(@NotNull UUID playerId) {
        OAuthLinkProvider p = provider;
        if (p == null) return false;
        return p.isLinked(playerId);
    }

    /**
     * Retrieves the cached linked account information.
     *
     * @param playerId Minecraft player UUID
     * @return Optional containing the linked account, or empty if not linked or provider not registered
     */
    @NotNull
    public static Optional<LinkedAccount> getLinkedAccount(@NotNull UUID playerId) {
        OAuthLinkProvider p = provider;
        if (p == null) return Optional.empty();
        return p.getLinkedAccount(playerId);
    }

    /**
     * Unlinks a player's LinuxDO account.
     *
     * @param playerId Minecraft player UUID
     * @return a future, or a failed future if provider is not registered
     */
    @NotNull
    public static CompletableFuture<Void> unlink(@NotNull UUID playerId) {
        OAuthLinkProvider p = provider;
        if (p == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("OAuthLinkAPI provider not registered"));
        }
        return p.unlink(playerId);
    }
}
