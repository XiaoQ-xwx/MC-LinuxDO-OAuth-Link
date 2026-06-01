package org.OAuth_Framework.oAuth_Framework.api;

import org.OAuth_Framework.oAuth_Framework.model.LinkedAccount;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Static convenience facade for the OAuth Framework API.
 * Delegates all calls to the registered OAuthFrameworkProvider.
 *
 * <p>Usage from downstream plugins:
 * <pre>
 *   if (OAuthFrameworkAPI.isLinked(player.getUniqueId())) {
 *       OAuthFrameworkAPI.getLinkedAccount(player.getUniqueId())
 *           .ifPresent(account -> {
 *               getLogger().info("LinuxDO user: " + account.linuxDoUsername());
 *           });
 *   }
 * </pre>
 */
public final class OAuthFrameworkAPI {

    private static volatile OAuthFrameworkProvider provider;

    private OAuthFrameworkAPI() {
        // Static utility class
    }

    /**
     * Registers the provider. Called by OAuth_Framework plugin on startup.
     */
    public static void register(@NotNull OAuthFrameworkProvider provider) {
        OAuthFrameworkAPI.provider = provider;
    }

    /**
     * Unregisters the provider. Called by OAuth_Framework plugin on shutdown.
     */
    public static void unregister(@NotNull OAuthFrameworkProvider provider) {
        if (OAuthFrameworkAPI.provider == provider) {
            OAuthFrameworkAPI.provider = null;
        }
    }

    /**
     * Returns whether the player has a valid, linked LinuxDO account.
     *
     * @param playerId Minecraft player UUID
     * @return true if linked, false if not linked or provider not registered
     */
    public static boolean isLinked(@NotNull UUID playerId) {
        OAuthFrameworkProvider p = provider;
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
        OAuthFrameworkProvider p = provider;
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
        OAuthFrameworkProvider p = provider;
        if (p == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("OAuthFrameworkAPI provider not registered"));
        }
        return p.unlink(playerId);
    }
}
