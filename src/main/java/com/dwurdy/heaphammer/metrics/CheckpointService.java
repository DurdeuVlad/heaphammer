package com.dwurdy.heaphammer.metrics;

import com.dwurdy.heaphammer.detection.CleanupResult;
import com.dwurdy.heaphammer.detection.CleanupValidator;
import com.dwurdy.heaphammer.domain.Checkpoint;
import com.dwurdy.heaphammer.domain.CheckpointPhase;
import com.dwurdy.heaphammer.domain.MetricSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service managing checkpoint capture across experiment lifecycle phases.
 */
public class CheckpointService {
    private final JvmMetricsCollector jvmMetrics;
    private final MinecraftMetricsCollector minecraftMetrics;
    private final CleanupValidator cleanupValidator;
    private final List<Checkpoint> checkpoints = new CopyOnWriteArrayList<>();

    public CheckpointService(
            JvmMetricsCollector jvmMetrics,
            MinecraftMetricsCollector minecraftMetrics,
            CleanupValidator cleanupValidator
    ) {
        this.jvmMetrics = Objects.requireNonNull(jvmMetrics, "jvmMetrics must not be null");
        this.minecraftMetrics = Objects.requireNonNull(minecraftMetrics, "minecraftMetrics must not be null");
        this.cleanupValidator = Objects.requireNonNull(cleanupValidator, "cleanupValidator must not be null");
    }

    public Checkpoint recordCheckpoint(CheckpointPhase phase, int iteration, String dimension, boolean explicitGc) {
        if (explicitGc) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

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
        return checkpoint;
    }

    public List<Checkpoint> getCheckpoints() {
        return Collections.unmodifiableList(new ArrayList<>(checkpoints));
    }

    public void clear() {
        checkpoints.clear();
    }
}
