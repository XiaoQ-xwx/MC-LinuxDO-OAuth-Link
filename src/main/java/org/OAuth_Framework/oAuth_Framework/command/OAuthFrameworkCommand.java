package org.OAuth_Framework.oAuth_Framework.command;

import org.OAuth_Framework.oAuth_Framework.config.OAuthConfig;
import org.OAuth_Framework.oAuth_Framework.service.OAuthFrameworkService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * /oauthframework (alias: /oauthfw) — administrative commands.
 */
public class OAuthFrameworkCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final OAuthFrameworkService service;
    private final Logger logger;

    public OAuthFrameworkCommand(JavaPlugin plugin, OAuthFrameworkService service, Logger logger) {
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
        if (!sender.hasPermission("oauth_framework.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此操作");
            return true;
        }

        try {
            OAuthConfig config = OAuthConfig.load(plugin);
            sender.sendMessage(ChatColor.GREEN + "✔ 配置已重新加载");
            logger.info("配置已由 " + sender.getName() + " 重新加载");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "✘ 配置重新加载失败: " + e.getMessage());
            logger.log(Level.WARNING, "配置重新加载失败", e);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== OAuth Framework 管理 ===");
        sender.sendMessage(ChatColor.GRAY + "/oauthfw reload " + ChatColor.WHITE + "- 重新加载配置");
    }
}
