package com.dwurdy.heaphammer.diagnostics;

import java.util.Objects;

/** Weak-reference census for one configured target class. */
public record RetentionClassEntry(String className, long observedCount, long liveCount) {
    public RetentionClassEntry {
        Objects.requireNonNull(className, "className must not be null");
    }
}
