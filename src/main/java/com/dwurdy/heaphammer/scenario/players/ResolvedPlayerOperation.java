package com.dwurdy.heaphammer.scenario.players;

import com.github.bsideup.jabel.Desugar;

import com.dwurdy.heaphammer.domain.PlayerAction;

import java.util.Objects;
import java.util.UUID;

/** Deterministic player lifecycle operation containing no live server object. */
@Desugar
public record ResolvedPlayerOperation(
        int iteration,
        int operationIndex,
        String dimension,
        UUID playerId,
        String profileName,
        PlayerAction action,
        double x,
        double y,
        double z
) {
    public ResolvedPlayerOperation {
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(profileName, "profileName must not be null");
        Objects.requireNonNull(action, "action must not be null");
    }
}
