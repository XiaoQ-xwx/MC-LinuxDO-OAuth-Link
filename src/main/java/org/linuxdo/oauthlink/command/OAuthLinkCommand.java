package org.linuxdo.oauthlink.command;

import org.linuxdo.oauthlink.OAuthLink;
import org.linuxdo.oauthlink.service.OAuthLinkService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * /oauthlink (alias: /olink) — administrative commands.
 */
public class OAuthLinkCommand implements CommandExecutor {

    private final OAuthLink plugin;
    private final OAuthLinkService service;
    private final Logger logger;

    public OAuthLinkCommand(OAuthLink plugin, OAuthLinkService service, Logger logger) {
        this.plugin = plugin;
        this.service = service;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            return handleReload(sender);
        }

        sendUsage(sender);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("oauthlink.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此操作");
            return true;
        }

        try {
            plugin.reloadOAuthConfig();
            sender.sendMessage(ChatColor.GREEN + "✔ 配置已重新加载");
            logger.info("配置已由 " + sender.getName() + " 重新加载");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "✘ 配置重新加载失败: " + e.getMessage());
            logger.log(Level.WARNING, "配置重新加载失败", e);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== OAuthLink 管理 ===");
        sender.sendMessage(ChatColor.GRAY + "/oauthlink reload " + ChatColor.WHITE + "- 重新加载配置");
    }
}
