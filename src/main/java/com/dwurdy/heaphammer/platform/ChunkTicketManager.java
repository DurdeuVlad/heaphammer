package com.dwurdy.heaphammer.platform;

import java.util.Set;

/**
 * Contract for managing server chunk tickets owned by HeapHammer (BR-003, Section 18).
 */
public interface ChunkTicketManager {

    /**
     * Acquire a chunk ticket for the specified dimension and chunk coordinates.
     * @return true if ticket was acquired, false if dimension was unavailable or ticket failed.
     */
    boolean acquireTicket(String dimension, int chunkX, int chunkZ);

    /**
     * Release a chunk ticket previously acquired by HeapHammer.
     * @return true if ticket was found and removed, false otherwise.
     */
    boolean releaseTicket(String dimension, int chunkX, int chunkZ);

    /**
     * Total number of chunk tickets actively held by HeapHammer across all dimensions.
     */
    int getActiveTicketCount();

    /**
     * Set of active chunk coordinate keys (ChunkPos packed long) held in the given dimension.
     */
    Set<Long> getActiveTicketChunkKeys(String dimension);

    /**
     * Release all tickets owned by HeapHammer (emergency stop or final cleanup).
     */
    void releaseAllTickets();
}
