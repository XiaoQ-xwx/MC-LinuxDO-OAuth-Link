package org.linuxdo.oauthlink.storage;

import org.linuxdo.oauthlink.model.LinkedAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for YamlLinkRepository.
 *
 * <p>Tests in-memory operations (find/save/delete) and async I/O behavior.
 * File-level I/O tests use a temporary directory for isolation.
 */
class YamlLinkRepositoryTest {

    @TempDir
    Path tempDir;

    private Path dataFile;
    private ExecutorService ioExecutor;
    private YamlLinkRepository repository;
    private Logger logger;

    private static final UUID PLAYER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        dataFile = tempDir.resolve("test-data.yml");
        ioExecutor = Executors.newSingleThreadExecutor();
        logger = Logger.getLogger("test");
        repository = new YamlLinkRepository(dataFile, ioExecutor, logger);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        ioExecutor.shutdown();
        ioExecutor.awaitTermination(2, TimeUnit.SECONDS);
    }

    // ── load ─────────────────────────────────────────────────

    @Test
    void load_WhenFileDoesNotExist_ShouldCompleteWithoutError() throws Exception {
        CompletableFuture<Void> future = repository.load();
        future.get(5, TimeUnit.SECONDS);

        // Should not throw, in-memory maps should be empty
        assertTrue(repository.findByPlayer(PLAYER_A).isEmpty());
    }

    @Test
    void load_WhenFileDoesNotExist_ShouldReturnCompletedFuture() {
        CompletableFuture<Void> future = repository.load();

        // Future should complete successfully (eventually)
        assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
    }

    // ── findByPlayer ─────────────────────────────────────────

    @Test
    void findByPlayer_UnknownPlayer_ShouldReturnEmpty() {
        Optional<LinkedAccount> result = repository.findByPlayer(PLAYER_A);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByPlayer_AfterSave_ShouldReturnAccount() throws Exception {
        LinkedAccount account = createAccount(PLAYER_A, "PlayerA", "ld-a", "usera");
        repository.save(account).get(5, TimeUnit.SECONDS);

        Optional<LinkedAccount> result = repository.findByPlayer(PLAYER_A);

        assertTrue(result.isPresent());
        assertEquals("usera", result.get().linuxDoUsername());
        assertEquals("ld-a", result.get().linuxDoId());
    }

    @Test
    void findByPlayer_AfterSave_ShouldReturnExactSameData() throws Exception {
        Instant linkedAt = Instant.parse("2026-06-01T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-06-01T12:00:00Z");
        LinkedAccount account = new LinkedAccount(PLAYER_A, "PlayerA", "ld-123", "linuxdo_user", linkedAt, expiresAt);
        repository.save(account).get(5, TimeUnit.SECONDS);

        Optional<LinkedAccount> result = repository.findByPlayer(PLAYER_A);

        assertTrue(result.isPresent());
        LinkedAccount found = result.get();
        assertEquals(PLAYER_A, found.playerId());
        assertEquals("PlayerA", found.playerName());
        assertEquals("ld-123", found.linuxDoId());
        assertEquals("linuxdo_user", found.linuxDoUsername());
        assertEquals(linkedAt, found.linkedAt());
        assertEquals(expiresAt, found.tokenExpiresAt());
    }

    // ── findByLinuxDoId ──────────────────────────────────────

    @Test
    void findByLinuxDoId_UnknownId_ShouldReturnEmpty() {
        Optional<LinkedAccount> result = repository.findByLinuxDoId("unknown-id");

        assertTrue(result.isEmpty());
    }

    @Test
    void findByLinuxDoId_AfterSave_ShouldReturnAccount() throws Exception {
        LinkedAccount account = createAccount(PLAYER_A, "PlayerA", "ld-a", "usera");
        repository.save(account).get(5, TimeUnit.SECONDS);

        Optional<LinkedAccount> result = repository.findByLinuxDoId("ld-a");

        assertTrue(result.isPresent());
        assertEquals(PLAYER_A, result.get().playerId());
    }

    // ── save ─────────────────────────────────────────────────

    @Test
    void save_ShouldReturnCompletedFuture() throws Exception {
        LinkedAccount account = createAccount(PLAYER_A, "PlayerA", "ld-a", "usera");
        CompletableFuture<Void> future = repository.save(account);

        assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void save_ShouldOverwriteExistingEntry() throws Exception {
        LinkedAccount v1 = createAccount(PLAYER_A, "PlayerA_old", "ld-a", "oldname");
        repository.save(v1).get(5, TimeUnit.SECONDS);

        LinkedAccount v2 = createAccount(PLAYER_A, "PlayerA_new", "ld-a", "newname");
        repository.save(v2).get(5, TimeUnit.SECONDS);

        Optional<LinkedAccount> result = repository.findByPlayer(PLAYER_A);
        assertTrue(result.isPresent());
        assertEquals("newname", result.get().linuxDoUsername());
        assertEquals("PlayerA_new", result.get().playerName());
    }

    @Test
    void save_MultiplePlayers_ShouldKeepAllEntries() throws Exception {
        LinkedAccount accountA = createAccount(PLAYER_A, "PlayerA", "ld-a", "usera");
        LinkedAccount accountB = createAccount(PLAYER_B, "PlayerB", "ld-b", "userb");

        repository.save(accountA).get(5, TimeUnit.SECONDS);
        repository.save(accountB).get(5, TimeUnit.SECONDS);

        assertTrue(repository.findByPlayer(PLAYER_A).isPresent());
        assertTrue(repository.findByPlayer(PLAYER_B).isPresent());
        assertTrue(repository.findByLinuxDoId("ld-a").isPresent());
        assertTrue(repository.findByLinuxDoId("ld-b").isPresent());
    }

    // ── delete ───────────────────────────────────────────────

    @Test
    void delete_ExistingPlayer_ShouldRemoveEntry() throws Exception {
        LinkedAccount account = createAccount(PLAYER_A, "PlayerA", "ld-a", "usera");
        repository.save(account).get(5, TimeUnit.SECONDS);

        repository.delete(PLAYER_A).get(5, TimeUnit.SECONDS);

        assertTrue(repository.findByPlayer(PLAYER_A).isEmpty());
        assertTrue(repository.findByLinuxDoId("ld-a").isEmpty());
    }

    @Test
    void delete_UnknownPlayer_ShouldCompleteWithoutError() throws Exception {
        CompletableFuture<Void> future = repository.delete(PLAYER_A);

        assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void delete_ShouldNotAffectOtherEntries() throws Exception {
        LinkedAccount accountA = createAccount(PLAYER_A, "PlayerA", "ld-a", "usera");
        LinkedAccount accountB = createAccount(PLAYER_B, "PlayerB", "ld-b", "userb");
        repository.save(accountA).get(5, TimeUnit.SECONDS);
        repository.save(accountB).get(5, TimeUnit.SECONDS);

        repository.delete(PLAYER_A).get(5, TimeUnit.SECONDS);

        // Player B should be unaffected
        assertTrue(repository.findByPlayer(PLAYER_B).isPresent());
        assertTrue(repository.findByLinuxDoId("ld-b").isPresent());
        // Player A should be gone
        assertTrue(repository.findByPlayer(PLAYER_A).isEmpty());
        assertTrue(repository.findByLinuxDoId("ld-a").isEmpty());
    }

    // ── delete on re-link scenario ───────────────────────────

    @Test
    void save_AfterDelete_ShouldWorkCorrectly() throws Exception {
        // Save initial account
        LinkedAccount account = createAccount(PLAYER_A, "PlayerA", "ld-a", "usera");
        repository.save(account).get(5, TimeUnit.SECONDS);

        // Delete
        repository.delete(PLAYER_A).get(5, TimeUnit.SECONDS);
        assertTrue(repository.findByPlayer(PLAYER_A).isEmpty());

        // Re-save with different LinuxDO ID (player re-linked)
        LinkedAccount newAccount = createAccount(PLAYER_A, "PlayerA", "ld-new", "newuser");
        repository.save(newAccount).get(5, TimeUnit.SECONDS);

        assertTrue(repository.findByPlayer(PLAYER_A).isPresent());
        assertEquals("ld-new", repository.findByPlayer(PLAYER_A).get().linuxDoId());
        assertTrue(repository.findByLinuxDoId("ld-a").isEmpty());
        assertTrue(repository.findByLinuxDoId("ld-new").isPresent());
    }

    // ── flush ────────────────────────────────────────────────

    @Test
    void flush_ShouldCompleteSuccessfully() throws Exception {
        LinkedAccount account = createAccount(PLAYER_A, "PlayerA", "ld-a", "usera");
        repository.save(account).get(5, TimeUnit.SECONDS);

        CompletableFuture<Void> future = repository.flush();
        assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
    }

    // ── Concurrent save consistency ──────────────────────────

    @Test
    void concurrentSaves_ShouldMaintainConsistentState() throws Exception {
        int count = 10;
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[count];

        for (int i = 0; i < count; i++) {
            UUID playerId = UUID.randomUUID();
            String name = "Player" + i;
            String ldId = "ld-" + i;
            String ldUser = "user" + i;
            futures[i] = repository.save(createAccount(playerId, name, ldId, ldUser));
        }

        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);

        // Verify all entries were saved
        for (int i = 0; i < count; i++) {
            assertTrue(repository.findByLinuxDoId("ld-" + i).isPresent(),
                    "Entry ld-" + i + " should exist after concurrent save");
        }
    }

    // ── helpers ──────────────────────────────────────────────

    private static LinkedAccount createAccount(UUID playerId, String playerName,
                                               String linuxDoId, String linuxDoUsername) {
        Instant now = Instant.parse("2026-06-01T12:00:00Z");
        return new LinkedAccount(playerId, playerName, linuxDoId, linuxDoUsername,
                now, now.plusSeconds(3600));
    }
}
