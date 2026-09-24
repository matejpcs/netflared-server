package com.matejpcs.netflared.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private boolean enabled;
    private String accountId = "";
    private String zoneId = "";
    private String zoneName = "";
    private String tunnelId = "";
    private String tunnelName = "";
    private String tunnelToken = "";
    private String hostname = "";
    private int serverPort = 25565;

    public ServerConfig(Path dataFolder) {
        this.file = dataFolder.resolve("config.json");
    }

    public void load() throws IOException {
        if (!Files.exists(file)) {
            save();
            return;
        }
        JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
        if (root == null) return;
        enabled = bool(root, "enabled", false);
        accountId = str(root, "accountId");
        zoneId = str(root, "zoneId");
        zoneName = str(root, "zoneName");
        tunnelId = str(root, "tunnelId");
        tunnelName = str(root, "tunnelName");
        tunnelToken = str(root, "tunnelToken");
        hostname = str(root, "hostname");
        serverPort = root.has("serverPort") ? root.get("serverPort").getAsInt() : 25565;
    }

    public synchronized void save() throws IOException {
        Files.createDirectories(file.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        root.addProperty("accountId", accountId);
        root.addProperty("zoneId", zoneId);
        root.addProperty("zoneName", zoneName);
        root.addProperty("tunnelId", tunnelId);
        root.addProperty("tunnelName", tunnelName);
        root.addProperty("tunnelToken", tunnelToken);
        root.addProperty("hostname", hostname);
        root.addProperty("serverPort", serverPort);
        Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }

    public boolean isConfigured() { return enabled && !tunnelId.isBlank() && !tunnelToken.isBlank(); }
    public void setEnabled(boolean value) { enabled = value; }
    public String accountId() { return accountId; }
    public void setAccountId(String value) { accountId = value; }
    public String zoneId() { return zoneId; }
    public void setZoneId(String value) { zoneId = value; }
    public String zoneName() { return zoneName; }
    public void setZoneName(String value) { zoneName = value; }
    public String tunnelId() { return tunnelId; }
    public void setTunnelId(String value) { tunnelId = value; }
    public String tunnelName() { return tunnelName; }
    public void setTunnelName(String value) { tunnelName = value; }
    public String tunnelToken() { return tunnelToken; }
    public void setTunnelToken(String value) { tunnelToken = value; }
    public String hostname() { return hostname; }
    public void setHostname(String value) { hostname = value; }
    public int serverPort() { return serverPort; }
    public void setServerPort(int value) { serverPort = value; }
}
