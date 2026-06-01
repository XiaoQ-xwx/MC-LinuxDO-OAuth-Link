package org.OAuth_Framework.oAuth_Framework.storage;

import org.OAuth_Framework.oAuth_Framework.model.LinkedAccount;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * API boundary for persisted account links.
 * All write operations are async. Reads are synchronous from in-memory cache.
 */
public interface LinkRepository {

    /**
     * Loads all linked accounts from persistent storage into memory.
     */
    CompletableFuture<Void> load();

    /**
     * Finds a linked account by player UUID (from in-memory cache).
     */
    Optional<LinkedAccount> findByPlayer(UUID playerId);

    /**
     * Finds a linked account by LinuxDO user ID (from in-memory cache).
     */
    Optional<LinkedAccount> findByLinuxDoId(String linuxDoId);

    /**
     * Persists a linked account. Overwrites existing entry for the same player.
     */
    CompletableFuture<Void> save(LinkedAccount account);

    /**
     * Removes a linked account by player UUID.
     */
    CompletableFuture<Void> delete(UUID playerId);

    /**
     * Force-writes current in-memory state to disk.
     */
    CompletableFuture<Void> flush();
}
