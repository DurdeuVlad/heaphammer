package com.dwurdy.heaphammer.report;

import com.dwurdy.heaphammer.domain.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Domain engine for comparing two ExperimentReport instances (Section 27).
 */
public class ReportDiffer {

    public static ReportDiff diff(ExperimentReport reportA, ExperimentReport reportB) {
        if (reportA == null || reportB == null) {
            throw new IllegalArgumentException("Reports to compare must not be null");
        }

        List<String> warnings = new ArrayList<>();

        // 1. Environment Comparison
        boolean envMatched = true;
        EnvironmentFingerprint envA = reportA.environment();
        EnvironmentFingerprint envB = reportB.environment();

        List<String> envDiffs = new ArrayList<>();
        if (!envA.minecraftVersion().equals(envB.minecraftVersion())) {
            envDiffs.add(String.format(Locale.ROOT, "MC %s vs %s", envA.minecraftVersion(), envB.minecraftVersion()));
            envMatched = false;
        }
        if (!envA.loaderVersion().equals(envB.loaderVersion())) {
            envDiffs.add(String.format(Locale.ROOT, "Loader %s vs %s", envA.loaderVersion(), envB.loaderVersion()));
            envMatched = false;
        }
        if (envA.installedMods().size() != envB.installedMods().size()) {
            envDiffs.add(String.format(Locale.ROOT, "Mod count %d vs %d", envA.installedMods().size(), envB.installedMods().size()));
            envMatched = false;
        }

        String envComparison = envMatched ? "IDENTICAL" : "MISMATCH (" + String.join(", ", envDiffs) + ")";
        if (!envMatched) {
            warnings.add("Environment mismatch detected between runs: " + envComparison);
        }

        // 2. Scenario & Workload Check
        if (!reportA.spec().scenarioId().equals(reportB.spec().scenarioId())) {
            warnings.add(String.format(Locale.ROOT, "Different scenarios: %s vs %s",
                    reportA.spec().scenarioId(), reportB.spec().scenarioId()));
        }
        if (reportA.spec().iterations() != reportB.spec().iterations()) {
            warnings.add(String.format(Locale.ROOT, "Iteration counts differ: %d vs %d",
                    reportA.spec().iterations(), reportB.spec().iterations()));
        }

        // 3. Heap Metrics Extraction
        long initialHeapA = extractInitialHeap(reportA);
        long initialHeapB = extractInitialHeap(reportB);
        long finalHeapA = extractFinalHeap(reportA);
        long finalHeapB = extractFinalHeap(reportB);

        long netDeltaA = finalHeapA - initialHeapA;
        long netDeltaB = finalHeapB - initialHeapB;
        long netDeltaDiff = netDeltaB - netDeltaA;

        // 4. Slopes and Classifications
        double slopeA = reportA.detection().slopeBytesPerCycle();
        double slopeB = reportB.detection().slopeBytesPerCycle();
        double slopeDiff = slopeB - slopeA;

        DetectionClassification classA = reportA.detection().classification();
        DetectionClassification classB = reportB.detection().classification();
        boolean classChanged = (classA != classB);

        // 5. Generate Summary
        double slopeAMb = slopeA / (1024.0 * 1024.0);
        double slopeBMb = slopeB / (1024.0 * 1024.0);
        double slopeDiffMb = slopeDiff / (1024.0 * 1024.0);
        double netDeltaDiffMb = netDeltaDiff / (1024.0 * 1024.0);

        String summary = String.format(Locale.ROOT,
                "Comparison %s vs %s: Net Delta Diff = %+.2f MB, Slope Diff = %+.2f MB/cycle (%s -> %s).",
                reportA.runId().value(), reportB.runId().value(),
                netDeltaDiffMb, slopeDiffMb, classA, classB);

        return new ReportDiff(
                reportA.runId(),
                reportB.runId(),
                envMatched,
                envComparison,
                initialHeapA,
                initialHeapB,
                finalHeapA,
                finalHeapB,
                netDeltaA,
                netDeltaB,
                netDeltaDiff,
                slopeA,
                slopeB,
                slopeDiff,
                classA,
                classB,
                classChanged,
                summary,
                warnings
        );
    }

    private static long extractInitialHeap(ExperimentReport report) {
        for (Checkpoint cp : report.checkpoints()) {
            if (cp.phase() == CheckpointPhase.BASELINE) {
                return cp.metrics().heapUsedBytes();
            }
        }
        if (!report.checkpoints().isEmpty()) {
            return report.checkpoints().get(0).metrics().heapUsedBytes();
        }
        return 0L;
    }

    private static long extractFinalHeap(ExperimentReport report) {
        if (!report.checkpoints().isEmpty()) {
            return report.checkpoints().get(report.checkpoints().size() - 1).metrics().heapUsedBytes();
        }
        return 0L;
    }
}
