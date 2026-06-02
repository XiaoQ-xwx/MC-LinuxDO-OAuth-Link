package org.linuxdo.oauthlink.command;

import org.linuxdo.oauthlink.model.LinkedAccount;
import org.linuxdo.oauthlink.service.OAuthLinkService;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LinkCommand — command routing and interaction logic.
 *
 * <p>Tests all subcommand paths without a real Bukkit server.
 * Bungee Chat API calls (sendMessage with BaseComponent) are verified
 * via the spigot().sendMessage() path on the mock Player.
 */
@ExtendWith(MockitoExtension.class)
class LinkCommandTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PLAYER_NAME = "TestPlayer";

    @Mock private OAuthLinkService service;
    @Mock private Player player;
    @Mock private ConsoleCommandSender consoleSender;
    @Mock private Command command;
    @Mock private BukkitScheduler scheduler;
    @Mock private PluginManager pluginManager;
    @Mock private Server server;
    @Mock private JavaPlugin plugin;

    private Logger logger;
    private LinkCommand linkCommand;
    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("LinkCommandTest");
        linkCommand = new LinkCommand(service, logger);

        // Player identity
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        lenient().when(player.getName()).thenReturn(PLAYER_NAME);

        // Mock Player.spigot() for Bungee Chat messages
        Player.Spigot spigotMock = mock(Player.Spigot.class);
        lenient().when(player.spigot()).thenReturn(spigotMock);

        // Static Bukkit mocks
        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);
        bukkitMock.when(Bukkit::isPrimaryThread).thenReturn(true);

        // runTask runs runnable synchronously (lenient: not all tests trigger async paths)
        lenient().doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(scheduler).runTask(any(), any(Runnable.class));

        // For getPlugin
        lenient().when(pluginManager.getPlugin("OAuthLink")).thenReturn(plugin);
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Non-player sender
    // ═══════════════════════════════════════════════════════════

    @Test
    void onCommand_ConsoleSender_ShouldSendErrorMessage() {
        boolean result = linkCommand.onCommand(consoleSender, command,
                "linkld", new String[0]);

        assertTrue(result);
        verify(consoleSender).sendMessage(anyString());
    }

    // ═══════════════════════════════════════════════════════════
    // /linkld — unlinked player → start OAuth flow
    // ═══════════════════════════════════════════════════════════

    @Test
    void onCommand_UnlinkedPlayer_ShouldStartOAuthFlow() {
        when(service.isLinked(PLAYER_UUID)).thenReturn(false);
        when(service.createAuthorizationUri(PLAYER_UUID, PLAYER_NAME))
                .thenReturn(URI.create("https://connect.linux.do/oauth2/authorize?state=abc"));

        boolean result = linkCommand.onCommand(player, command,
                "linkld", new String[0]);

        assertTrue(result);
        verify(player, atLeastOnce()).sendMessage(anyString());  // header + footer
        verify(player).spigot();  // clickable link message
    }

    // ═══════════════════════════════════════════════════════════
    // /linkld — linked player → show profile
    // ═══════════════════════════════════════════════════════════

    @Test
    void onCommand_LinkedPlayer_ShouldShowProfile() {
        Instant linkedAt = Instant.parse("2026-06-01T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-06-01T14:00:00Z");
        LinkedAccount account = new LinkedAccount(PLAYER_UUID, PLAYER_NAME,
                "ld-42", "linuxuser", linkedAt, expiresAt);

        when(service.isLinked(PLAYER_UUID)).thenReturn(true);
        when(service.getLinkedAccount(PLAYER_UUID)).thenReturn(Optional.of(account));

        boolean result = linkCommand.onCommand(player, command,
                "linkld", new String[0]);

        assertTrue(result);
        // Profile display sends multiple messages
        verify(player, atLeastOnce()).sendMessage(anyString());
        verify(player, atLeastOnce()).spigot();  // clickable profile link + unlink button
    }

    @Test
    void onCommand_LinkedButTokenExpired_ShouldStartOAuthFlow() {
        // isLinked returns false when token expired
        when(service.isLinked(PLAYER_UUID)).thenReturn(false);
        when(service.createAuthorizationUri(PLAYER_UUID, PLAYER_NAME))
                .thenReturn(URI.create("https://connect.linux.do/oauth2/authorize?state=xyz"));

        boolean result = linkCommand.onCommand(player, command,
                "linkld", new String[0]);

        assertTrue(result);
        verify(player, atLeastOnce()).sendMessage(anyString());
    }

    // ═══════════════════════════════════════════════════════════
    // /linkld <code> — complete link
    // ═══════════════════════════════════════════════════════════

    @Test
    void onCommand_WithCode_ShouldAttemptLink() {
        Instant linkedAt = Instant.parse("2026-06-01T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-06-01T14:00:00Z");
        LinkedAccount account = new LinkedAccount(PLAYER_UUID, PLAYER_NAME,
                "ld-42", "linuxuser", linkedAt, expiresAt);

        when(service.linkPlayer(PLAYER_UUID, PLAYER_NAME, "ABCD1234"))
                .thenReturn(CompletableFuture.completedFuture(account));

        boolean result = linkCommand.onCommand(player, command,
                "linkld", new String[]{"ABCD1234"});

        assertTrue(result);
        verify(player).sendMessage(contains("正在验证"));
    }

    // ═══════════════════════════════════════════════════════════
    // /linkld unlink — confirmation prompt
    // ═══════════════════════════════════════════════════════════

    @Test
    void onCommand_Unlink_NotLinked_ShouldShowError() {
        when(service.isLinked(PLAYER_UUID)).thenReturn(false);

        boolean result = linkCommand.onCommand(player, command,
                "linkld", new String[]{"unlink"});

        assertTrue(result);
        verify(player).sendMessage(contains("还没有绑定"));
    }

    @Test
    void onCommand_Unlink_Linked_ShouldShowConfirmation() {
        when(service.isLinked(PLAYER_UUID)).thenReturn(true);

        boolean result = linkCommand.onCommand(player, command,
                "linkld", new String[]{"unlink"});

        assertTrue(result);
        verify(player).sendMessage(contains("确认解除绑定"));
        verify(player).spigot();  // confirm + cancel buttons
    }

    // ═══════════════════════════════════════════════════════════
    // /linkld unlink confirm — execute unlink
    // ═══════════════════════════════════════════════════════════

    @Test
    void onCommand_UnlinkConfirm_Linked_ShouldExecuteUnlink() {
        Instant linkedAt = Instant.parse("2026-06-01T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-06-01T14:00:00Z");
        LinkedAccount account = new LinkedAccount(PLAYER_UUID, PLAYER_NAME,
                "ld-42", "linuxuser", linkedAt, expiresAt);

        when(service.isLinked(PLAYER_UUID)).thenReturn(true);
        when(service.getLinkedAccount(PLAYER_UUID)).thenReturn(Optional.of(account));
        when(service.unlink(PLAYER_UUID)).thenReturn(CompletableFuture.completedFuture(null));

        boolean result = linkCommand.onCommand(player, command,
                "linkld", new String[]{"unlink", "confirm"});

        assertTrue(result);
        verify(service).unlink(PLAYER_UUID);
    }

    @Test
    void onCommand_UnlinkConfirm_NotLinked_ShouldShowError() {
        when(service.isLinked(PLAYER_UUID)).thenReturn(false);

        boolean result = linkCommand.onCommand(player, command,
                "linkld", new String[]{"unlink", "confirm"});

        assertTrue(result);
        verify(player).sendMessage(contains("还没有绑定"));
        verify(service, never()).unlink(any());
    }

    // ═══════════════════════════════════════════════════════════
    // Tab completion
    // ═══════════════════════════════════════════════════════════

    @Test
    void onTabComplete_NoArgs_ShouldSuggestUnlink() {
        List<String> suggestions = linkCommand.onTabComplete(player, command,
                "linkld", new String[0]);

        assertEquals(1, suggestions.size());
        assertEquals("unlink", suggestions.get(0));
    }

    @Test
    void onTabComplete_UnlinkArg_ShouldSuggestUnlink() {
        List<String> suggestions = linkCommand.onTabComplete(player, command,
                "linkld", new String[]{"unlink"});

        assertEquals(1, suggestions.size());
        assertEquals("unlink", suggestions.get(0),
                "Fully typed 'unlink' should suggest 'unlink', not jump to second argument");
    }

    @Test
    void onTabComplete_AfterConfirm_ShouldReturnEmpty() {
        List<String> suggestions = linkCommand.onTabComplete(player, command,
                "linkld", new String[]{"unlink", "confirm"});

        assertTrue(suggestions.isEmpty());
    }

    @Test
    void onTabComplete_OtherFirstArg_ShouldReturnEmpty() {
        List<String> suggestions = linkCommand.onTabComplete(player, command,
                "linkld", new String[]{"somethingelse"});

        assertTrue(suggestions.isEmpty());
    }
}
