package com.dwurdy.heaphammer.testmod;

import com.dwurdy.heaphammer.domain.DetectionClassification;
import com.dwurdy.heaphammer.domain.ExperimentReport;
import com.dwurdy.heaphammer.infrastructure.FileStorage;
import com.dwurdy.heaphammer.infrastructure.json.GsonCodec;
import com.dwurdy.heaphammer.report.ReportDiff;
import com.dwurdy.heaphammer.report.ReportDiffer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Test fixture manager for verifying matrix experiment reports, verdicts, and differentials.
 */
public class MatrixTestFixtureManager {

    public ExperimentReport loadReport(Path reportPath) throws IOException {
        String json = FileStorage.readString(reportPath);
        return GsonCodec.fromJson(json, ExperimentReport.class);
    }

    public ReportDiff compareReports(ExperimentReport baseline, ExperimentReport candidate) {
        return ReportDiffer.diff(baseline, candidate);
    }

    public List<Path> findReportsInDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.toString().endsWith(".json") && !p.toString().contains("diff"))
                    .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .collect(Collectors.toList());
        }
    }

    public boolean assertVerdict(ExperimentReport report, DetectionClassification expected) {
        return report.detection().classification() == expected;
    }

    public boolean assertSlopeGreaterThan(ExperimentReport report, double minSlopeMbPerCycle) {
        double slopeMb = report.detection().slopeBytesPerCycle() / (1024.0 * 1024.0);
        return slopeMb >= minSlopeMbPerCycle;
    }

    public boolean assertSlopeLessThanOrEqualTo(ExperimentReport report, double maxSlopeMbPerCycle) {
        double slopeMb = report.detection().slopeBytesPerCycle() / (1024.0 * 1024.0);
        return slopeMb <= maxSlopeMbPerCycle;
    }
}
