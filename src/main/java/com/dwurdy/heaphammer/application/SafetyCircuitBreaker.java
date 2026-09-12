package com.dwurdy.heaphammer.application;

import com.github.bsideup.jabel.Desugar;
import com.dwurdy.heaphammer.infrastructure.config.ConfigManager;
import com.dwurdy.heaphammer.infrastructure.config.HeapHammerConfig;

import java.util.Objects;

/**
 * Runtime watchdog circuit breaker that halts running experiments if JVM heap
 * memory drops below critical thresholds, preventing OutOfMemoryError and server watchdog crashes.
 */
public class SafetyCircuitBreaker {

    public interface MemoryProvider {
        long getAvailableMemoryBytes();
        long getMaxMemoryBytes();
    }

    public static class SystemMemoryProvider implements MemoryProvider {
        @Override
        public long getAvailableMemoryBytes() {
            long max = Runtime.getRuntime().maxMemory();
            long total = Runtime.getRuntime().totalMemory();
            long free = Runtime.getRuntime().freeMemory();
            return (max - total) + free;
        }

        @Override
        public long getMaxMemoryBytes() {
            return Runtime.getRuntime().maxMemory();
        }
    }

    @Desugar
    public record TripResult(boolean tripped, String reason) {}

    private final MemoryProvider memoryProvider;

    public SafetyCircuitBreaker() {
        this(new SystemMemoryProvider());
    }

    public SafetyCircuitBreaker(MemoryProvider memoryProvider) {
        this.memoryProvider = Objects.requireNonNull(memoryProvider, "memoryProvider must not be null");
    }

    public TripResult evaluate() {
        HeapHammerConfig config = ConfigManager.getActiveConfig();
        if (config == null || !config.isCircuitBreakerEnabled()) {
            return new TripResult(false, null);
        }

        long available = memoryProvider.getAvailableMemoryBytes();
        long thresholdBytes = config.getMinFreeMemoryMb() * 1024L * 1024L;

        if (available < thresholdBytes) {
            long availableMb = available / (1024L * 1024L);
            String reason = String.format(
                    "Safety circuit breaker tripped: Available JVM heap is dangerously low (%d MB remaining < %d MB threshold). Workload aborted to prevent OutOfMemoryError.",
                    availableMb, config.getMinFreeMemoryMb()
            );
            return new TripResult(true, reason);
        }

        return new TripResult(false, null);
    }
}
