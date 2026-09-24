package com.matejpcs.netflared.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CloudflaredManager {
    private static final String RELEASES = "https://api.github.com/repos/cloudflare/cloudflared/releases/latest";
    private final Path binary;
    private final Path tokenFile;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private Process process;
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    public CloudflaredManager(Path dataFolder) {
        this.binary = dataFolder.resolve("bin").resolve(Platform.executableName());
        this.tokenFile = dataFolder.resolve("tunnel.token");
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    public synchronized Path ensureBinary() throws IOException, InterruptedException {
        if (Files.isRegularFile(binary) && Files.isExecutable(binary)) return binary;
        if (!Platform.supported()) throw new IOException("Unsupported platform: " + Platform.id());

        String assetName = assetName();
        Files.createDirectories(binary.getParent());

        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Netflared-Server")
                .timeout(Duration.ofSeconds(30))
                .GET().build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Could not query the latest cloudflared release (HTTP " + response.statusCode() + ")");
        }

        JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
        String downloadUrl = null;
        JsonArray assets = release.getAsJsonArray("assets");
        for (int i = 0; i < assets.size(); i++) {
            JsonObject asset = assets.get(i).getAsJsonObject();
            if (assetName.equals(asset.get("name").getAsString())) {
                downloadUrl = asset.get("browser_download_url").getAsString();
                break;
            }
        }
        if (downloadUrl == null) throw new IOException("No cloudflared binary is published for " + Platform.id());

        Path temp = binary.resolveSibling(binary.getFileName() + ".download");
        HttpRequest download = HttpRequest.newBuilder(URI.create(downloadUrl))
                .header("User-Agent", "Netflared-Server")
                .timeout(Duration.ofMinutes(5))
                .GET().build();

        HttpResponse<InputStream> result = http.send(download, HttpResponse.BodyHandlers.ofInputStream());
        if (result.statusCode() != 200) throw new IOException("Could not download cloudflared (HTTP " + result.statusCode() + ")");

        try (InputStream in = result.body()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(temp, binary, StandardCopyOption.REPLACE_EXISTING);

        if (!Platform.id().startsWith("windows")) {
            try {
                Set<PosixFilePermission> permissions = new HashSet<>(Files.getPosixFilePermissions(binary));
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
                permissions.add(PosixFilePermission.GROUP_EXECUTE);
                permissions.add(PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(binary, permissions);
            } catch (UnsupportedOperationException ignored) {}
        }
        return binary;
    }

    public synchronized void start(String token) throws IOException {
        if (isRunning()) return;
        stopping.set(false);
        Files.createDirectories(tokenFile.getParent());
        Files.writeString(tokenFile, token, java.nio.charset.StandardCharsets.UTF_8);
        if (!Platform.id().startsWith("windows")) {
            try {
                Files.setPosixFilePermissions(tokenFile, Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {}
        }
        process = new ProcessBuilder(binary.toString(), "tunnel", "run", "--token-file", tokenFile.toString())
                .redirectErrorStream(true)
                .start();

        Process current = process;
        Thread.ofVirtual().name("netflared-cloudflared-monitor").start(() -> monitor(current, token));
    }

    private void monitor(Process current, String token) {
        try {
            current.getInputStream().transferTo(System.out);
            int exit = current.waitFor();
            if (!stopping.get()) {
                System.err.println("[Netflared] cloudflared exited with code " + exit + "; reconnecting in 5 seconds.");
                Thread.sleep(5000);
                synchronized (this) {
                    if (!stopping.get() && process == current) {
                        process = null;
                        start(token);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (!stopping.get()) System.err.println("[Netflared] cloudflared output monitor failed: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        stopping.set(true);
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            process = null;
        }
    }

    public Path binary() { return binary; }

    public void clearTokenFile() throws IOException {
        Files.deleteIfExists(tokenFile);
    }

    private static String assetName() {
        return switch (Platform.id()) {
            case "linux-amd64" -> "cloudflared-linux-amd64";
            case "linux-arm64" -> "cloudflared-linux-arm64";
            case "linux-arm" -> "cloudflared-linux-arm";
            case "linux-386" -> "cloudflared-linux-386";
            case "windows-amd64" -> "cloudflared-windows-amd64.exe";
            case "windows-arm64" -> "cloudflared-windows-arm64.exe";
            case "windows-386" -> "cloudflared-windows-386.exe";
            default -> throw new IllegalStateException("Unsupported platform: " + Platform.id());
        };
    }
}
