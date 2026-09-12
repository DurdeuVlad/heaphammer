package com.dwurdy.heaphammer.diagnostics;

import java.util.Objects;

/** Difference between two weak-reference censuses for one class. */
public record RetentionDeltaEntry(String className, long deltaObserved, long deltaLive) {
    public RetentionDeltaEntry {
        Objects.requireNonNull(className, "className must not be null");
    }
}
