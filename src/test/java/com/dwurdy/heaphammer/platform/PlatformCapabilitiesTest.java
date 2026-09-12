package com.dwurdy.heaphammer.platform;

import com.dwurdy.heaphammer.domain.PlatformCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlatformCapabilitiesTest {
    @Test
    @DisplayName("legacy adapters default to explicit unsupported capabilities")
    void defaultAdapterCapabilitiesAreSafe() {
        MockPlatformAdapter adapter = new MockPlatformAdapter();

        assertFalse(adapter.getCapabilities().supports(PlatformCapability.PLAYER_LIFECYCLE));
        assertFalse(adapter.getPlayerLifecyclePort().isPresent());
        assertFalse(adapter.getEntityLifecyclePort().isPresent());
        assertFalse(adapter.getWorldStoreMetricsPort().isPresent());
    }
}
