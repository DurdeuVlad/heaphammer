package com.dwurdy.heaphammer.platform.forge;

/**
 * Bridge interface decoupling ForgeChunkManager operations from specific MinecraftForge runtime jars.
 * This allows HeapHammer to run unit tests and portable analysis without requiring
 * Forge 1.12.2 build classpath dependencies during compilation.
 */
public interface ForgeTicketBridge {

    /**
     * Request a Forge chunk ticket for the specified dimension.
     * @param dimension Dimension identifier (e.g., "minecraft:overworld", "0", "-1")
     * @return Opaque ticket object or null if request failed
     */
    Object requestTicket(String dimension);

    /**
     * Force a chunk using an acquired ticket.
     */
    boolean forceChunk(Object ticket, String dimension, int chunkX, int chunkZ);

    /**
     * Unforce a chunk previously held by a ticket.
     */
    boolean unforceChunk(Object ticket, String dimension, int chunkX, int chunkZ);

    /**
     * Release a ticket completely back to Forge Chunk Manager.
     */
    void releaseTicket(Object ticket);

    /**
     * Check if the target dimension exists and is loaded.
     */
    boolean isDimensionLoaded(String dimension);
}
