package com.dwurdy.heaphammer.diagnostics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Counter snapshot for event dispatches and listener registration deltas. */
public record EventMetricsSnapshot(
        long timestamp,
        Map<String, Long> dispatchCounts,
        Map<String, Long> registrationCounts
) {
    public EventMetricsSnapshot {
        Objects.requireNonNull(dispatchCounts, "dispatchCounts must not be null");
        Objects.requireNonNull(registrationCounts, "registrationCounts must not be null");
        dispatchCounts = Collections.unmodifiableMap(new LinkedHashMap<>(dispatchCounts));
        registrationCounts = Collections.unmodifiableMap(new LinkedHashMap<>(registrationCounts));
    }

    public static EventMetricsSnapshot empty() {
        return new EventMetricsSnapshot(System.currentTimeMillis(), Collections.emptyMap(), Collections.emptyMap());
    }

    public EventMetricsDelta diff(EventMetricsSnapshot baseline) {
        return new EventMetricsDelta(timestamp,
                difference(dispatchCounts, baseline == null ? Collections.emptyMap() : baseline.dispatchCounts()),
                difference(registrationCounts, baseline == null ? Collections.emptyMap() : baseline.registrationCounts()));
    }

    private static Map<String, Long> difference(Map<String, Long> current, Map<String, Long> baseline) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : current.entrySet()) {
            result.put(entry.getKey(), entry.getValue() - baseline.getOrDefault(entry.getKey(), 0L));
        }
        return result;
    }
}
