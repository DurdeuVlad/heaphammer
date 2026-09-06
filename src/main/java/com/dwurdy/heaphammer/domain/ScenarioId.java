package com.dwurdy.heaphammer.domain;

import java.util.Objects;

/**
 * Identifier for a scenario family (e.g. "chunks").
 */
public record ScenarioId(String value) {
    public static final ScenarioId CHUNKS = new ScenarioId("chunks");

    public ScenarioId {
        Objects.requireNonNull(value, "ScenarioId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ScenarioId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
