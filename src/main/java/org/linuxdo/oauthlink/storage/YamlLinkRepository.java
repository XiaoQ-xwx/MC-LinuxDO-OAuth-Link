package org.linuxdo.oauthlink.storage;

import org.linuxdo.oauthlink.model.LinkedAccount;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * YAML-backed implementation of LinkRepository.
 * Uses atomic temp-file writes to prevent corruption.
 * All file I/O executes on the provided ioExecutor.
 */
public class YamlLinkRepository implements LinkRepository {

    private final Path dataFile;
    private final ExecutorService ioExecutor;
    private final Logger logger;

    private final ConcurrentHashMap<UUID, LinkedAccount> byPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> byLinuxDoId = new ConcurrentHashMap<>();
    /** Guards compound updates across both maps to keep dual-index writes atomic. */
    private final Object indexLock = new Object();

    public YamlLinkRepository(Path dataFile, ExecutorService ioExecutor, Logger logger) {
        this.dataFile = dataFile;
        this.ioExecutor = ioExecutor;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Void> load() {
        return CompletableFuture.runAsync(() -> {
            if (!Files.exists(dataFile)) {
                logger.info("数据文件不存在，将创建新文件: " + dataFile);
                return;
            }
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile.toFile());
                var accountsSection = yaml.getConfigurationSection("accounts");
                if (accountsSection == null) {
                    return;
                }
                for (String key : accountsSection.getKeys(false)) {
                    try {
                        UUID playerId = UUID.fromString(key);
                        String playerName = accountsSection.getString(key + ".player-name", "");
                        String linuxDoId = accountsSection.getString(key + ".linuxdo-id", "");
                        String linuxDoUsername = accountsSection.getString(key + ".linuxdo-username", "");
                        String linuxDoDisplayName = accountsSection.getString(key + ".linuxdo-display-name", linuxDoUsername);
                        int trustLevel = accountsSection.getInt(key + ".trust-level", LinkedAccount.UNKNOWN);
                        int likesReceived = accountsSection.getInt(key + ".likes-received", LinkedAccount.UNKNOWN);
                        String rawProfileJson = accountsSection.getString(key + ".raw-profile-json", "");
                        long linkedAtEpoch = accountsSection.getLong(key + ".linked-at", 0);
                        long tokenExpiresAtEpoch = accountsSection.getLong(key + ".token-expires-at", 0);

                        if (linuxDoId.isEmpty()) continue;

                        LinkedAccount account = new LinkedAccount(
                                playerId, playerName, linuxDoId, linuxDoUsername,
                                linuxDoDisplayName, trustLevel, likesReceived,
                                rawProfileJson,
                                Instant.ofEpochSecond(linkedAtEpoch),
                                Instant.ofEpochSecond(tokenExpiresAtEpoch));

                        byPlayer.put(playerId, account);
                        byLinuxDoId.put(linuxDoId, playerId);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "解析账户数据失败: " + key, e);
                    }
                }
                logger.info("已加载 " + byPlayer.size() + " 个绑定账户");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "加载数据文件失败: " + dataFile, e);
            }
        }, ioExecutor);
    }

    @Override
    public Optional<LinkedAccount> findByPlayer(UUID playerId) {
        return Optional.ofNullable(byPlayer.get(playerId));
    }

    @Override
    public Optional<LinkedAccount> findByLinuxDoId(String linuxDoId) {
        UUID playerId = byLinuxDoId.get(linuxDoId);
        if (playerId == null) return Optional.empty();
        return Optional.ofNullable(byPlayer.get(playerId));
    }

    @Override
    public CompletableFuture<Void> save(LinkedAccount account) {
        synchronized (indexLock) {
            // Clean up stale reverse index when re-binding to a different LinuxDO account
            LinkedAccount old = byPlayer.get(account.playerId());
            if (old != null && !old.linuxDoId().equals(account.linuxDoId())) {
                byLinuxDoId.remove(old.linuxDoId());
            }
            byPlayer.put(account.playerId(), account);
            byLinuxDoId.put(account.linuxDoId(), account.playerId());
        }
        return writeSnapshot();
    }

    @Override
    public CompletableFuture<Void> delete(UUID playerId) {
        synchronized (indexLock) {
            LinkedAccount removed = byPlayer.remove(playerId);
            if (removed != null) {
                byLinuxDoId.remove(removed.linuxDoId());
            }
        }
        return writeSnapshot();
    }

    @Override
    public CompletableFuture<Void> flush() {
        return writeSnapshot();
    }

    private CompletableFuture<Void> writeSnapshot() {
        return CompletableFuture.runAsync(() -> {
            // Build snapshot from current state to avoid holding locks during I/O
            Map<UUID, LinkedAccount> snapshot = Map.copyOf(byPlayer);
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                for (var entry : snapshot.entrySet()) {
                    LinkedAccount account = entry.getValue();
                    String key = "accounts." + entry.getKey().toString();
                    yaml.set(key + ".player-name", account.playerName());
                    yaml.set(key + ".linuxdo-id", account.linuxDoId());
                    yaml.set(key + ".linuxdo-username", account.linuxDoUsername());
                    yaml.set(key + ".linuxdo-display-name", account.linuxDoDisplayName());
                    yaml.set(key + ".trust-level", account.trustLevel());
                    yaml.set(key + ".likes-received", account.likesReceived());
                    yaml.set(key + ".raw-profile-json", account.rawProfileJson());
                    yaml.set(key + ".linked-at", account.linkedAt().getEpochSecond());
                    yaml.set(key + ".token-expires-at", account.tokenExpiresAt().getEpochSecond());
                }

                // Atomic write: temp file then replace
                Path tempFile = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
                yaml.save(tempFile.toFile());
                Files.move(tempFile, dataFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "写入数据文件失败: " + dataFile, e);
                throw new RuntimeException("写入数据文件失败: " + dataFile, e);
            }
        }, ioExecutor);
    }
}
