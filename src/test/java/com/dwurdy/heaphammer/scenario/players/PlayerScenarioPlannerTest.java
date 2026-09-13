package com.dwurdy.heaphammer.scenario.players;

import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.domain.PlayerAction;
import com.dwurdy.heaphammer.domain.ScenarioId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerScenarioPlannerTest {
    @Test
    @DisplayName("player planner emits deterministic join/action/quit operations")
    void plansDeterministicLifecycle() {
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.PLAYERS)
                .iterations(2)
                .loginsPerCycle(2)
                .playerActions(List.of(PlayerAction.JOIN, PlayerAction.RESPAWN, PlayerAction.QUIT))
                .build();

        ExperimentPlan first = new PlayerScenarioPlanner().plan(spec);
        ExperimentPlan second = new PlayerScenarioPlanner().plan(spec);

        assertEquals(12, first.playerOperations().size());
        assertEquals(first.playerOperations(), second.playerOperations());
        assertEquals(PlayerAction.JOIN, first.playerOperations().get(0).action());
        assertEquals(PlayerAction.RESPAWN, first.playerOperations().get(1).action());
        assertEquals(PlayerAction.QUIT, first.playerOperations().get(2).action());
    }
}
