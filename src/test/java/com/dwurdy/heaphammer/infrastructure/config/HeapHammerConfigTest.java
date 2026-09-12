package com.dwurdy.heaphammer.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HeapHammerConfigTest {

    @Test
    @DisplayName("HeapHammerConfig has sensible production defaults")
    void testDefaultValues() {
        HeapHammerConfig config = new HeapHammerConfig();
        assertEquals(32, config.getMaxRadius());
        assertEquals(50, config.getMaxIterations());
        assertEquals(128, config.getMaxBatchSize());
        assertEquals(10, config.getMaxWarmupIterations());
        assertEquals(1200, config.getMaxHoldTicks());
        assertEquals(1200, config.getMaxSettleTicks());
        assertEquals(50, config.getMaxOperationsPerTick());
        assertEquals(35, config.getMaxMillisPerTick());
        assertTrue(config.isCircuitBreakerEnabled());
        assertEquals(64, config.getMinFreeMemoryMb());
        assertTrue(config.isAutoCleanupOnStartup());
    }

    @Test
    @DisplayName("ConfigManager creates default config on missing file")
    void testCreateDefaultConfig(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve("heaphammer.json");
        assertFalse(configFile.toFile().exists());

        HeapHammerConfig config = ConfigManager.load(configFile);
        assertNotNull(config);
        assertTrue(configFile.toFile().exists());
        assertEquals(32, config.getMaxRadius());
    }

    @Test
    @DisplayName("ConfigManager loads and saves customized configuration")
    void testSaveAndLoadConfig(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("custom_config.json");
        HeapHammerConfig custom = new HeapHammerConfig();
        custom.setMaxRadius(64);
        custom.setMaxBatchSize(256);
        custom.setMinFreeMemoryMb(128);

        ConfigManager.save(configFile, custom);

        HeapHammerConfig loaded = ConfigManager.load(configFile);
        assertEquals(64, loaded.getMaxRadius());
        assertEquals(256, loaded.getMaxBatchSize());
        assertEquals(128, loaded.getMinFreeMemoryMb());
    }
}
