package com.dwurdy.heaphammer.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkTicketManagerTest {

    @Test
    @DisplayName("ChunkTicketManager acquires, deduplicates, and releases tickets cleanly")
    void testTicketLifecycle() {
        MockPlatformAdapter.MockChunkTicketManager manager = new MockPlatformAdapter.MockChunkTicketManager();
        assertEquals(0, manager.getActiveTicketCount());

        assertTrue(manager.acquireTicket("minecraft:overworld", 0, 0));
        assertTrue(manager.acquireTicket("minecraft:overworld", 1, 0));
        assertFalse(manager.acquireTicket("minecraft:overworld", 0, 0), "Duplicate acquire should return false");

        assertEquals(2, manager.getActiveTicketCount());
        assertEquals(2, manager.getActiveTicketChunkKeys("minecraft:overworld").size());

        assertTrue(manager.releaseTicket("minecraft:overworld", 0, 0));
        assertEquals(1, manager.getActiveTicketCount());
        assertFalse(manager.releaseTicket("minecraft:overworld", 0, 0), "Releasing already released ticket should return false");

        manager.releaseAllTickets();
        assertEquals(0, manager.getActiveTicketCount());
        assertTrue(manager.getActiveTicketChunkKeys("minecraft:overworld").isEmpty());
    }
}
