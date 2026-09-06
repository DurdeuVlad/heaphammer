package com.dwurdy.heaphammer.detection;

import com.dwurdy.heaphammer.platform.MockPlatformAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CleanupValidatorTest {

    @Test
    @DisplayName("CleanupValidator returns CLEAN when 0 HeapHammer tickets remain")
    void testCleanValidation() {
        MockPlatformAdapter mockPlatform = new MockPlatformAdapter();
        CleanupValidator validator = new CleanupValidator(mockPlatform);

        CleanupResult result = validator.validate("minecraft:overworld");
        assertTrue(result.isClean());
        assertEquals(CleanupResult.CleanupStatus.CLEAN, result.status());
        assertEquals(0, result.remainingHeapHammerTickets());
    }

    @Test
    @DisplayName("CleanupValidator returns CLEANUP_FAILED when HeapHammer tickets are unreleased")
    void testFailedValidation() {
        MockPlatformAdapter mockPlatform = new MockPlatformAdapter();
        mockPlatform.getChunkTicketManager().acquireTicket("minecraft:overworld", 10, 20);

        CleanupValidator validator = new CleanupValidator(mockPlatform);
        CleanupResult result = validator.validate("minecraft:overworld");

        assertFalse(result.isClean());
        assertEquals(CleanupResult.CleanupStatus.CLEANUP_FAILED, result.status());
        assertEquals(1, result.remainingHeapHammerTickets());
        assertFalse(result.holdoutChunkKeys().isEmpty());
    }
}
