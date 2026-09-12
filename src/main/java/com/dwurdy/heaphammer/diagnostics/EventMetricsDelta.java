package com.dwurdy.heaphammer.diagnostics;

import com.github.bsideup.jabel.Desugar;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Difference between two event counter snapshots. */
@Desugar
public record EventMetricsDelta(long timestamp, Map<String, Long> dispatchCounts, Map<String, Long> registrationCounts) {
    public EventMetricsDelta {
        Objects.requireNonNull(dispatchCounts, "dispatchCounts must not be null");
        Objects.requireNonNull(registrationCounts, "registrationCounts must not be null");
        dispatchCounts = Collections.unmodifiableMap(new LinkedHashMap<>(dispatchCounts));
        registrationCounts = Collections.unmodifiableMap(new LinkedHashMap<>(registrationCounts));
    }
}
