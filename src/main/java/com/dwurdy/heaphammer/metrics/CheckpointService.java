package com.dwurdy.heaphammer.metrics;

import com.dwurdy.heaphammer.detection.CleanupResult;
import com.dwurdy.heaphammer.detection.CleanupValidator;
import com.dwurdy.heaphammer.domain.Checkpoint;
import com.dwurdy.heaphammer.domain.CheckpointDiagnostics;
import com.dwurdy.heaphammer.domain.CheckpointPhase;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.domain.RunDiagnostics;
import com.dwurdy.heaphammer.diagnostics.ClassHistogram;
import com.dwurdy.heaphammer.diagnostics.EventMetricsSnapshot;
import com.dwurdy.heaphammer.diagnostics.RetentionSnapshot;
import com.dwurdy.heaphammer.diagnostics.WorldStoreSnapshot;
import com.dwurdy.heaphammer.platform.PlatformAdapter;
import com.dwurdy.heaphammer.domain.MetricSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service managing checkpoint capture across experiment lifecycle phases.
 */
public class CheckpointService {
    private final JvmMetricsCollector jvmMetrics;
    private final MinecraftMetricsCollector minecraftMetrics;
    private final CleanupValidator cleanupValidator;
    private final List<Checkpoint> checkpoints = new CopyOnWriteArrayList<>();
    private final DiagnosticCaptureService diagnosticCaptureService;
    private final List<CompletableFuture<DiagnosticCaptureService.CaptureResult>> pendingCaptures = new CopyOnWriteArrayList<>();
    private final List<String> diagnosticWarnings = new CopyOnWriteArrayList<>();
    private final AtomicLong runGeneration = new AtomicLong();
    private volatile ClassHistogram baselineHistogram;
    private volatile ClassHistogram finalHistogram;

    public CheckpointService(
            JvmMetricsCollector jvmMetrics,
            MinecraftMetricsCollector minecraftMetrics,
            CleanupValidator cleanupValidator
    ) {
        this.jvmMetrics = Objects.requireNonNull(jvmMetrics, "jvmMetrics must not be null");
        this.minecraftMetrics = Objects.requireNonNull(minecraftMetrics, "minecraftMetrics must not be null");
        this.cleanupValidator = Objects.requireNonNull(cleanupValidator, "cleanupValidator must not be null");
        this.diagnosticCaptureService = new DiagnosticCaptureService();
    }

    /** Configures optional evidence collectors for the next run. Collectors remain disabled by default. */
    public void configure(ExperimentSpec spec, PlatformAdapter platform) {
        runGeneration.incrementAndGet();
        pendingCaptures.clear();
        diagnosticWarnings.clear();
        baselineHistogram = null;
        finalHistogram = null;
        diagnosticCaptureService.configure(spec, platform);
    }

    public Checkpoint recordCheckpoint(CheckpointPhase phase, int iteration, String dimension, boolean explicitGc) {
        CleanupResult cleanupResult = cleanupValidator.validate(dimension);
        boolean cleanupValid = cleanupResult.isClean();

        MetricSample sample = new MetricSample(
                System.currentTimeMillis(),
                0L,
                jvmMetrics.getHeapUsedBytes(),
                jvmMetrics.getHeapCommittedBytes(),
                jvmMetrics.getHeapMaxBytes(),
                jvmMetrics.getNonHeapUsedBytes(),
                minecraftMetrics.getLoadedChunks(dimension),
                minecraftMetrics.getActiveEntities(dimension),
                jvmMetrics.getTotalGcCount(),
                jvmMetrics.getTotalGcTimeMs()
        );

        Checkpoint checkpoint = new Checkpoint(
                phase,
                iteration,
                System.currentTimeMillis(),
                sample,
                cleanupValid
        );

        checkpoints.add(checkpoint);
        long generation = runGeneration.get();
        CompletableFuture<DiagnosticCaptureService.CaptureResult> capture = diagnosticCaptureService
                .capture(phase, explicitGc);
        if (diagnosticCaptureService.isEnabled()) {
            int checkpointIndex = checkpoints.size() - 1;
            pendingCaptures.add(capture);
            capture.thenAccept(result -> {
                if (generation != runGeneration.get()) return;
                checkpoints.set(checkpointIndex, new Checkpoint(
                        phase, iteration, checkpoint.timestampEpochMs(), checkpoint.metrics(), checkpoint.cleanupValid(),
                        result.diagnostics()));
                diagnosticWarnings.addAll(result.warnings());
                if (phase == CheckpointPhase.BASELINE && result.histogram() != null) baselineHistogram = result.histogram();
                if (phase == CheckpointPhase.FINAL_CLEANUP && result.histogram() != null) finalHistogram = result.histogram();
            });
        }
        return checkpoint;
    }

    /** Completes without blocking the server thread once all queued evidence captures have settled. */
    public CompletableFuture<RunDiagnostics> awaitDiagnostics() {
        CompletableFuture<?>[] captures = pendingCaptures.toArray(new CompletableFuture<?>[0]);
        return CompletableFuture.allOf(captures).thenApply(ignored -> buildRunDiagnostics());
    }

    private RunDiagnostics buildRunDiagnostics() {
        RetentionSnapshot baselineRetention = null;
        RetentionSnapshot finalRetention = null;
        WorldStoreSnapshot baselineWorldStore = null;
        WorldStoreSnapshot finalWorldStore = null;
        EventMetricsSnapshot baselineEvents = null;
        EventMetricsSnapshot finalEvents = null;
        List<RetentionSnapshot> retentionSeries = new ArrayList<>();
        for (Checkpoint checkpoint : checkpoints) {
            CheckpointDiagnostics diagnostics = checkpoint.diagnostics();
            if (diagnostics.retention() != null) {
                retentionSeries.add(diagnostics.retention());
                if (checkpoint.phase() == CheckpointPhase.BASELINE) baselineRetention = diagnostics.retention();
                if (checkpoint.phase() == CheckpointPhase.FINAL_CLEANUP) finalRetention = diagnostics.retention();
            }
            if (diagnostics.worldStore() != null) {
                if (checkpoint.phase() == CheckpointPhase.BASELINE) baselineWorldStore = diagnostics.worldStore();
                if (checkpoint.phase() == CheckpointPhase.FINAL_CLEANUP) finalWorldStore = diagnostics.worldStore();
            }
            if (diagnostics.events() != null) {
                if (checkpoint.phase() == CheckpointPhase.BASELINE) baselineEvents = diagnostics.events();
                if (checkpoint.phase() == CheckpointPhase.FINAL_CLEANUP) finalEvents = diagnostics.events();
            }
        }
        com.dwurdy.heaphammer.diagnostics.HistogramDiff histogramDiff =
                finalHistogram == null ? null : finalHistogram.diff(baselineHistogram, 20);
        return new RunDiagnostics(
                baselineHistogram, finalHistogram, histogramDiff,
                baselineRetention, finalRetention, retentionSeries,
                baselineWorldStore, finalWorldStore,
                baselineEvents, finalEvents,
                new ArrayList<>(diagnosticWarnings));
    }

    public List<Checkpoint> getCheckpoints() {
        return Collections.unmodifiableList(new ArrayList<>(checkpoints));
    }

    public void clear() {
        runGeneration.incrementAndGet();
        checkpoints.clear();
        pendingCaptures.clear();
        diagnosticWarnings.clear();
        baselineHistogram = null;
        finalHistogram = null;
    }

    public void close() {
        diagnosticCaptureService.close();
    }
}
