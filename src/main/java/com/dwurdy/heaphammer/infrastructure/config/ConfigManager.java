package com.dwurdy.heaphammer.infrastructure.config;

import com.dwurdy.heaphammer.infrastructure.FileStorage;
import com.dwurdy.heaphammer.infrastructure.json.GsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Service managing loading, persisting, and hot-reloading of HeapHammerConfig.
 */
public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-config");
    private static volatile HeapHammerConfig activeConfig = new HeapHammerConfig();
    private static Path activeConfigPath = Paths.get(HeapHammerConfig.DEFAULT_CONFIG_PATH);

    public static HeapHammerConfig getActiveConfig() {
        return activeConfig;
    }

    public static synchronized void setActiveConfig(HeapHammerConfig config) {
        activeConfig = Objects.requireNonNull(config, "config must not be null");
    }

    public static synchronized HeapHammerConfig load(Path configPath) {
        activeConfigPath = Objects.requireNonNull(configPath, "configPath must not be null");
        if (!Files.exists(configPath)) {
            HeapHammerConfig defaultConfig = new HeapHammerConfig();
            try {
                save(configPath, defaultConfig);
                LOGGER.info("Created default HeapHammer configuration at {}", configPath.toAbsolutePath());
            } catch (IOException e) {
                LOGGER.warn("Failed to write default configuration to {}: {}", configPath, e.getMessage());
            }
            activeConfig = defaultConfig;
            return defaultConfig;
        }

        try {
            String json = FileStorage.readString(configPath);
            HeapHammerConfig loaded = GsonCodec.fromJson(json, HeapHammerConfig.class);
            if (loaded != null) {
                activeConfig = loaded;
                LOGGER.info("Loaded HeapHammer configuration from {}", configPath.toAbsolutePath());
                return loaded;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse config file at {}. Using safe default configuration. Error: {}", configPath, e.getMessage());
        }

        activeConfig = new HeapHammerConfig();
        return activeConfig;
    }

    public static synchronized void save(Path configPath, HeapHammerConfig config) throws IOException {
        Objects.requireNonNull(configPath, "configPath must not be null");
        Objects.requireNonNull(config, "config must not be null");
        String json = GsonCodec.toJson(config);
        FileStorage.writeStringAtomic(configPath, json);
    }

    public static synchronized HeapHammerConfig reload() {
        return load(activeConfigPath);
    }

    public static Path getActiveConfigPath() {
        return activeConfigPath;
    }
}
