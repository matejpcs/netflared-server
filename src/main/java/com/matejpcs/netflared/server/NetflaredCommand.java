package com.matejpcs.netflared.server;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class NetflaredCommand implements CommandExecutor {
    private final NetflaredServerPlugin plugin;

    public NetflaredCommand(NetflaredServerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("netflared.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to manage Netflared.");
            return true;
        }

        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "status" -> plugin.status(sender);
            case "info" -> plugin.info(sender);
            case "connect", "start" -> plugin.connect(sender);
            case "disconnect", "stop" -> plugin.disconnect(sender);
            case "reload" -> plugin.reload(sender);
            case "setup" -> setup(sender, args);
            case "reset" -> plugin.reset(sender);
            default -> help(sender);
        }
        return true;
    }

    private void setup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage:");
            sender.sendMessage(ChatColor.GRAY + "/netflared setup <cloudflare-api-token>");
            sender.sendMessage(ChatColor.GRAY + "/netflared setup select <zone-number> [subdomain]");
            sender.sendMessage(ChatColor.GRAY + "The API token is kept in memory only.");
            return;
        }

        if (args[1].equalsIgnoreCase("select")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Choose a zone number.");
                return;
            }
            int index;
            try {
                index = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Zone number must be a number.");
                return;
            }
            plugin.selectZone(sender, index, args.length >= 4 ? args[3] : "play");
            return;
        }

        plugin.beginSetup(sender, args[1]);
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Netflared Server");
        sender.sendMessage(ChatColor.GRAY + "/netflared status - show tunnel state");
        sender.sendMessage(ChatColor.GRAY + "/netflared info - show configuration");
        sender.sendMessage(ChatColor.GRAY + "/netflared setup <token> - begin setup");
        sender.sendMessage(ChatColor.GRAY + "/netflared setup select <number> [subdomain] - finish setup");
        sender.sendMessage(ChatColor.GRAY + "/netflared connect - start cloudflared");
        sender.sendMessage(ChatColor.GRAY + "/netflared disconnect - stop cloudflared");
        sender.sendMessage(ChatColor.GRAY + "/netflared reload - reload and reconnect");
        sender.sendMessage(ChatColor.GRAY + "/netflared reset - remove local configuration");
    }
}
