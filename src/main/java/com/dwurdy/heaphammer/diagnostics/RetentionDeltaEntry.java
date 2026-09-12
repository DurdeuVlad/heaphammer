package com.dwurdy.heaphammer.diagnostics;

import com.github.bsideup.jabel.Desugar;

import java.util.Objects;

/** Difference between two weak-reference censuses for one class. */
@Desugar
public record RetentionDeltaEntry(String className, long deltaObserved, long deltaLive) {
    public RetentionDeltaEntry {
        Objects.requireNonNull(className, "className must not be null");
    }
}
