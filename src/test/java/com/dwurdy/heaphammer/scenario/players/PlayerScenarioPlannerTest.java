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

    @Test
    @DisplayName("cohort mode keeps every login online simultaneously before quitting")
    void cohortKeepsPlayersConcurrentlyOnline() {
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.PLAYERS)
                .iterations(2)
                .loginsPerCycle(3)
                .playerCohort(true)
                .playerActions(List.of(PlayerAction.JOIN, PlayerAction.LOOKAT, PlayerAction.QUIT))
                .build();

        ExperimentPlan plan = new PlayerScenarioPlanner().plan(spec);
        List<ResolvedPlayerOperation> ops = plan.playerOperations();

        // 2 iterations x (3 joins + 3 lookats + 3 quits)
        assertEquals(18, ops.size());
        List<ResolvedPlayerOperation> firstCycle = ops.subList(0, 9);
        for (int i = 0; i < 3; i++) {
            assertEquals(PlayerAction.JOIN, firstCycle.get(i).action());
            assertEquals(PlayerAction.LOOKAT, firstCycle.get(i + 3).action());
            assertEquals(PlayerAction.QUIT, firstCycle.get(i + 6).action());
        }
        // Every member must be aimed at a distinct cohort position, not at itself.
        for (int i = 0; i < 3; i++) {
            ResolvedPlayerOperation join = firstCycle.get(i);
            ResolvedPlayerOperation look = firstCycle.get(i + 3);
            assertEquals(join.playerId(), look.playerId());
            assertTrue(look.x() != join.x() || look.z() != join.z(),
                    "LOOKAT must target another cohort member, not the observer's own position");
        }
        // Deterministic: identical spec produces identical resolved operations.
        assertEquals(ops, new PlayerScenarioPlanner().plan(spec).playerOperations());
    }

    @Test
    @DisplayName("churn ordering is unchanged when cohort mode is off")
    void nonCohortKeepsSequentialLifecycle() {
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.PLAYERS)
                .iterations(1)
                .loginsPerCycle(2)
                .playerCohort(false)
                .playerActions(List.of(PlayerAction.JOIN, PlayerAction.TELEPORT, PlayerAction.QUIT))
                .build();

        List<ResolvedPlayerOperation> ops = new PlayerScenarioPlanner().plan(spec).playerOperations();
        assertEquals(PlayerAction.JOIN, ops.get(0).action());
        assertEquals(PlayerAction.TELEPORT, ops.get(1).action());
        assertEquals(PlayerAction.QUIT, ops.get(2).action());
        assertNotEquals(ops.get(0).playerId(), ops.get(3).playerId());
        assertEquals(PlayerAction.JOIN, ops.get(3).action());
    }

    @Test
    @DisplayName("large cohorts scale the ring so neighbors never collapse below the fixture distance guard")
    void largeCohortKeepsNeighborSpacing() {
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.PLAYERS)
                .iterations(1)
                .loginsPerCycle(40)
                .playerCohort(true)
                .playerActions(List.of(PlayerAction.JOIN, PlayerAction.LOOKAT, PlayerAction.QUIT))
                .build();

        List<ResolvedPlayerOperation> ops = new PlayerScenarioPlanner().plan(spec).playerOperations();
        List<ResolvedPlayerOperation> joins = ops.stream()
                .filter(op -> op.action() == PlayerAction.JOIN).toList();
        assertEquals(40, joins.size());
        for (int i = 0; i < 40; i++) {
            ResolvedPlayerOperation next = joins.get((i + 1) % 40);
            double dx = next.x() - joins.get(i).x();
            double dz = next.z() - joins.get(i).z();
            assertTrue(Math.sqrt(dx * dx + dz * dz) >= 1.0,
                    "neighbor spacing collapsed below the fixture distance guard");
        }
        // LOOKAT targets must still land inside the 32-block gaze range.
        ResolvedPlayerOperation firstJoin = joins.get(0);
        ResolvedPlayerOperation firstLook = ops.stream()
                .filter(op -> op.action() == PlayerAction.LOOKAT && op.playerId().equals(firstJoin.playerId()))
                .findFirst().orElseThrow();
        double dx = firstLook.x() - firstJoin.x();
        double dz = firstLook.z() - firstJoin.z();
        assertTrue(Math.sqrt(dx * dx + dz * dz) < 32.0, "LOOKAT target outside the gaze range");
    }
}
