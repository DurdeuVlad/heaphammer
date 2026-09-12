package com.dwurdy.heaphammer.report;

import com.dwurdy.heaphammer.domain.*;
import com.dwurdy.heaphammer.infrastructure.FileStorage;
import com.dwurdy.heaphammer.infrastructure.json.GsonCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Service managing creation, persistence, listing, and inspection of experiment reports (BR-011).
 */
public class ReportService {
    private final Path reportsDir;

    public ReportService(Path baseDir) {
        this.reportsDir = Objects.requireNonNull(baseDir, "baseDir must not be null").resolve("reports");
    }

    public Path saveReport(ExperimentReport report) throws IOException {
        Path target = reportsDir.resolve(report.runId().value() + ".json").normalize();
        if (!target.startsWith(reportsDir.normalize())) {
            throw new SecurityException("Path traversal attempt detected in report ID: " + report.runId());
        }
        String json = GsonCodec.toJson(report);
        FileStorage.writeStringAtomic(target, json);
        return target;
    }

    public Optional<ExperimentReport> loadReport(ExperimentId runId) throws IOException {
        Path target = reportsDir.resolve(runId.value() + ".json").normalize();
        if (!target.startsWith(reportsDir.normalize())) {
            throw new SecurityException("Path traversal attempt detected in report ID: " + runId);
        }
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        String json = FileStorage.readString(target);
        return Optional.of(GsonCodec.fromJson(json, ExperimentReport.class));
    }

    public Optional<ExperimentReport> loadLatestReport() throws IOException {
        if (!Files.exists(reportsDir)) return Optional.empty();
        try (Stream<Path> stream = Files.list(reportsDir)) {
            Optional<Path> latest = stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .max(Comparator.comparingLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }));
            if (latest.isPresent()) {
                String json = FileStorage.readString(latest.get());
                return Optional.of(GsonCodec.fromJson(json, ExperimentReport.class));
            }
        }
        return Optional.empty();
    }

    public List<String> listReportIds() throws IOException {
        if (!Files.exists(reportsDir)) return Collections.emptyList();
        try (Stream<Path> stream = Files.list(reportsDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .sorted(Comparator.reverseOrder())
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    public static String buildCanonicalCommand(ExperimentSpec spec) {
        String scenario = spec.scenarioId().value();
        StringBuilder command = new StringBuilder(String.format(Locale.ROOT,
                "/hh run %s --seed=%d --center=%d,%d --radius=%d --iterations=%d --batch=%d --strategy=%s --warmup=%d --hold=%d --settle=%d",
                scenario, spec.seed(), spec.centerX(), spec.centerZ(), spec.radius(), spec.iterations(), spec.batchSize(),
                spec.strategy().toLowerCase(Locale.ROOT), spec.warmupIterations(), spec.holdTicks(), spec.settleTicks()));
        if (spec.explicitGc()) command.append(" --explicit-gc=true");
        if (spec.entityProfile() != com.dwurdy.heaphammer.domain.EntityWorkloadProfile.TRANSIENT) {
            command.append(" --profile=").append(spec.entityProfile().name().toLowerCase(Locale.ROOT));
        }
        if (spec.scenarioId().equals(com.dwurdy.heaphammer.domain.ScenarioId.PLAYERS)) {
            command.append(" --logins-per-cycle=").append(spec.loginsPerCycle());
            command.append(" --actions=").append(spec.playerActions().stream()
                    .map(action -> action.name().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.joining(",")));
        }
        if (spec.isSoak()) {
            command.append(" --duration=").append(spec.durationSeconds()).append("s")
                    .append(" --interval=").append(spec.intervalSeconds()).append("s");
        }
        if (!spec.diagnosticCollectors().isEmpty()) {
            command.append(" --diagnostics=").append(spec.diagnosticCollectors().stream()
                    .map(collector -> collector.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                    .collect(java.util.stream.Collectors.joining(",")));
        }
        if (!spec.trackedClasses().isEmpty()) {
            command.append(" --track-classes=").append(String.join(",", spec.trackedClasses()));
        }
        return command.toString();
    }

    public String formatSummary(ExperimentReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== HeapHammer Report: ").append(report.runId().value()).append(" ===\n");
        sb.append("Status: ").append(report.status()).append(" | Verdict: ").append(report.detection().classification()).append("\n");
        sb.append("Duration: ").append(report.durationMs() / 1000).append("s | Checkpoints: ").append(report.checkpoints().size()).append("\n");
        sb.append("Slope: ").append(String.format(Locale.ROOT, "%.2f MB/cycle (RÂ² = %.2f)", report.detection().slopeMbPerCycle(), report.detection().rSquared())).append("\n");
        sb.append("Net Delta: ").append(String.format(Locale.ROOT, "%.2f MB", report.detection().netDeltaMb())).append("\n");
        if (report.evidence().histogramDiff() != null) {
            sb.append("Histogram Growth: ").append(String.format(Locale.ROOT, "%+.2f MB\n",
                    report.evidence().histogramDiff().totalDeltaBytesMb()));
        }
        if (report.evidence().baselineRetention() != null && report.evidence().finalRetention() != null) {
            sb.append("Retention Census: ").append(report.evidence().baselineRetention().entries().size())
                    .append(" classes at baseline -> ").append(report.evidence().finalRetention().entries().size())
                    .append(" classes at final\n");
        }
        if (report.evidence().baselineWorldStore() != null && report.evidence().finalWorldStore() != null) {
            sb.append("World Store: ").append(report.evidence().finalWorldStore().totalEntityCount()
                    - report.evidence().baselineWorldStore().totalEntityCount()).append(" entity delta\n");
        }
        sb.append("Rationale: ").append(report.detection().rationale()).append("\n");
        sb.append("Canonical Replay: ").append(report.canonicalCommand()).append("\n");
        return sb.toString();
    }
}
