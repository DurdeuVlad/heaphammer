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

    @Test
    @DisplayName("cohort executor holds all players online and ticks connection housekeeping")
    void cohortExecutorHoldsPlayersOnline() {
        FakePlayerPort port = new FakePlayerPort();
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.PLAYERS)
                .iterations(1)
                .loginsPerCycle(3)
                .playerCohort(true)
                .playerActions(List.of(PlayerAction.JOIN, PlayerAction.LOOKAT, PlayerAction.QUIT))
                .settleTicks(1)
                .build();
        ExperimentPlan plan = new PlayerScenarioPlanner().plan(spec);

        PlayerScenarioExecutor executor = new PlayerScenarioExecutor(plan, port,
                (phase, iteration) -> {}, state -> {});
        int ticks = 0;
        while (!executor.getStateMachine().getState().isTerminal() && ticks++ < 200) {
            executor.tick();
        }

        assertEquals(ExperimentState.COMPLETED, executor.getStateMachine().getState());
        // Order: 3 joins, 3 lookats (all while online), 3 quits.
        assertEquals(List.of(
                PlayerAction.JOIN, PlayerAction.JOIN, PlayerAction.JOIN,
                PlayerAction.LOOKAT, PlayerAction.LOOKAT, PlayerAction.LOOKAT,
                PlayerAction.QUIT, PlayerAction.QUIT, PlayerAction.QUIT), port.actions);
        assertTrue(port.housekeepingTicks > 0, "executor must drain synthetic connections every tick");
    }

    @Test
    @DisplayName("cohort dwell keeps the aimed cohort online for holdTicks before quitting")
    void cohortDwellsForHoldTicks() {
        FakePlayerPort port = new FakePlayerPort();
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.PLAYERS)
                .iterations(1)
                .loginsPerCycle(3)
                .playerCohort(true)
                .playerActions(List.of(PlayerAction.JOIN, PlayerAction.LOOKAT, PlayerAction.QUIT))
                .holdTicks(5)
                .settleTicks(1)
                .build();
        ExperimentPlan plan = new PlayerScenarioPlanner().plan(spec);

        PlayerScenarioExecutor executor = new PlayerScenarioExecutor(plan, port,
                (phase, iteration) -> {}, state -> {});
        List<Integer> onlinePerTick = new ArrayList<>();
        int ticks = 0;
        while (!executor.getStateMachine().getState().isTerminal() && ticks++ < 200) {
            executor.tick();
            onlinePerTick.add(port.active.size());
        }

        assertEquals(ExperimentState.COMPLETED, executor.getStateMachine().getState());
        // The full cohort must stay online for the entire holdTicks dwell:
        // the last 5 consecutive ticks before the quits all show 3 online.
        int lastFullIndex = -1;
        for (int i = onlinePerTick.size() - 1; i >= 0; i--) {
            if (onlinePerTick.get(i) == 3) { lastFullIndex = i; break; }
        }
        assertTrue(lastFullIndex >= 4, "cohort must be online at least holdTicks ticks before quits");
        int window = 0;
        for (int i = lastFullIndex; i >= 0 && onlinePerTick.get(i) == 3; i--) window++;
        assertTrue(window >= 5, "expected >=5 consecutive full-cohort ticks, got " + window);
    }

    private static final class FakePlayerPort implements PlayerLifecyclePort {
        private final Set<UUID> active = new HashSet<>();
        private final List<PlayerAction> actions = new ArrayList<>();
        private int cleanupCalls;
        private int housekeepingTicks;

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

        @Override
        public void housekeepingTick() {
            housekeepingTicks++;
        }
    }
}
