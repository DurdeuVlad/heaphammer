package com.dwurdy.heaphammer.platform;

import com.dwurdy.heaphammer.domain.EnvironmentFingerprint;

import java.util.function.Consumer;

/**
 * Platform abstraction isolating Minecraft and mod loader interactions (Section 15.7).
 */
public interface PlatformAdapter {

    ChunkTicketManager getChunkTicketManager();

    EnvironmentFingerprint captureFingerprint();

    int getLoadedChunkCount(String dimension);

    int getTotalLoadedChunkCount();

    int getActiveEntityCount(String dimension);

    boolean isDimensionAvailable(String dimension);

    void registerServerTickHook(Consumer<Long> tickConsumer);

    boolean isServerReady();
}
