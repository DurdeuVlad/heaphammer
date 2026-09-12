package com.dwurdy.heaphammer.diagnostics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventMetricsCounterTest {
    @Test
    void snapshotsOnlyAggregateNamesAndCounts() {
        EventMetricsCounter counter = new EventMetricsCounter();
        counter.recordDispatch("ServerPlayerJoinEvent");
        counter.recordDispatch("ServerPlayerJoinEvent");
        counter.recordRegistration("main-bus");

        EventMetricsSnapshot snapshot = counter.snapshot();
        assertEquals(2L, snapshot.dispatchCounts().get("ServerPlayerJoinEvent"));
        assertEquals(1L, snapshot.registrationCounts().get("main-bus"));
    }
}
