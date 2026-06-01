package org.OAuth_Framework.oAuth_Framework.command;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.OAuth_Framework.oAuth_Framework.model.LinkedAccount;
import org.OAuth_Framework.oAuth_Framework.oauth.OAuthException;
import org.OAuth_Framework.oAuth_Framework.service.OAuthFrameworkService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * /linkLD command — initiates, completes, or manages LinuxDO OAuth linking.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/linkLD — start OAuth flow, or show profile if already linked</li>
 *   <li>/linkLD &lt;code&gt; — complete OAuth flow with link code</li>
 *   <li>/linkLD unlink — show unlink confirmation</li>
 *   <li>/linkLD unlink confirm — execute unlink</li>
 * </ul>
 */
public class LinkCommand implements CommandExecutor, TabCompleter {

    private final OAuthFrameworkService service;
    private final Logger logger;

    public LinkCommand(OAuthFrameworkService service, Logger logger) {
        this.service = service;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行");
            return true;
        }

        if (args.length == 0) {
            // /linkLD — start OAuth flow, or show profile if already linked
            return handleProfileOrStart(player);
        }

        switch (args[0].toLowerCase()) {
            case "unlink":
                return handleUnlinkRequest(player, args);
            default:
                // /linkLD <code> — complete OAuth flow
                return handleCompleteLink(player, args[0]);
        }
    }

    // ===== Profile / Start =====

    private boolean handleProfileOrStart(Player player) {
        if (service.isLinked(player.getUniqueId())) {
            return showProfile(player);
        }
        return startOAuthFlow(player);
    }

    private boolean showProfile(Player player) {
        service.getLinkedAccount(player.getUniqueId()).ifPresentOrElse(account -> {
            String username = account.linuxDoUsername();
            String displayName = account.linuxDoDisplayName();
            String nameLine = username.equals(displayName)
                    ? username
                    : displayName + " (@" + username + ")";

            // ── Header ──
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "⚡ " + ChatColor.BOLD + "Linux.DO 账号信息"
                    + ChatColor.GRAY + " ──────────────────");

            // ── Profile fields ──
            sendInfoLine(player, "👤 用户", nameLine);
            sendInfoLine(player, "⭐ 信任等级",
                    ChatColor.AQUA + account.getTrustLevelLabel());
            sendInfoLine(player, "❤ 社区分数",
                    ChatColor.LIGHT_PURPLE + account.getLikesReceivedLabel());

            // ── Clickable profile link ──
            ComponentBuilder linkBuilder = new ComponentBuilder()
                    .append(ChatColor.GRAY + "🌐 论坛主页: ")
                    .append(new ComponentBuilder("[点击访问]")
                            .color(net.md_5.bungee.api.ChatColor.AQUA)
                            .bold(true)
                            .event(new ClickEvent(ClickEvent.Action.OPEN_URL, account.getProfileUrl()))
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    new Text("在浏览器中打开 " + account.getProfileUrl())))
                            .create());
            player.spigot().sendMessage(linkBuilder.create());

            // ── Footer ──
            player.sendMessage(ChatColor.GRAY + "──────────────────────────────────");

            // ── Unlink button ──
            ComponentBuilder unlinkBuilder = new ComponentBuilder()
                    .append(new ComponentBuilder("[退出登录]")
                            .color(net.md_5.bungee.api.ChatColor.RED)
                            .bold(true)
                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/linkLD unlink"))
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    new Text("解除当前 Minecraft 账号与 Linux.DO 的绑定")))
                            .create())
                    .append(ChatColor.GRAY + " ← 点击解除绑定");

            player.spigot().sendMessage(unlinkBuilder.create());
            player.sendMessage("");
        }, () -> {
            // Edge case: was linked but token expired — restart flow
            startOAuthFlow(player);
        });
        return true;
    }

    private void sendInfoLine(Player player, String label, String value) {
        player.sendMessage(ChatColor.GRAY + label + ": " + ChatColor.WHITE + value);
    }

    private boolean startOAuthFlow(Player player) {
        URI authUri = service.createAuthorizationUri(player.getUniqueId(), player.getName());

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "⚡ " + ChatColor.BOLD + "Linux.DO 账号绑定"
                + ChatColor.GRAY + " ──────────────────");

        // Send clickable JSON chat message
        ComponentBuilder builder = new ComponentBuilder()
                .append(ChatColor.GRAY + "点击 ")
                .append(new ComponentBuilder("[此处]")
                        .color(net.md_5.bungee.api.ChatColor.AQUA)
                        .bold(true)
                        .event(new ClickEvent(ClickEvent.Action.OPEN_URL, authUri.toString()))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new Text("打开 LinuxDO 授权页面")))
                        .create())
                .append(ChatColor.GRAY + " 验证你的 LinuxDO 账号");

        player.spigot().sendMessage(builder.create());
        player.sendMessage(ChatColor.GRAY + "授权完成后，请使用 " + ChatColor.AQUA + "/linkLD <验证码>"
                + ChatColor.GRAY + " 完成绑定");
        player.sendMessage("");
        return true;
    }

    // ===== Complete Link =====

    private boolean handleCompleteLink(Player player, String code) {
        player.sendMessage(ChatColor.GRAY + "正在验证...");

        service.linkPlayer(player.getUniqueId(), player.getName(), code)
                .thenAccept(account -> {
                    player.sendMessage(ChatColor.GREEN + "✔ 成功绑定 LinuxDO 账号: @"
                            + ChatColor.WHITE + account.linuxDoUsername());
                    player.sendMessage(ChatColor.GRAY + "使用 " + ChatColor.AQUA + "/linkLD"
                            + ChatColor.GRAY + " 查看账号信息");
                })
                .exceptionally(throwable -> {
                    String message = ChatColor.RED + "❌ 绑定失败: ";
                    if (throwable.getCause() instanceof OAuthException oa) {
                        message += oa.getSafeMessage();
                    } else if (throwable instanceof OAuthException oa) {
                        message += oa.getSafeMessage();
                    } else {
                        message += "未知错误，请稍后重试";
                        logger.log(Level.WARNING, "绑定失败", throwable);
                    }
                    String finalMessage = message;
                    if (Bukkit.isPrimaryThread()) {
                        player.sendMessage(finalMessage);
                    } else {
                        Bukkit.getScheduler().runTask(
                                Bukkit.getPluginManager().getPlugin("OAuth_Framework"),
                                () -> player.sendMessage(finalMessage));
                    }
                    return null;
                });
        return true;
    }

    // ===== Unlink =====

    private boolean handleUnlinkRequest(Player player, String[] args) {
        if (args.length >= 2 && "confirm".equalsIgnoreCase(args[1])) {
            return executeUnlink(player);
        }

        // Show confirmation prompt
        if (!service.isLinked(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "你还没有绑定 LinuxDO 账号");
            return true;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.BOLD + "确认解除绑定？");
        player.sendMessage(ChatColor.GRAY + "解除后你将失去与此 LinuxDO 账号相关的所有权限和称号");

        // Confirm button
        ComponentBuilder confirmBuilder = new ComponentBuilder()
                .append(new ComponentBuilder("[确认解除]")
                        .color(net.md_5.bungee.api.ChatColor.RED)
                        .bold(true)
                        .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/linkLD unlink confirm"))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new Text("永久解除 Minecraft 与 Linux.DO 账号的绑定")))
                        .create());

        // Cancel button
        ComponentBuilder cancelBuilder = new ComponentBuilder()
                .append("    ")
                .append(new ComponentBuilder("[取消操作]")
                        .color(net.md_5.bungee.api.ChatColor.GREEN)
                        .bold(true)
                        .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/linkLD"))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new Text("返回账号信息页面")))
                        .create());

        player.spigot().sendMessage(confirmBuilder.append(cancelBuilder.create()).create());
        player.sendMessage("");
        return true;
    }

    private boolean executeUnlink(Player player) {
        if (!service.isLinked(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "你还没有绑定 LinuxDO 账号");
            return true;
        }

        service.getLinkedAccount(player.getUniqueId()).ifPresent(account -> {
            service.unlink(player.getUniqueId())
                    .thenRun(() -> {
                        Bukkit.getScheduler().runTask(
                                Bukkit.getPluginManager().getPlugin("OAuth_Framework"),
                                () -> {
                                    player.sendMessage(ChatColor.GREEN + "✔ 已解除与 LinuxDO 账号 @"
                                            + account.linuxDoUsername() + " 的绑定");
                                    player.sendMessage(ChatColor.GRAY + "你可以随时使用 "
                                            + ChatColor.AQUA + "/linkLD" + ChatColor.GRAY
                                            + " 重新绑定");
                                });
                    })
                    .exceptionally(throwable -> {
                        logger.log(Level.WARNING, "解除绑定时发生错误", throwable);
                        Bukkit.getScheduler().runTask(
                                Bukkit.getPluginManager().getPlugin("OAuth_Framework"),
                                () -> player.sendMessage(ChatColor.RED + "❌ 解除绑定失败，请稍后重试"));
                        return null;
                    });
        });
        return true;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("unlink");
        }
        if (args.length == 2 && "unlink".equalsIgnoreCase(args[0])) {
            return List.of("confirm");
        }
        return Collections.emptyList();
    }
}
