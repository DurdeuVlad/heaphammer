package com.dwurdy.heaphammer.diagnostics;

import com.github.bsideup.jabel.Desugar;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Point-in-time weak-reference census. */
@Desugar
public record RetentionSnapshot(long timestamp, Map<String, RetentionClassEntry> entries) {
    public RetentionSnapshot {
        Objects.requireNonNull(entries, "entries must not be null");
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public static RetentionSnapshot empty() {
        return new RetentionSnapshot(System.currentTimeMillis(), Collections.emptyMap());
    }

    public RetentionDelta diff(RetentionSnapshot baseline) {
        Map<String, RetentionDeltaEntry> deltas = new LinkedHashMap<>();
        Map<String, RetentionClassEntry> base = baseline == null ? Collections.emptyMap() : baseline.entries();
        for (Map.Entry<String, RetentionClassEntry> current : entries.entrySet()) {
            RetentionClassEntry previous = base.get(current.getKey());
            long previousObserved = previous == null ? 0L : previous.observedCount();
            long previousLive = previous == null ? 0L : previous.liveCount();
            deltas.put(current.getKey(), new RetentionDeltaEntry(
                    current.getKey(),
                    current.getValue().observedCount() - previousObserved,
                    current.getValue().liveCount() - previousLive));
        }
        return new RetentionDelta(timestamp, deltas);
    }
}
