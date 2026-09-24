package com.matejpcs.netflared.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostnameTest {
    @Test
    void buildsHostname() {
        assertEquals("play.example.com", Hostname.build("play", "example.com"));
        assertEquals("mc.eu.example.com", Hostname.build("mc.eu", "example.com"));
    }

    @Test
    void rejectsInvalidLabels() {
        assertThrows(IllegalArgumentException.class, () -> Hostname.build("bad_label", "example.com"));
        assertTrue(Hostname.valid("play.example.com"));
        assertFalse(Hostname.valid("-bad.example.com"));
    }
}
