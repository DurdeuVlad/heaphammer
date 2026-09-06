package com.dwurdy.heaphammer.detection;

import com.dwurdy.heaphammer.platform.ChunkTicketManager;
import com.dwurdy.heaphammer.platform.PlatformAdapter;

import java.util.Objects;
import java.util.Set;

/**
 * Validates that HeapHammer-owned tickets are cleared and measures post-cleanup state.
 */
public class CleanupValidator {
    private final PlatformAdapter platform;

    public CleanupValidator(PlatformAdapter platform) {
        this.platform = Objects.requireNonNull(platform, "platform must not be null");
    }

    public CleanupResult validate(String dimension) {
        ChunkTicketManager ticketManager = platform.getChunkTicketManager();
        Set<Long> remainingKeys = ticketManager.getActiveTicketChunkKeys(dimension);
        int remainingTickets = remainingKeys.size();
        int loadedChunks = platform.getLoadedChunkCount(dimension);

        if (remainingTickets > 0) {
            return CleanupResult.failed(
                    remainingTickets,
                    remainingKeys,
                    loadedChunks,
                    "Cleanup failed: " + remainingTickets + " HeapHammer tickets were not released."
            );
        }

        return CleanupResult.clean(loadedChunks);
    }
}
