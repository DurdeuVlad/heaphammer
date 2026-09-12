package com.dwurdy.heaphammer.platform;

import com.dwurdy.heaphammer.domain.EnvironmentFingerprint;

import java.util.List;
import java.util.UUID;
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

    // Entity lifecycle operations (Phase 3, Issue #16)
    List<String> getAvailableEntityTypes();

    UUID spawnEntity(String dimension, String entityTypeId, double x, double y, double z);

    boolean removeEntity(String dimension, UUID entityUuid, String removeMode);

    int removeAllTestEntities(String dimension);

    // Block entity lifecycle operations (Phase 3, Issue #17)
    List<String> getAvailableBlockEntityTypes();

    boolean placeBlockEntity(String dimension, String blockEntityTypeId, int x, int y, int z);

    boolean removeBlockEntity(String dimension, int x, int y, int z);

    int removeAllTestBlockEntities(String dimension);

    /**
     * Purges any orphaned test state (leftover chunk tickets, test-tagged entities, block entities)
     * across all dimensions, typically invoked on server startup or after a crash.
     *
     * @return count of cleaned up assets
     */
    default int cleanupOrphanedState() {
        return 0;
    }
}
