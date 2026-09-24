package com.matejpcs.netflared.server;

import java.util.Locale;

public final class Platform {
    private Platform() {}

    public static String id() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        String osId;
        if (os.contains("win")) osId = "windows";
        else if (os.contains("linux")) osId = "linux";
        else if (os.contains("mac") || os.contains("darwin")) osId = "macos";
        else osId = "unsupported";

        String archId;
        if (arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64")) archId = "amd64";
        else if (arch.equals("aarch64") || arch.equals("arm64")) archId = "arm64";
        else if (arch.equals("arm") || arch.startsWith("armv7")) archId = "arm";
        else if (arch.equals("x86") || arch.equals("i386") || arch.equals("i686")) archId = "386";
        else archId = "unsupported";

        return osId + "-" + archId;
    }

    public static boolean supported() {
        return switch (id()) {
            case "linux-amd64", "linux-arm64", "linux-arm", "linux-386",
                 "windows-amd64", "windows-arm64", "windows-386" -> true;
            default -> false;
        };
    }

    public static String executableName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "cloudflared.exe" : "cloudflared";
    }
}
