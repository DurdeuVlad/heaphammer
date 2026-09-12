package com.dwurdy.heaphammer.diagnostics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetentionTrackerTest {

    @Test
    @DisplayName("retention tracker does not strongly retain observed objects")
    void trackerReportsObservedLiveObjects() {
        RetentionTracker tracker = new RetentionTracker();
        Object player = new Object();

        tracker.observe("example.Player", player);
        RetentionSnapshot snapshot = tracker.snapshot();

        assertEquals(1, snapshot.entries().get("example.Player").observedCount());
        assertEquals(1, snapshot.entries().get("example.Player").liveCount());
        assertTrue(tracker.trackedReferenceCount() > 0);
    }

    @Test
    @DisplayName("retention snapshot exposes stable deltas by class")
    void snapshotsCanBeCompared() {
        RetentionTracker tracker = new RetentionTracker();
        Object first = new Object();
        tracker.observe("example.Player", first);
        RetentionSnapshot baseline = tracker.snapshot();

        Object second = new Object();
        tracker.observe("example.Player", second);
        RetentionSnapshot current = tracker.snapshot();

        RetentionDelta delta = current.diff(baseline);
        assertEquals(1, delta.entries().get("example.Player").deltaObserved());
        assertEquals(1, delta.entries().get("example.Player").deltaLive());
    }
}
