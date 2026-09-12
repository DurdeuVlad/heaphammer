package com.dwurdy.heaphammer.scenario.players;

import com.dwurdy.heaphammer.domain.ExperimentId;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.domain.PlayerAction;
import com.dwurdy.heaphammer.domain.ScenarioId;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deterministic planner for real server-side player lifecycle churn. */
public final class PlayerScenarioPlanner {
    public ExperimentPlan plan(ExperimentSpec spec) {
        return plan(ExperimentId.generate(), spec);
    }

    public ExperimentPlan plan(ExperimentId id, ExperimentSpec spec) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
        if (!ScenarioId.PLAYERS.equals(spec.scenarioId())) {
            throw new IllegalArgumentException("PlayerScenarioPlanner requires the players scenario");
        }

        List<PlayerAction> actions = normalizedActions(spec.playerActions());
        List<ResolvedPlayerOperation> operations = new ArrayList<>();
        int operationIndex = 0;
        for (int iteration = 0; iteration < spec.iterations(); iteration++) {
            for (int login = 0; login < spec.loginsPerCycle(); login++) {
                UUID playerId = UUID.nameUUIDFromBytes(("heaphammer-player:" + spec.seed() + ":" + iteration + ":" + login)
                        .getBytes(StandardCharsets.UTF_8));
                String profileName = "hh_test_" + playerId.toString().replace("-", "").substring(0, 12);
                for (PlayerAction action : actions) {
                    operations.add(new ResolvedPlayerOperation(
                            iteration,
                            operationIndex++,
                            spec.dimension(),
                            playerId,
                            profileName,
                            action,
                            spec.centerX(),
                            64.0,
                            spec.centerZ()
                    ));
                }
            }
        }

        int estimatedTicks = spec.iterations() * (spec.holdTicks() + spec.settleTicks()
                + Math.max(1, operations.size() / spec.maxOperationsPerTick()));
        return ExperimentPlan.forPlayers(id, System.currentTimeMillis(), spec, operations, estimatedTicks);
    }

    private static List<PlayerAction> normalizedActions(List<PlayerAction> requested) {
        List<PlayerAction> actions = new ArrayList<>(requested == null ? List.of() : requested);
        if (actions.isEmpty() || actions.get(0) != PlayerAction.JOIN) {
            actions.add(0, PlayerAction.JOIN);
        }
        if (actions.get(actions.size() - 1) != PlayerAction.QUIT) {
            actions.add(PlayerAction.QUIT);
        }
        return actions;
    }
}
