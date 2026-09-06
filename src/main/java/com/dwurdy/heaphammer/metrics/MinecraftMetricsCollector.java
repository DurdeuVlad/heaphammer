package com.dwurdy.heaphammer.metrics;

import com.dwurdy.heaphammer.platform.PlatformAdapter;

import java.util.Objects;

/**
 * Collects Minecraft world resource metrics (chunks, entities).
 */
public class MinecraftMetricsCollector {
    private final PlatformAdapter platform;

    public MinecraftMetricsCollector(PlatformAdapter platform) {
        this.platform = Objects.requireNonNull(platform, "platform must not be null");
    }

    public int getLoadedChunks(String dimension) {
        return platform.getLoadedChunkCount(dimension);
    }

    public int getActiveEntities(String dimension) {
        return platform.getActiveEntityCount(dimension);
    }
}
