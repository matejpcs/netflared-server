package com.matejpcs.netflared.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlatformTest {
    @Test
    void platformIdIsPresent() {
        assertNotNull(Platform.id());
        assertFalse(Platform.id().isBlank());
    }
}
