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
    /** Radius of the ring on which cohort players stand for mutual observation. */
    private static final double COHORT_RING_RADIUS = 3.0;
    /** Eye height offset used when aiming one cohort member at another. */
    private static final double LOOK_TARGET_EYE_OFFSET = 1.6;

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
            if (spec.playerCohort()) {
                operationIndex = planCohortIteration(spec, operations, operationIndex, iteration, actions);
            } else {
                operationIndex = planChurnIteration(spec, operations, operationIndex, iteration, actions);
            }
        }

        int estimatedTicks = spec.iterations() * (spec.holdTicks() + spec.settleTicks()
                + Math.max(1, operations.size() / spec.maxOperationsPerTick()));
        return ExperimentPlan.forPlayers(id, System.currentTimeMillis(), spec, operations, estimatedTicks);
    }

    /**
     * Legacy churn ordering: each login runs its full JOIN..QUIT sequence
     * before the next player joins, so at most one synthetic player is online
     * at a time.
     */
    private static int planChurnIteration(ExperimentSpec spec, List<ResolvedPlayerOperation> operations,
                                          int operationIndex, int iteration, List<PlayerAction> actions) {
        for (int login = 0; login < spec.loginsPerCycle(); login++) {
            UUID playerId = playerId(spec, iteration, login);
            String profileName = profileName(playerId);
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
        return operationIndex;
    }

    /**
     * Cohort ordering: every login in the iteration joins first, performs its
     * middle actions while the whole cohort is online, and only then quits.
     * Members stand on a small deterministic ring and LOOKAT aims each member
     * at the next member's eye position so observation-style mods trigger.
     */
    private static int planCohortIteration(ExperimentSpec spec, List<ResolvedPlayerOperation> operations,
                                           int operationIndex, int iteration, List<PlayerAction> actions) {
        int cohort = spec.loginsPerCycle();
        // Neighbor spacing on a ring is 2*r*sin(PI/N); keep it comfortably
        // above 0.5 so observation fixtures' minimum-distance guards cannot
        // trip on large cohorts (at r=3 the spacing collapses below 0.5 for
        // N >= 38). A single-member cohort has no neighbor, so skip the
        // scaling there.
        double ringRadius = cohort <= 1 ? COHORT_RING_RADIUS
                : Math.max(COHORT_RING_RADIUS, 0.6 / Math.sin(Math.PI / cohort));
        double[][] ring = new double[cohort][3];
        for (int login = 0; login < cohort; login++) {
            double angle = 2.0 * Math.PI * login / cohort;
            ring[login][0] = spec.centerX() + ringRadius * Math.cos(angle);
            ring[login][1] = 64.0;
            ring[login][2] = spec.centerZ() + ringRadius * Math.sin(angle);
        }

        for (int login = 0; login < cohort; login++) {
            operations.add(new ResolvedPlayerOperation(
                    iteration, operationIndex++, spec.dimension(),
                    playerId(spec, iteration, login), profileName(playerId(spec, iteration, login)),
                    PlayerAction.JOIN, ring[login][0], ring[login][1], ring[login][2]));
        }

        List<PlayerAction> middle = middleActions(actions);
        for (int login = 0; login < cohort; login++) {
            double[] gazeTarget = ring[(login + 1) % cohort];
            for (PlayerAction action : middle) {
                double[] coords = action == PlayerAction.LOOKAT
                        ? new double[]{gazeTarget[0], gazeTarget[1] + LOOK_TARGET_EYE_OFFSET, gazeTarget[2]}
                        : ring[login];
                operations.add(new ResolvedPlayerOperation(
                        iteration, operationIndex++, spec.dimension(),
                        playerId(spec, iteration, login), profileName(playerId(spec, iteration, login)),
                        action, coords[0], coords[1], coords[2]));
            }
        }

        for (int login = 0; login < cohort; login++) {
            operations.add(new ResolvedPlayerOperation(
                    iteration, operationIndex++, spec.dimension(),
                    playerId(spec, iteration, login), profileName(playerId(spec, iteration, login)),
                    PlayerAction.QUIT, ring[login][0], ring[login][1], ring[login][2]));
        }
        return operationIndex;
    }

    private static UUID playerId(ExperimentSpec spec, int iteration, int login) {
        return UUID.nameUUIDFromBytes(("heaphammer-player:" + spec.seed() + ":" + iteration + ":" + login)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String profileName(UUID playerId) {
        return "hh_test_" + playerId.toString().replace("-", "").substring(0, 12);
    }

    private static List<PlayerAction> middleActions(List<PlayerAction> actions) {
        List<PlayerAction> middle = new ArrayList<>();
        for (PlayerAction action : actions) {
            if (action != PlayerAction.JOIN && action != PlayerAction.QUIT) {
                middle.add(action);
            }
        }
        return middle;
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
