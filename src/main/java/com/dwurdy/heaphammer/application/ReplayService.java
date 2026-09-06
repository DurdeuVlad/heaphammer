package com.dwurdy.heaphammer.application;

import com.dwurdy.heaphammer.domain.ExperimentId;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentReport;
import com.dwurdy.heaphammer.report.PlanStorage;
import com.dwurdy.heaphammer.report.ReportService;
import com.dwurdy.heaphammer.scenario.chunks.ChunkScenarioExecutor;
import com.dwurdy.heaphammer.scenario.chunks.ChunkWorkloadPlanner;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Service managing deterministic replay and configuration rerun (BR-002).
 */
public class ReplayService {
    private final ExperimentService experimentService;
    private final PlanStorage planStorage;
    private final ReportService reportService;
    private final ChunkWorkloadPlanner planner;

    public ReplayService(
            ExperimentService experimentService,
            PlanStorage planStorage,
            ReportService reportService
    ) {
        this.experimentService = Objects.requireNonNull(experimentService, "experimentService must not be null");
        this.planStorage = Objects.requireNonNull(planStorage, "planStorage must not be null");
        this.reportService = Objects.requireNonNull(reportService, "reportService must not be null");
        this.planner = new ChunkWorkloadPlanner();
    }

    /**
     * Replay executes the exact resolved operations from a saved plan.
     */
    public com.dwurdy.heaphammer.scenario.ScenarioExecutor replay(String planOrRunId) throws IOException {
        Optional<ExperimentPlan> planOpt;
        if ("last".equalsIgnoreCase(planOrRunId)) {
            planOpt = planStorage.loadLatestPlan();
            if (planOpt.isEmpty()) {
                Optional<ExperimentReport> reportOpt = reportService.loadLatestReport();
                if (reportOpt.isPresent()) {
                    planOpt = planStorage.loadPlan(reportOpt.get().runId());
                }
            }
        } else {
            planOpt = planStorage.loadPlan(ExperimentId.of(planOrRunId));
        }

        if (planOpt.isEmpty()) {
            throw new IllegalArgumentException("No saved plan found for identifier: " + planOrRunId);
        }

        ExperimentPlan plan = planOpt.get();
        // Create an execution plan instance with a fresh run ID referencing the original plan
        ExperimentPlan replayExecutionPlan = new ExperimentPlan(
                ExperimentId.generate(),
                System.currentTimeMillis(),
                plan.spec(),
                plan.operations(),
                plan.entityOperations(),
                plan.blockEntityOperations(),
                plan.estimatedDurationTicks(),
                plan.uniqueChunksCount()
        );

        return experimentService.start(replayExecutionPlan);
    }

    /**
     * Rerun regenerates a plan from the original configuration and seed through the current planner.
     */
    public com.dwurdy.heaphammer.scenario.ScenarioExecutor rerun(String runId) throws IOException {
        Optional<ExperimentReport> reportOpt;
        if ("last".equalsIgnoreCase(runId)) {
            reportOpt = reportService.loadLatestReport();
        } else {
            reportOpt = reportService.loadReport(ExperimentId.of(runId));
        }

        if (reportOpt.isEmpty()) {
            throw new IllegalArgumentException("No saved report found for rerun identifier: " + runId);
        }

        ExperimentReport report = reportOpt.get();
        ExperimentPlan newPlan = planner.plan(report.spec());
        return experimentService.start(newPlan);
    }
}
