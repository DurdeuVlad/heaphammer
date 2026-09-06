package com.dwurdy.heaphammer.detection;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Result of validating world cleanup following an experiment cycle (BR-005).
 */
public record CleanupResult(
        CleanupStatus status,
        int remainingHeapHammerTickets,
        int totalLoadedChunks,
        Set<Long> holdoutChunkKeys,
        String details
) {
    public enum CleanupStatus {
        CLEAN,
        FOREIGN_HOLD,
        CLEANUP_FAILED
    }

    public CleanupResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(details, "details must not be null");
        holdoutChunkKeys = (holdoutChunkKeys == null) ? Collections.emptySet() : Collections.unmodifiableSet(Set.copyOf(holdoutChunkKeys));
    }

    public boolean isClean() {
        return status == CleanupStatus.CLEAN || status == CleanupStatus.FOREIGN_HOLD;
    }

    public static CleanupResult clean(int totalLoadedChunks) {
        return new CleanupResult(CleanupStatus.CLEAN, 0, totalLoadedChunks, Set.of(), "Cleanup verified: 0 tickets remaining");
    }

    public static CleanupResult failed(int remainingTickets, Set<Long> holdoutKeys, int totalLoadedChunks, String details) {
        return new CleanupResult(CleanupStatus.CLEANUP_FAILED, remainingTickets, totalLoadedChunks, holdoutKeys, details);
    }

    public static CleanupResult foreignHold(int totalLoadedChunks, String details) {
        return new CleanupResult(CleanupStatus.FOREIGN_HOLD, 0, totalLoadedChunks, Set.of(), details);
    }
}
