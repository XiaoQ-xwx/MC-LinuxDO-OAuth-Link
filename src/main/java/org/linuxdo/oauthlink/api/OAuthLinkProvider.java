package org.linuxdo.oauthlink.api;

import org.linuxdo.oauthlink.model.LinkedAccount;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for the OAuthLink API.
 * Registered via Bukkit ServicesManager.
 * Downstream plugins should use {@link OAuthLinkAPI} static facade
 * or obtain this interface from ServicesManager.
 */
public interface OAuthLinkProvider {

    /**
     * Returns whether the player has a valid, linked LinuxDO account.
     *
     * @param playerId Minecraft player UUID
     * @return true if linked and token not expired
     */
    boolean isLinked(@NotNull UUID playerId);

    /**
     * Retrieves the cached linked account information.
     *
     * @param playerId Minecraft player UUID
     * @return Optional containing the linked account, or empty if not linked
     */
    @NotNull
    Optional<LinkedAccount> getLinkedAccount(@NotNull UUID playerId);

    /**
     * Unlinks a player's LinuxDO account and removes cached data.
     *
     * @param playerId Minecraft player UUID
     * @return a future that completes when the unlinking is persisted
     */
    @NotNull
    CompletableFuture<Void> unlink(@NotNull UUID playerId);
}
