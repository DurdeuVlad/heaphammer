package com.dwurdy.heaphammer.domain;

import com.github.bsideup.jabel.Desugar;

import java.util.Objects;

/**
 * Identifier for a scenario family (e.g. "chunks").
 */
@Desugar
public record ScenarioId(String value) {
    public static final ScenarioId CHUNKS = new ScenarioId("chunks");
    public static final ScenarioId ENTITIES = new ScenarioId("entities");
    public static final ScenarioId BLOCK_ENTITIES = new ScenarioId("blockentities");

    public static ScenarioId of(String value) {
        return new ScenarioId(value);
    }

    public ScenarioId {
        Objects.requireNonNull(value, "ScenarioId value must not be null");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("ScenarioId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
