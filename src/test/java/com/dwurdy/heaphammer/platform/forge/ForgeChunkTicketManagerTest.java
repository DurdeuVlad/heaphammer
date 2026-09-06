package com.dwurdy.heaphammer.platform.forge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ForgeChunkTicketManagerTest {

    private MockForgeTicketBridge mockBridge;
    private ForgeChunkTicketManager ticketManager;

    @BeforeEach
    void setUp() {
        mockBridge = new MockForgeTicketBridge();
        ticketManager = new ForgeChunkTicketManager(mockBridge);
    }

    @Test
    @DisplayName("Ticket acquisition packs chunk coordinates and forces chunks")
    void testAcquireAndReleaseTicket() {
        assertTrue(ticketManager.acquireTicket("minecraft:overworld", 10, 20));
        assertEquals(1, ticketManager.getActiveTicketCount());

        Set<Long> keys = ticketManager.getActiveTicketChunkKeys("minecraft:overworld");
        assertEquals(1, keys.size());
        long packed = ForgeChunkTicketManager.packChunkPos(10, 20);
        assertTrue(keys.contains(packed));
        assertEquals(10, ForgeChunkTicketManager.unpackChunkX(packed));
        assertEquals(20, ForgeChunkTicketManager.unpackChunkZ(packed));

        // Release ticket
        assertTrue(ticketManager.releaseTicket("minecraft:overworld", 10, 20));
        assertEquals(0, ticketManager.getActiveTicketCount());
        assertTrue(ticketManager.getActiveTicketChunkKeys("minecraft:overworld").isEmpty());
    }

    @Test
    @DisplayName("Acquiring duplicate chunk ticket returns true without leaking multiple tickets")
    void testDuplicateAcquireIdempotency() {
        assertTrue(ticketManager.acquireTicket("minecraft:overworld", 5, 5));
        assertTrue(ticketManager.acquireTicket("minecraft:overworld", 5, 5));
        assertEquals(1, ticketManager.getActiveTicketCount());
        assertEquals(1, mockBridge.forcedCount);
    }

    @Test
    @DisplayName("Acquiring ticket in unloaded dimension returns false")
    void testUnloadedDimension() {
        mockBridge.dimensionLoaded = false;
        assertFalse(ticketManager.acquireTicket("minecraft:overworld", 0, 0));
        assertEquals(0, ticketManager.getActiveTicketCount());
    }

    @Test
    @DisplayName("Release all tickets cleans up active tickets across all dimensions")
    void testReleaseAllTickets() {
        ticketManager.acquireTicket("minecraft:overworld", 1, 1);
        ticketManager.acquireTicket("minecraft:overworld", 1, 2);
        ticketManager.acquireTicket("minecraft:the_nether", 0, 0);
        assertEquals(3, ticketManager.getActiveTicketCount());

        ticketManager.releaseAllTickets();
        assertEquals(0, ticketManager.getActiveTicketCount());
        assertEquals(0, mockBridge.activeTickets.size());
    }

    private static class MockForgeTicketBridge implements ForgeTicketBridge {
        boolean dimensionLoaded = true;
        int forcedCount = 0;
        final Set<Object> activeTickets = new HashSet<>();

        @Override
        public Object requestTicket(String dimension) {
            Object ticket = new Object();
            activeTickets.add(ticket);
            return ticket;
        }

        @Override
        public boolean forceChunk(Object ticket, String dimension, int chunkX, int chunkZ) {
            forcedCount++;
            return true;
        }

        @Override
        public boolean unforceChunk(Object ticket, String dimension, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public void releaseTicket(Object ticket) {
            activeTickets.remove(ticket);
        }

        @Override
        public boolean isDimensionLoaded(String dimension) {
            return dimensionLoaded;
        }
    }
}
