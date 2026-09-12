package com.dwurdy.heaphammer.metrics;

import com.dwurdy.heaphammer.detection.CleanupValidator;
import com.dwurdy.heaphammer.diagnostics.EventMetricsSnapshot;
import com.dwurdy.heaphammer.domain.DiagnosticCollector;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.platform.EventMetricsPort;
import com.dwurdy.heaphammer.platform.MockPlatformAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticCaptureServiceTest {
    @Test
    void expensiveCollectorIsNotAwaitedByCheckpointCall() throws Exception {
        BlockingEventPlatform platform = new BlockingEventPlatform();
        CheckpointService service = new CheckpointService(new JvmMetricsCollector(),
                new MinecraftMetricsCollector(platform), new CleanupValidator(platform));
        service.configure(ExperimentSpec.builder().diagnosticCollectors(List.of(DiagnosticCollector.EVENT_METRICS)).build(), platform);

        long start = System.nanoTime();
        service.recordCheckpoint(com.dwurdy.heaphammer.domain.CheckpointPhase.BASELINE, 0, "minecraft:overworld", false);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsedMs < 500, "checkpoint must not wait for the diagnostic worker");
        assertTrue(platform.entered.await(2, TimeUnit.SECONDS));

        platform.release.countDown();
        service.awaitDiagnostics().get(2, TimeUnit.SECONDS);
        service.close();
    }

    private static final class BlockingEventPlatform extends MockPlatformAdapter {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public Optional<EventMetricsPort> getEventMetricsPort() {
            return Optional.of(() -> {
                entered.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return EventMetricsSnapshot.empty();
            });
        }
    }
}
