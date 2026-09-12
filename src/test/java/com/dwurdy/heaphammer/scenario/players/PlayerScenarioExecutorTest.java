package com.dwurdy.heaphammer.scenario.players;

import com.dwurdy.heaphammer.domain.*;
import com.dwurdy.heaphammer.platform.PlayerLifecyclePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PlayerScenarioExecutorTest {
    @Test
    @DisplayName("player executor reaches real lifecycle port and cleans all test players")
    void executesAndCleansPlayers() {
        FakePlayerPort port = new FakePlayerPort();
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.PLAYERS)
                .iterations(1)
                .loginsPerCycle(1)
                .playerActions(List.of(PlayerAction.JOIN, PlayerAction.RESPAWN, PlayerAction.QUIT))
                .maxOperationsPerTick(2)
                .settleTicks(1)
                .build();
        ExperimentPlan plan = new PlayerScenarioPlanner().plan(spec);
        List<PlayerAction> actions = new ArrayList<>();
        AtomicReference<ExperimentState> finalState = new AtomicReference<>();

        PlayerScenarioExecutor executor = new PlayerScenarioExecutor(plan, port,
                (phase, iteration) -> {}, finalState::set);
        int ticks = 0;
        while (!executor.getStateMachine().getState().isTerminal() && ticks++ < 100) {
            executor.tick();
        }
        actions.addAll(port.actions);

        assertEquals(ExperimentState.COMPLETED, executor.getStateMachine().getState());
        assertEquals(ExperimentState.COMPLETED, finalState.get());
        assertEquals(List.of(PlayerAction.JOIN, PlayerAction.RESPAWN, PlayerAction.QUIT), actions);
        assertEquals(0, port.active.size());
        assertTrue(port.cleanupCalls > 0);
    }

    private static final class FakePlayerPort implements PlayerLifecyclePort {
        private final Set<UUID> active = new HashSet<>();
        private final List<PlayerAction> actions = new ArrayList<>();
        private int cleanupCalls;

        @Override
        public UUID join(String dimension, String profileName, UUID profileId, double x, double y, double z) {
            active.add(profileId);
            actions.add(PlayerAction.JOIN);
            return profileId;
        }

        @Override
        public boolean perform(String dimension, UUID playerId, PlayerAction action, String targetDimension, double x, double y, double z) {
            actions.add(action);
            return active.contains(playerId);
        }

        @Override
        public boolean quit(String dimension, UUID playerId) {
            actions.add(PlayerAction.QUIT);
            return active.remove(playerId);
        }

        @Override
        public int activeTestPlayerCount() {
            return active.size();
        }

        @Override
        public int cleanupTestPlayers() {
            cleanupCalls++;
            int count = active.size();
            active.clear();
            return count;
        }
    }
}
