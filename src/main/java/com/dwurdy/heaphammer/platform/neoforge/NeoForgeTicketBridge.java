package com.dwurdy.heaphammer.platform.neoforge;

/**
 * Bridge interface decoupling NeoForge 1.21.x chunk ticket operations
 * from runtime MinecraftServer and ServerLevel instances.
 */
public interface NeoForgeTicketBridge {

    /**
     * Request a dedicated HeapHammer chunk ticket in the specified dimension.
     *
     * @param dimension resource location string (e.g. "minecraft:overworld")
     * @param chunkX    chunk X coordinate
     * @param chunkZ    chunk Z coordinate
     * @return true if ticket was registered on the ServerChunkCache
     */
    boolean addRegionTicket(String dimension, int chunkX, int chunkZ);

    /**
     * Release a previously acquired HeapHammer chunk ticket in the specified dimension.
     *
     * @param dimension resource location string
     * @param chunkX    chunk X coordinate
     * @param chunkZ    chunk Z coordinate
     * @return true if ticket was removed from the ServerChunkCache
     */
    boolean removeRegionTicket(String dimension, int chunkX, int chunkZ);

    /**
     * Checks if the specified dimension is loaded and accessible on the server.
     *
     * @param dimension resource location string
     * @return true if dimension exists and is loaded
     */
    boolean isDimensionLoaded(String dimension);
}
