package com.dwurdy.heaphammer.diagnostics;

import com.github.bsideup.jabel.Desugar;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Difference between two weak-reference censuses. */
@Desugar
public record RetentionDelta(long timestamp, Map<String, RetentionDeltaEntry> entries) {
    public RetentionDelta {
        Objects.requireNonNull(entries, "entries must not be null");
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }
}
