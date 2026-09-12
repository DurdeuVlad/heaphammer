package com.dwurdy.heaphammer.report;

import com.dwurdy.heaphammer.domain.*;
import com.dwurdy.heaphammer.diagnostics.EventMetricsSnapshot;
import com.dwurdy.heaphammer.diagnostics.HistogramDiff;
import com.dwurdy.heaphammer.diagnostics.HistogramDiffEntry;
import com.dwurdy.heaphammer.diagnostics.RetentionSnapshot;
import com.dwurdy.heaphammer.diagnostics.WorldStoreSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        double rSquaredA = reportA.detection().rSquared();
        double rSquaredB = reportB.detection().rSquared();
        double rSquaredDifference = rSquaredB - rSquaredA;

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
                rSquaredA,
                rSquaredB,
                rSquaredDifference,
                classA,
                classB,
                classChanged,
                summary,
                warnings,
                diffEvidence(reportA.evidence(), reportB.evidence())
        );
    }

    private static ReportEvidenceDiff diffEvidence(RunDiagnostics evidenceA, RunDiagnostics evidenceB) {
        if (evidenceA == null) evidenceA = RunDiagnostics.EMPTY;
        if (evidenceB == null) evidenceB = RunDiagnostics.EMPTY;

        Map<String, Long> classA = histogramGrowth(evidenceA.histogramDiff());
        Map<String, Long> classB = histogramGrowth(evidenceB.histogramDiff());
        Map<String, Long> classDifference = subtract(classB, classA);

        Map<String, Long> retainedA = retainedGrowth(evidenceA);
        Map<String, Long> retainedB = retainedGrowth(evidenceB);
        Map<String, Long> retainedDifference = subtract(retainedB, retainedA);

        long worldBytesA = worldGrowthBytes(evidenceA);
        long worldBytesB = worldGrowthBytes(evidenceB);
        long worldEntitiesA = worldGrowthEntities(evidenceA);
        long worldEntitiesB = worldGrowthEntities(evidenceB);

        Map<String, Long> eventsA = eventGrowth(evidenceA);
        Map<String, Long> eventsB = eventGrowth(evidenceB);

        return new ReportEvidenceDiff(
                classDifference,
                retainedDifference,
                worldBytesB - worldBytesA,
                worldEntitiesB - worldEntitiesA,
                subtract(eventsB, eventsA)
        );
    }

    private static Map<String, Long> histogramGrowth(HistogramDiff diff) {
        if (diff == null) return Collections.emptyMap();
        Map<String, Long> result = new LinkedHashMap<>();
        for (HistogramDiffEntry entry : diff.topGrowingClasses()) {
            result.put(entry.className(), entry.deltaBytes());
        }
        return result;
    }

    private static Map<String, Long> retainedGrowth(RunDiagnostics evidence) {
        RetentionSnapshot baseline = evidence.baselineRetention();
        RetentionSnapshot current = evidence.finalRetention();
        if (baseline == null || current == null) return Collections.emptyMap();
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, com.dwurdy.heaphammer.diagnostics.RetentionClassEntry> entry : current.entries().entrySet()) {
            long base = baseline.entries().containsKey(entry.getKey())
                    ? baseline.entries().get(entry.getKey()).liveCount() : 0L;
            result.put(entry.getKey(), entry.getValue().liveCount() - base);
        }
        return result;
    }

    private static long worldGrowthBytes(RunDiagnostics evidence) {
        WorldStoreSnapshot baseline = evidence.baselineWorldStore();
        WorldStoreSnapshot current = evidence.finalWorldStore();
        if (baseline == null || current == null) return 0L;
        return current.totalRegionBytes() - baseline.totalRegionBytes();
    }

    private static long worldGrowthEntities(RunDiagnostics evidence) {
        WorldStoreSnapshot baseline = evidence.baselineWorldStore();
        WorldStoreSnapshot current = evidence.finalWorldStore();
        if (baseline == null || current == null) return 0L;
        return current.totalEntityCount() - baseline.totalEntityCount();
    }

    private static Map<String, Long> eventGrowth(RunDiagnostics evidence) {
        EventMetricsSnapshot baseline = evidence.baselineEvents();
        EventMetricsSnapshot current = evidence.finalEvents();
        if (baseline == null || current == null) return Collections.emptyMap();
        return subtract(current.dispatchCounts(), baseline.dispatchCounts());
    }

    private static Map<String, Long> subtract(Map<String, Long> current, Map<String, Long> baseline) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String key : union(current, baseline)) {
            result.put(key, current.getOrDefault(key, 0L) - baseline.getOrDefault(key, 0L));
        }
        return result;
    }

    private static List<String> union(Map<String, Long> first, Map<String, Long> second) {
        List<String> keys = new ArrayList<>(first.keySet());
        for (String key : second.keySet()) {
            if (!keys.contains(key)) keys.add(key);
        }
        return keys;
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
