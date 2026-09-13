package com.dwurdy.heaphammer.metrics;

import com.dwurdy.heaphammer.diagnostics.ClassHistogram;
import com.dwurdy.heaphammer.diagnostics.ClassHistogramCollector;
import com.dwurdy.heaphammer.diagnostics.EventMetricsSnapshot;
import com.dwurdy.heaphammer.diagnostics.RetentionSnapshot;
import com.dwurdy.heaphammer.diagnostics.RetentionTracker;
import com.dwurdy.heaphammer.diagnostics.WorldStoreSnapshot;
import com.dwurdy.heaphammer.domain.CheckpointDiagnostics;
import com.dwurdy.heaphammer.domain.CheckpointPhase;
import com.dwurdy.heaphammer.domain.DiagnosticCollector;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.platform.PlatformAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs opt-in, potentially expensive evidence collection away from the server tick thread.
 * The platform hooks supplied here must themselves be thread-safe and must not retain live game objects.
 */
public final class DiagnosticCaptureService implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticCaptureService.class);

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "heaphammer-diagnostics");
        thread.setDaemon(true);
        return thread;
    });
    private final ClassHistogramCollector histogramCollector;
    private final RetentionTracker retentionTracker = new RetentionTracker();
    private volatile Configuration configuration = Configuration.disabled();

    public DiagnosticCaptureService() {
        this(new ClassHistogramCollector());
    }

    DiagnosticCaptureService(ClassHistogramCollector histogramCollector) {
        this.histogramCollector = Objects.requireNonNull(histogramCollector, "histogramCollector must not be null");
    }

    public void configure(ExperimentSpec spec, PlatformAdapter platform) {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(platform, "platform must not be null");
        retentionTracker.clear();
        retentionTracker.setTrackedClasses(spec.trackedClasses());
        if (spec.diagnosticCollectors().contains(DiagnosticCollector.RETENTION)) {
            platform.getRetentionObservationPort().ifPresent(port -> port.attach(retentionTracker));
        }
        configuration = new Configuration(
                EnumSet.copyOf(spec.diagnosticCollectors().isEmpty()
                        ? EnumSet.noneOf(DiagnosticCollector.class)
                        : EnumSet.copyOf(spec.diagnosticCollectors())),
                platform,
                spec.trackedClasses()
        );
    }

    public CompletableFuture<CaptureResult> capture(CheckpointPhase phase, boolean explicitGc) {
        Configuration config = configuration;
        if (config.collectors.isEmpty()) {
            return CompletableFuture.completedFuture(CaptureResult.empty());
        }
        return CompletableFuture.supplyAsync(() -> captureNow(config, phase, explicitGc), worker);
    }

    public boolean isEnabled() {
        return !configuration.collectors.isEmpty();
    }

    private CaptureResult captureNow(Configuration config, CheckpointPhase phase, boolean explicitGc) {
        List<String> warnings = new ArrayList<>();
        if (explicitGc) {
            try {
                System.gc();
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                warnings.add("Diagnostic GC settle interrupted");
            }
        }

        ClassHistogram histogram = null;
        if (config.collectors.contains(DiagnosticCollector.HISTOGRAM)
                && (phase == CheckpointPhase.BASELINE || phase == CheckpointPhase.FINAL_CLEANUP)) {
            try {
                histogram = histogramCollector.capture(50).orElse(null);
                if (histogram == null) warnings.add("JVM class histogram unavailable");
            } catch (RuntimeException e) {
                warnings.add("JVM class histogram failed: " + e.getMessage());
                LOGGER.debug("Histogram capture failed", e);
            }
        }

        RetentionSnapshot retention = null;
        if (config.collectors.contains(DiagnosticCollector.RETENTION)) {
            retention = retentionTracker.snapshot();
        }

        WorldStoreSnapshot worldStore = null;
        if (config.collectors.contains(DiagnosticCollector.WORLD_STORE)) {
            try {
                worldStore = config.platform.getWorldStoreMetricsPort().map(port -> port.capture()).orElse(null);
                if (worldStore == null) warnings.add("World-store metrics unsupported by platform");
            } catch (RuntimeException e) {
                warnings.add("World-store metrics failed: " + e.getMessage());
            }
        }

        EventMetricsSnapshot events = null;
        if (config.collectors.contains(DiagnosticCollector.EVENT_METRICS)) {
            try {
                events = config.platform.getEventMetricsPort().map(port -> port.snapshot()).orElse(null);
                if (events == null) warnings.add("Event metrics unsupported by platform");
            } catch (RuntimeException e) {
                warnings.add("Event metrics failed: " + e.getMessage());
            }
        }

        return new CaptureResult(new CheckpointDiagnostics(retention, worldStore, events), histogram, warnings);
    }

    public RetentionTracker retentionTracker() {
        return retentionTracker;
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }

    private record Configuration(Set<DiagnosticCollector> collectors, PlatformAdapter platform, List<String> trackedClasses) {
        private Configuration {
            collectors = Collections.unmodifiableSet(EnumSet.copyOf(collectors));
            trackedClasses = trackedClasses == null ? List.of() : List.copyOf(trackedClasses);
        }

        private static Configuration disabled() {
            return new Configuration(EnumSet.noneOf(DiagnosticCollector.class), null, List.of());
        }
    }

    public record CaptureResult(CheckpointDiagnostics diagnostics, ClassHistogram histogram, List<String> warnings) {
        public CaptureResult {
            diagnostics = diagnostics == null ? CheckpointDiagnostics.EMPTY : diagnostics;
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public static CaptureResult empty() {
            return new CaptureResult(CheckpointDiagnostics.EMPTY, null, List.of());
        }
    }
}
