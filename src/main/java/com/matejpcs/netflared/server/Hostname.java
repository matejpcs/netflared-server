package com.matejpcs.netflared.server;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Hostname {
    private static final Pattern LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    private Hostname() {}

    public static String build(String subdomain, String zone) {
        String cleanZone = zone.trim().toLowerCase(Locale.ROOT).replaceAll("^\\.+|\\.+$", "");
        String cleanSubdomain = subdomain == null ? "" : subdomain.trim().toLowerCase(Locale.ROOT).replaceAll("^\\.+|\\.+$", "");
        if (cleanSubdomain.isBlank()) return cleanZone;
        for (String label : cleanSubdomain.split("\\.")) {
            if (!LABEL.matcher(label).matches()) throw new IllegalArgumentException("Invalid hostname label: " + label);
        }
        return cleanSubdomain + "." + cleanZone;
    }

    public static boolean valid(String hostname) {
        if (hostname == null || hostname.length() > 253 || hostname.isBlank()) return false;
        for (String label : hostname.toLowerCase(Locale.ROOT).split("\\.")) {
            if (!LABEL.matcher(label).matches()) return false;
        }
        return true;
    }
}
