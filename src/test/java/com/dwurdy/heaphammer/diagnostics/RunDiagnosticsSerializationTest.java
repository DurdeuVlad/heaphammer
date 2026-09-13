package com.dwurdy.heaphammer.diagnostics;

import com.dwurdy.heaphammer.domain.RunDiagnostics;
import com.dwurdy.heaphammer.infrastructure.json.GsonCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RunDiagnosticsSerializationTest {

    @Test
    @DisplayName("structured diagnostics preserve histogram, retention, world-store, and event evidence")
    void diagnosticsRoundTrip() {
        RetentionSnapshot retention = new RetentionSnapshot(
                10L,
                Map.of("example.Player", new RetentionClassEntry("example.Player", 3L, 2L)));
        WorldStoreDimensionSnapshot world = new WorldStoreDimensionSnapshot(
                "minecraft:overworld", 2L, 4096L, 9L, 7L, 4L, 3L, 1L, Map.of("0-99", 4L));
        WorldStoreSnapshot worldSnapshot = new WorldStoreSnapshot(10L, Map.of("minecraft:overworld", world));
        EventMetricsSnapshot events = new EventMetricsSnapshot(10L, Map.of("PlayerLoggedOutEvent", 3L), Map.of());

        RunDiagnostics diagnostics = new RunDiagnostics(
                null,
                null,
                new HistogramDiff(List.of(), 0L, 0L),
                retention,
                retention,
                List.of(retention),
                worldSnapshot,
                worldSnapshot,
                events,
                events,
                List.of("optional collector warning")
        );

        RunDiagnostics restored = GsonCodec.fromJson(GsonCodec.toJson(diagnostics), RunDiagnostics.class);

        assertEquals(2L, restored.finalRetention().entries().get("example.Player").liveCount());
        assertEquals(4096L, restored.finalWorldStore().dimensions().get("minecraft:overworld").regionBytes());
        assertEquals(3L, restored.finalEvents().dispatchCounts().get("PlayerLoggedOutEvent"));
        assertEquals(List.of("optional collector warning"), restored.warnings());
    }
}
