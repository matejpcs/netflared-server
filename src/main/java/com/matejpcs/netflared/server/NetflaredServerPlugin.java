package com.matejpcs.netflared.server;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class NetflaredServerPlugin extends JavaPlugin {
    private ServerConfig config;
    private CloudflaredManager cloudflared;
    private volatile CloudflareClient setupClient;
    private volatile List<CloudflareClient.Zone> setupZones = List.of();

    @Override
    public void onEnable() {
        try {
            config = new ServerConfig(getDataFolder().toPath());
            config.load();
        } catch (IOException e) {
            getLogger().severe("Could not load configuration: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        cloudflared = new CloudflaredManager(getDataFolder().toPath());
        getCommand("netflared").setExecutor(new NetflaredCommand(this));

        if (!config.isConfigured()) {
            getLogger().info("Netflared is not configured. Run /netflared setup <cloudflare-api-token>.");
            return;
        }

        getLogger().info("Configured for " + config.hostname() + " -> 127.0.0.1:" + config.serverPort());
        connect(null);
    }

    @Override
    public void onDisable() {
        if (cloudflared != null) cloudflared.stop();
        setupClient = null;
        setupZones = List.of();
    }

    public void beginSetup(CommandSender sender, String token) {
        if (token.length() < 20) {
            sender.sendMessage(ChatColor.RED + "That does not look like a Cloudflare API token.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Checking Cloudflare API token...");
        CompletableFuture.runAsync(() -> {
            try {
                CloudflareClient client = new CloudflareClient(token);
                client.verify();
                List<CloudflareClient.Zone> zones = client.listZones();
                if (zones.isEmpty()) throw new IOException("No Cloudflare zones are visible to this token.");
                setupClient = client;
                setupZones = zones;

                Bukkit.getScheduler().runTask(this, () -> {
                    sender.sendMessage(ChatColor.GREEN + "Cloudflare token accepted. Choose a domain:");
                    for (int i = 0; i < zones.size(); i++) {
                        CloudflareClient.Zone z = zones.get(i);
                        sender.sendMessage(ChatColor.YELLOW + "" + (i + 1) + ChatColor.GRAY + ". " + z.name()
                                + (z.accountName().isBlank() ? "" : " (" + z.accountName() + ")"));
                    }
                    sender.sendMessage(ChatColor.GRAY + "Run: /netflared setup select <number> [subdomain]");
                    sender.sendMessage(ChatColor.GRAY + "Default subdomain: play");
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(this, () ->
                        sender.sendMessage(ChatColor.RED + "Setup failed: " + friendly(e)));
            }
        });
    }

    public void selectZone(CommandSender sender, int index, String subdomain) {
        CloudflareClient client = setupClient;
        List<CloudflareClient.Zone> zones = setupZones;
        if (client == null || zones.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No setup session is active. Run /netflared setup <token> first.");
            return;
        }
        if (index < 1 || index > zones.size()) {
            sender.sendMessage(ChatColor.RED + "Invalid zone number.");
            return;
        }

        CloudflareClient.Zone zone = zones.get(index - 1);
        final String hostname;
        try {
            hostname = Hostname.build(subdomain, zone.name());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
            return;
        }

        int port = Bukkit.getPort();
        String tunnelName = "netflared-" + Bukkit.getServer().getName().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        tunnelName = tunnelName.substring(0, Math.min(48, tunnelName.length()));

        sender.sendMessage(ChatColor.YELLOW + "Creating Cloudflare Tunnel for " + hostname + "...");
        CompletableFuture.runAsync(() -> {
            try {
                CloudflareClient.Tunnel tunnel = client.createTunnel(zone.accountId(), tunnelName);
                String token = client.getTunnelToken(zone.accountId(), tunnel.id());
                client.updateConfiguration(zone.accountId(), tunnel.id(), hostname, port);
                client.upsertDns(zone.id(), hostname, tunnel.id() + ".cfargotunnel.com");

                config.setEnabled(true);
                config.setAccountId(zone.accountId());
                config.setZoneId(zone.id());
                config.setZoneName(zone.name());
                config.setTunnelId(tunnel.id());
                config.setTunnelName(tunnel.name());
                config.setTunnelToken(token);
                config.setHostname(hostname);
                config.setServerPort(port);
                config.save();

                setupClient = null;
                setupZones = List.of();

                Bukkit.getScheduler().runTask(this, () -> {
                    sender.sendMessage(ChatColor.GREEN + "Netflared setup complete.");
                    sender.sendMessage(ChatColor.GOLD + "Public address: " + ChatColor.WHITE + hostname);
                    sender.sendMessage(ChatColor.GRAY + "Start/connect from a Netflared client mod.");
                    connect(sender);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(this, () ->
                        sender.sendMessage(ChatColor.RED + "Setup failed: " + friendly(e)));
            }
        });
    }

    public void connect(CommandSender sender) {
        if (!config.isConfigured()) {
            if (sender != null) sender.sendMessage(ChatColor.RED + "Netflared is not configured.");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                cloudflared.ensureBinary();
                cloudflared.start(config.tunnelToken());
                Bukkit.getScheduler().runTask(this, () -> {
                    if (sender != null) sender.sendMessage(ChatColor.GREEN + "Cloudflared started.");
                    getLogger().info("Tunnel process started for " + config.hostname());
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(this, () -> {
                    if (sender != null) sender.sendMessage(ChatColor.RED + "Could not start tunnel: " + friendly(e));
                    getLogger().severe("Could not start tunnel: " + friendly(e));
                });
            }
        });
    }

    public void disconnect(CommandSender sender) {
        cloudflared.stop();
        if (sender != null) sender.sendMessage(ChatColor.YELLOW + "Cloudflared stopped.");
    }

    public void status(CommandSender sender) {
        if (!config.isConfigured()) {
            sender.sendMessage(ChatColor.YELLOW + "Netflared is not configured.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Netflared status");
        sender.sendMessage(ChatColor.GRAY + "Address: " + ChatColor.WHITE + config.hostname());
        sender.sendMessage(ChatColor.GRAY + "Local: " + ChatColor.WHITE + "127.0.0.1:" + config.serverPort());
        sender.sendMessage(ChatColor.GRAY + "Tunnel: " + (cloudflared.isRunning() ? ChatColor.GREEN + "CONNECTED" : ChatColor.RED + "STOPPED"));
        sender.sendMessage(ChatColor.GRAY + "Tunnel ID: " + ChatColor.WHITE + config.tunnelId());
    }

    public void info(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Netflared configuration");
        sender.sendMessage(ChatColor.GRAY + "Public hostname: " + ChatColor.WHITE + (config.hostname().isBlank() ? "not configured" : config.hostname()));
        sender.sendMessage(ChatColor.GRAY + "Minecraft port: " + ChatColor.WHITE + config.serverPort());
        sender.sendMessage(ChatColor.GRAY + "Cloudflare zone: " + ChatColor.WHITE + config.zoneName());
        sender.sendMessage(ChatColor.GRAY + "Tunnel: " + ChatColor.WHITE + config.tunnelName());
        sender.sendMessage(ChatColor.GRAY + "Platform: " + ChatColor.WHITE + Platform.id());
        sender.sendMessage(ChatColor.GRAY + "cloudflared: " + ChatColor.WHITE + cloudflared.binary());
    }

    public void reload(CommandSender sender) {
        try {
            config.load();
            cloudflared.stop();
            if (config.isConfigured()) connect(sender);
            else sender.sendMessage(ChatColor.YELLOW + "Configuration reloaded; Netflared is not configured.");
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Reload failed: " + e.getMessage());
        }
    }

    public void reset(CommandSender sender) {
        disconnect(null);
        if (!config.isConfigured()) {
            sender.sendMessage(ChatColor.YELLOW + "Netflared is already unconfigured.");
            return;
        }
        try {
            config.setEnabled(false);
            config.setTunnelToken("");
            config.setTunnelId("");
            config.setTunnelName("");
            config.setHostname("");
            config.save();
            sender.sendMessage(ChatColor.GREEN + "Local configuration reset. Cloudflare resources were left untouched.");
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Could not save reset configuration: " + e.getMessage());
        }
    }

    private static String friendly(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
