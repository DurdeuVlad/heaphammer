package com.dwurdy.heaphammer.diagnostics;

import com.github.bsideup.jabel.Desugar;

import java.util.Objects;

/** Weak-reference census for one configured target class. */
@Desugar
public record RetentionClassEntry(String className, long observedCount, long liveCount) {
    public RetentionClassEntry {
        Objects.requireNonNull(className, "className must not be null");
    }
}
