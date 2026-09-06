package com.dwurdy.heaphammer.detection;

import com.dwurdy.heaphammer.domain.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Retained-heap trend analysis and leak heuristic engine (BR-004, Section 6).
 */
public class TrendAnalyzer {
    // Default threshold: 5 MB/cycle persistent retained growth
    public static final double DEFAULT_SLOPE_THRESHOLD_BYTES = 5.0 * 1024.0 * 1024.0;
    // Default minimum R² for high confidence linearity
    public static final double DEFAULT_MIN_R_SQUARED = 0.70;

    private final double slopeThresholdBytes;
    private final double minRSquared;

    public TrendAnalyzer() {
        this(DEFAULT_SLOPE_THRESHOLD_BYTES, DEFAULT_MIN_R_SQUARED);
    }

    public TrendAnalyzer(double slopeThresholdBytes, double minRSquared) {
        this.slopeThresholdBytes = slopeThresholdBytes;
        this.minRSquared = minRSquared;
    }

    public DetectionResult analyze(ExperimentSpec spec, List<Checkpoint> checkpoints) {
        if (checkpoints == null || checkpoints.isEmpty()) {
            return new DetectionResult(
                    DetectionClassification.INCONCLUSIVE,
                    0.0, 0.0, 0.0, 0L,
                    "No checkpoints available for trend analysis."
            );
        }

        // 1. Locate baseline checkpoint
        Checkpoint baseline = null;
        for (Checkpoint cp : checkpoints) {
            if (cp.phase() == CheckpointPhase.BASELINE) {
                baseline = cp;
                break;
            }
        }
        long baselineHeap = (baseline != null) ? baseline.metrics().heapUsedBytes() : checkpoints.get(0).metrics().heapUsedBytes();

        // 2. Filter post-cleanup checkpoints excluding warmup iterations
        int warmup = spec != null ? spec.warmupIterations() : 0;
        List<Checkpoint> evalPoints = new ArrayList<>();
        for (Checkpoint cp : checkpoints) {
            if (cp.phase() == CheckpointPhase.ITERATION_CLEANUP || cp.phase() == CheckpointPhase.FINAL_CLEANUP) {
                if (cp.iteration() >= warmup) {
                    evalPoints.add(cp);
                }
            }
        }

        if (evalPoints.size() < 2) {
            return new DetectionResult(
                    DetectionClassification.INCONCLUSIVE,
                    0.0, 0.0, 0.0, 0L,
                    "Insufficient post-warmup evaluation checkpoints (found " + evalPoints.size() + ", requires >= 2)."
            );
        }

        // 3. Verify cleanup integrity
        for (Checkpoint cp : evalPoints) {
            if (!cp.cleanupValid()) {
                return new DetectionResult(
                        DetectionClassification.CLEANUP_FAILED,
                        1.0, 0.0, 0.0, 0L,
                        "Cleanup validation failed on iteration " + cp.iteration() + "; cannot measure retained heap reliably."
                );
            }
        }

        // 4. Compute regression on retained heap
        int n = evalPoints.size();
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = evalPoints.get(i).iteration();
            y[i] = evalPoints.get(i).metrics().heapUsedBytes();
        }

        LinearRegression regression = LinearRegression.compute(x, y);
        double slope = regression.slope();
        double rSquared = regression.rSquared();
        long finalHeap = (long) y[n - 1];
        long netDeltaBytes = finalHeap - baselineHeap;

        double slopeMb = slope / (1024.0 * 1024.0);
        double netDeltaMb = netDeltaBytes / (1024.0 * 1024.0);

        // 5. Evaluate thresholds and classify
        if (slope > slopeThresholdBytes && rSquared >= minRSquared && netDeltaBytes > 0) {
            double confidence = Math.min(0.99, Math.max(0.70, rSquared));
            String rationale = String.format(Locale.ROOT,
                    "SUSPICIOUS: Monotonic retained heap growth detected (+%.2f MB/cycle, R² = %.2f, net delta = +%.2f MB).",
                    slopeMb, rSquared, netDeltaMb);
            return new DetectionResult(
                    DetectionClassification.SUSPICIOUS,
                    confidence, slope, rSquared, netDeltaBytes, rationale
            );
        }

        if (slope <= slopeThresholdBytes * 0.4 || netDeltaBytes <= 0) {
            double confidence = 0.90;
            String rationale = String.format(Locale.ROOT,
                    "PASS: Retained heap stable across cycles (slope = %.2f MB/cycle, net delta = %.2f MB).",
                    slopeMb, netDeltaMb);
            return new DetectionResult(
                    DetectionClassification.PASS,
                    confidence, slope, rSquared, netDeltaBytes, rationale
            );
        }

        // Intermediate / noisy variance without clear trend
        return new DetectionResult(
                DetectionClassification.INCONCLUSIVE,
                0.50, slope, rSquared, netDeltaBytes,
                String.format(Locale.ROOT,
                        "INCONCLUSIVE: Heap variance observed (slope = %.2f MB/cycle, R² = %.2f, net delta = %.2f MB) without definitive linear trend.",
                        slopeMb, rSquared, netDeltaMb)
        );
    }
}
