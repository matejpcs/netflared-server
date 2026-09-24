package com.matejpcs.netflared.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class CloudflareClient {
    private static final String API = "https://api.cloudflare.com/client/v4";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String token;

    public CloudflareClient(String token) {
        this.token = token;
    }

    public record Zone(String id, String name, String accountId, String accountName) {}
    public record Tunnel(String id, String name) {}
    public record DnsRecord(String id, String name, String type, String content) {}

    public void verify() throws IOException, InterruptedException {
        request("GET", "/user/tokens/verify", null, 200);
    }

    public List<Zone> listZones() throws IOException, InterruptedException {
        JsonObject response = request("GET", "/zones?per_page=100", null, 200);
        List<Zone> zones = new ArrayList<>();
        for (JsonElement element : response.getAsJsonArray("result")) {
            JsonObject z = element.getAsJsonObject();
            JsonObject account = z.has("account") && z.get("account").isJsonObject()
                    ? z.getAsJsonObject("account") : null;
            zones.add(new Zone(
                    z.get("id").getAsString(),
                    z.get("name").getAsString(),
                    account == null || !account.has("id") ? "" : account.get("id").getAsString(),
                    account == null || !account.has("name") ? "" : account.get("name").getAsString()
            ));
        }
        return zones;
    }

    public Tunnel createTunnel(String accountId, String name) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("config_src", "cloudflare");
        JsonObject result = request("POST", "/accounts/" + accountId + "/cfd_tunnel", body, 200).getAsJsonObject("result");
        return new Tunnel(result.get("id").getAsString(), result.get("name").getAsString());
    }

    public String getTunnelToken(String accountId, String tunnelId) throws IOException, InterruptedException {
        return request("GET", "/accounts/" + accountId + "/cfd_tunnel/" + tunnelId + "/token", null, 200)
                .get("result").getAsString();
    }

    public void updateConfiguration(String accountId, String tunnelId, String hostname, int port)
            throws IOException, InterruptedException {
        JsonArray ingress = new JsonArray();

        JsonObject route = new JsonObject();
        route.addProperty("hostname", hostname);
        route.addProperty("service", "tcp://localhost:" + port);
        ingress.add(route);

        JsonObject fallback = new JsonObject();
        fallback.addProperty("service", "http_status:404");
        ingress.add(fallback);

        JsonObject config = new JsonObject();
        config.add("ingress", ingress);
        JsonObject body = new JsonObject();
        body.add("config", config);

        request("PUT", "/accounts/" + accountId + "/cfd_tunnel/" + tunnelId + "/configurations", body, 200);
    }

    public DnsRecord findDnsRecord(String zoneId, String hostname) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(hostname, StandardCharsets.UTF_8);
        JsonObject response = request("GET", "/zones/" + zoneId + "/dns_records?type=CNAME&name=" + encoded + "&per_page=100", null, 200);
        JsonArray results = response.getAsJsonArray("result");
        if (results.isEmpty()) return null;
        JsonObject record = results.get(0).getAsJsonObject();
        return new DnsRecord(
                record.get("id").getAsString(),
                record.get("name").getAsString(),
                record.get("type").getAsString(),
                record.get("content").getAsString()
        );
    }

    public void upsertDns(String zoneId, String hostname, String target) throws IOException, InterruptedException {
        DnsRecord existing = findDnsRecord(zoneId, hostname);
        JsonObject body = new JsonObject();
        body.addProperty("type", "CNAME");
        body.addProperty("name", hostname);
        body.addProperty("content", target);
        body.addProperty("ttl", 1);
        body.addProperty("proxied", true);

        if (existing == null) {
            request("POST", "/zones/" + zoneId + "/dns_records", body, 200);
        } else {
            request("PUT", "/zones/" + zoneId + "/dns_records/" + existing.id(), body, 200);
        }
    }

    public void deleteDns(String zoneId, String hostname) throws IOException, InterruptedException {
        DnsRecord existing = findDnsRecord(zoneId, hostname);
        if (existing != null) {
            request("DELETE", "/zones/" + zoneId + "/dns_records/" + existing.id(), null, 200);
        }
    }

    public void deleteTunnel(String accountId, String tunnelId) throws IOException, InterruptedException {
        request("DELETE", "/accounts/" + accountId + "/cfd_tunnel/" + tunnelId, null, 200);
    }

    private JsonObject request(String method, String path, JsonObject body, int expectedStatus)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(API + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");

        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body.toString()));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonObject json;
        try {
            json = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (Exception ex) {
            throw new IOException("Cloudflare returned invalid JSON (HTTP " + response.statusCode() + ")");
        }

        if (response.statusCode() != expectedStatus || !json.has("success") || !json.get("success").getAsBoolean()) {
            String message = "Cloudflare API request failed (HTTP " + response.statusCode() + ")";
            if (json.has("errors") && json.get("errors").isJsonArray() && !json.getAsJsonArray("errors").isEmpty()) {
                JsonObject error = json.getAsJsonArray("errors").get(0).getAsJsonObject();
                if (error.has("message")) message += ": " + error.get("message").getAsString();
            }
            throw new IOException(message);
        }
        return json;
    }
}
