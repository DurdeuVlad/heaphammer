package com.dwurdy.heaphammer.detection;

import com.dwurdy.heaphammer.domain.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.dwurdy.heaphammer.diagnostics.RetentionClassEntry;
import com.dwurdy.heaphammer.diagnostics.RetentionSnapshot;
import com.dwurdy.heaphammer.domain.RunDiagnostics;

/**
 * Retained-heap trend analysis, plateau detection, and leak heuristic engine (BR-004, Section 6, Section 27).
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
                    0.0, 0.0, 0.0, 0L, false,
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
                    0.0, 0.0, 0.0, 0L, false,
                    "Insufficient post-warmup evaluation checkpoints (found " + evalPoints.size() + ", requires >= 2)."
            );
        }

        // 3. Verify cleanup integrity
        for (Checkpoint cp : evalPoints) {
            if (!cp.cleanupValid()) {
                return new DetectionResult(
                        DetectionClassification.CLEANUP_FAILED,
                        1.0, 0.0, 0.0, 0L, false,
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

        // 5. Check for Plateau Behavior (Bounded warming cache)
        boolean plateau = detectPlateau(x, y, slopeThresholdBytes);
        if (plateau) {
            double confidence = 0.92;
            String rationale = String.format(Locale.ROOT,
                    "PASS (PLATEAU): Bounded warming pattern detected; early growth plateaued into stable retention (net delta = +%.2f MB).",
                    netDeltaMb);
            return new DetectionResult(
                    DetectionClassification.PASS,
                    confidence, slope, rSquared, netDeltaBytes, true, rationale
            );
        }

        // 6. Evaluate thresholds and classify
        if (slope > slopeThresholdBytes && rSquared >= minRSquared && netDeltaBytes > 0) {
            // Statistical confidence scoring
            double confidence;
            if (rSquared >= 0.85 && n >= 4) {
                confidence = Math.min(0.99, rSquared);
            } else {
                confidence = Math.max(0.65, Math.min(0.85, rSquared));
            }

            String rationale = String.format(Locale.ROOT,
                    "SUSPICIOUS: Monotonic retained heap growth detected (+%.2f MB/cycle, R² = %.2f, net delta = +%.2f MB).",
                    slopeMb, rSquared, netDeltaMb);
            return new DetectionResult(
                    DetectionClassification.SUSPICIOUS,
                    confidence, slope, rSquared, netDeltaBytes, false, rationale
            );
        }

        if (slope <= slopeThresholdBytes * 0.4 || netDeltaBytes <= 0) {
            double confidence = 0.90;
            String rationale = String.format(Locale.ROOT,
                    "PASS: Retained heap stable across cycles (slope = %.2f MB/cycle, net delta = %.2f MB).",
                    slopeMb, netDeltaMb);
            return new DetectionResult(
                    DetectionClassification.PASS,
                    confidence, slope, rSquared, netDeltaBytes, false, rationale
            );
        }

        // Intermediate / noisy variance without clear trend
        return new DetectionResult(
                DetectionClassification.INCONCLUSIVE,
                0.50, slope, rSquared, netDeltaBytes, false,
                String.format(Locale.ROOT,
                        "INCONCLUSIVE: Heap variance observed (slope = %.2f MB/cycle, R² = %.2f, net delta = %.2f MB) without definitive linear trend.",
                        slopeMb, rSquared, netDeltaMb)
        );
    }

    /**
     * Adds weak-reference retention evidence to the ordinary heap verdict. A positive,
     * well-fitted live-reference trend is treated as a stronger signal than GC-noisy heap
     * samples, while histogram/world/event evidence remains attribution-only.
     */
    public DetectionResult analyze(ExperimentSpec spec, List<Checkpoint> checkpoints, RunDiagnostics evidence) {
        DetectionResult heapResult = analyze(spec, checkpoints);
        if (evidence == null || evidence.retentionSeries().size() < 3
                || heapResult.classification() == DetectionClassification.CLEANUP_FAILED) {
            return heapResult;
        }

        List<RetentionSnapshot> series = evidence.retentionSeries();
        double[] x = new double[series.size()];
        double[] y = new double[series.size()];
        for (int i = 0; i < series.size(); i++) {
            x[i] = i;
            y[i] = series.get(i).entries().values().stream()
                    .mapToLong(RetentionClassEntry::liveCount).sum();
        }
        LinearRegression retentionRegression = LinearRegression.compute(x, y);
        if (retentionRegression.slope() > 0.0 && retentionRegression.rSquared() >= minRSquared) {
            String rationale = heapResult.rationale() + String.format(Locale.ROOT,
                    " Weak-reference retention census also grew by %.2f live objects/checkpoint (R² = %.2f).",
                    retentionRegression.slope(), retentionRegression.rSquared());
            return new DetectionResult(
                    DetectionClassification.SUSPICIOUS,
                    Math.max(heapResult.confidence(), Math.min(0.99, retentionRegression.rSquared())),
                    heapResult.slopeBytesPerCycle(),
                    heapResult.rSquared(),
                    heapResult.netDeltaBytes(),
                    false,
                    rationale
            );
        }
        return heapResult;
    }

    private boolean detectPlateau(double[] x, double[] y, double threshold) {
        int n = x.length;
        if (n < 4) {
            return false;
        }

        int mid = n / 2;
        double[] xFirst = new double[mid];
        double[] yFirst = new double[mid];
        System.arraycopy(x, 0, xFirst, 0, mid);
        System.arraycopy(y, 0, yFirst, 0, mid);

        int secondLen = n - mid;
        double[] xSecond = new double[secondLen];
        double[] ySecond = new double[secondLen];
        System.arraycopy(x, mid, xSecond, 0, secondLen);
        System.arraycopy(y, mid, ySecond, 0, secondLen);

        LinearRegression regFirst = LinearRegression.compute(xFirst, yFirst);
        LinearRegression regSecond = LinearRegression.compute(xSecond, ySecond);

        // Early half has positive warming slope, second half levels off to near zero / below 25% threshold
        return regFirst.slope() > threshold * 0.4 && regSecond.slope() <= threshold * 0.25;
    }
}
