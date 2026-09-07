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
        if (!Files.exists(reportsDir)) return List.of();
        try (Stream<Path> stream = Files.list(reportsDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }
    }

    public static String buildCanonicalCommand(ExperimentSpec spec) {
        return String.format(Locale.ROOT,
                "/hh run chunks --seed=%d --center=%d,%d --radius=%d --iterations=%d --batch=%d --strategy=%s --warmup=%d --hold=%d --settle=%d%s",
                spec.seed(), spec.centerX(), spec.centerZ(), spec.radius(), spec.iterations(), spec.batchSize(),
                spec.strategy().toLowerCase(Locale.ROOT), spec.warmupIterations(), spec.holdTicks(), spec.settleTicks(),
                spec.explicitGc() ? " --explicit-gc=true" : ""
        );
    }

    public String formatSummary(ExperimentReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== HeapHammer Report: ").append(report.runId().value()).append(" ===\n");
        sb.append("Status: ").append(report.status()).append(" | Verdict: ").append(report.detection().classification()).append("\n");
        sb.append("Duration: ").append(report.durationMs() / 1000).append("s | Checkpoints: ").append(report.checkpoints().size()).append("\n");
        sb.append("Slope: ").append(String.format(Locale.ROOT, "%.2f MB/cycle (R² = %.2f)", report.detection().slopeMbPerCycle(), report.detection().rSquared())).append("\n");
        sb.append("Net Delta: ").append(String.format(Locale.ROOT, "%.2f MB", report.detection().netDeltaMb())).append("\n");
        sb.append("Rationale: ").append(report.detection().rationale()).append("\n");
        sb.append("Canonical Replay: ").append(report.canonicalCommand()).append("\n");
        return sb.toString();
    }
}
