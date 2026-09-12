package com.dwurdy.heaphammer.application;

import com.dwurdy.heaphammer.infrastructure.config.ConfigManager;
import com.dwurdy.heaphammer.infrastructure.config.HeapHammerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafetyCircuitBreakerTest {

    @BeforeEach
    void setUp() {
        ConfigManager.setActiveConfig(new HeapHammerConfig());
    }

    @AfterEach
    void tearDown() {
        ConfigManager.setActiveConfig(new HeapHammerConfig());
    }

    @Test
    @DisplayName("Circuit breaker does not trip when memory is abundant")
    void testHealthyMemory() {
        // 1024 MB available (> 64 MB threshold)
        SafetyCircuitBreaker.MemoryProvider memory = new SafetyCircuitBreaker.MemoryProvider() {
            @Override
            public long getAvailableMemoryBytes() {
                return 1024L * 1024L * 1024L;
            }

            @Override
            public long getMaxMemoryBytes() {
                return 2048L * 1024L * 1024L;
            }
        };

        SafetyCircuitBreaker breaker = new SafetyCircuitBreaker(memory);
        SafetyCircuitBreaker.TripResult result = breaker.evaluate();

        assertFalse(result.tripped());
        assertNull(result.reason());
    }

    @Test
    @DisplayName("Circuit breaker trips when available memory is below threshold")
    void testLowMemoryTrips() {
        // 32 MB available (< 64 MB threshold)
        SafetyCircuitBreaker.MemoryProvider memory = new SafetyCircuitBreaker.MemoryProvider() {
            @Override
            public long getAvailableMemoryBytes() {
                return 32L * 1024L * 1024L;
            }

            @Override
            public long getMaxMemoryBytes() {
                return 2048L * 1024L * 1024L;
            }
        };

        SafetyCircuitBreaker breaker = new SafetyCircuitBreaker(memory);
        SafetyCircuitBreaker.TripResult result = breaker.evaluate();

        assertTrue(result.tripped());
        assertNotNull(result.reason());
        assertTrue(result.reason().contains("Available JVM heap is dangerously low"));
        assertTrue(result.reason().contains("32 MB"));
    }

    @Test
    @DisplayName("Circuit breaker respects circuitBreakerEnabled config toggle")
    void testDisabledBreaker() {
        HeapHammerConfig config = new HeapHammerConfig();
        config.setCircuitBreakerEnabled(false);
        ConfigManager.setActiveConfig(config);

        SafetyCircuitBreaker.MemoryProvider memory = new SafetyCircuitBreaker.MemoryProvider() {
            @Override
            public long getAvailableMemoryBytes() {
                return 10L * 1024L * 1024L; // critically low
            }

            @Override
            public long getMaxMemoryBytes() {
                return 2048L * 1024L * 1024L;
            }
        };

        SafetyCircuitBreaker breaker = new SafetyCircuitBreaker(memory);
        SafetyCircuitBreaker.TripResult result = breaker.evaluate();

        assertFalse(result.tripped());
    }
}
