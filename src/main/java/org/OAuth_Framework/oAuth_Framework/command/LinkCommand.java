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
 * /link command — initiates or completes LinuxDO OAuth linking.
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
            // /link — start OAuth flow
            return handleStartLink(player);
        } else {
            // /link <code> — complete OAuth flow
            return handleCompleteLink(player, args[0]);
        }
    }

    private boolean handleStartLink(Player player) {
        if (service.isLinked(player.getUniqueId())) {
            service.getLinkedAccount(player.getUniqueId()).ifPresent(account ->
                    player.sendMessage(ChatColor.GREEN + "✔ 已绑定 LinuxDO 账号: @"
                            + account.linuxDoUsername()));
            return true;
        }

        URI authUri = service.createAuthorizationUri(player.getUniqueId());

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
        player.sendMessage(ChatColor.GRAY + "授权完成后，请使用 " + ChatColor.AQUA + "/link <验证码>"
                + ChatColor.GRAY + " 完成绑定");
        return true;
    }

    private boolean handleCompleteLink(Player player, String code) {
        player.sendMessage(ChatColor.GRAY + "正在验证...");

        service.linkPlayer(player.getUniqueId(), player.getName(), code)
                .thenAccept(account -> {
                    // Already runs on main thread via Bukkit scheduler
                    player.sendMessage(ChatColor.GREEN + "✔ 成功绑定 LinuxDO 账号: @"
                            + ChatColor.WHITE + account.linuxDoUsername());
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
                    // Ensure we're on the main thread for messaging
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

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
