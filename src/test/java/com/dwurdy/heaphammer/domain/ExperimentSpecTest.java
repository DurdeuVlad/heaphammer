package com.dwurdy.heaphammer.domain;

import com.dwurdy.heaphammer.infrastructure.config.ConfigManager;
import com.dwurdy.heaphammer.infrastructure.config.HeapHammerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentSpecTest {

    @BeforeEach
    void setUp() {
        ConfigManager.setActiveConfig(new HeapHammerConfig());
    }

    @AfterEach
    void tearDown() {
        ConfigManager.setActiveConfig(new HeapHammerConfig());
    }

    @Test
    @DisplayName("Valid default spec builds successfully")
    void testValidSpec() {
        ExperimentSpec spec = ExperimentSpec.builder().build();
        assertNotNull(spec);
        assertEquals(6, spec.radius());
        assertEquals(5, spec.iterations());
        assertEquals(9, spec.batchSize());
    }

    @Test
    @DisplayName("Values exceeding configurable limits throw IllegalArgumentException")
    void testCeilingExceeded() {
        assertThrows(IllegalArgumentException.class, () ->
                ExperimentSpec.builder().radius(33).build()); // max 32 for chunks
        assertThrows(IllegalArgumentException.class, () ->
                ExperimentSpec.builder().iterations(51).build()); // max 50
        assertThrows(IllegalArgumentException.class, () ->
                ExperimentSpec.builder().batchSize(129).build()); // max 128
        assertThrows(IllegalArgumentException.class, () ->
                ExperimentSpec.builder().maxOperationsPerTick(51).build()); // max 50
        assertThrows(IllegalArgumentException.class, () ->
                ExperimentSpec.builder().maxMillisPerTick(36).build()); // max 35
    }

    @Test
    @DisplayName("Custom configuration allows higher ceiling when configured")
    void testCustomConfigCeiling() {
        HeapHammerConfig custom = new HeapHammerConfig();
        custom.setMaxRadius(100);
        custom.setMaxIterations(200);
        ConfigManager.setActiveConfig(custom);

        ExperimentSpec spec = ExperimentSpec.builder()
                .radius(64)
                .iterations(100)
                .build();
        assertEquals(64, spec.radius());
        assertEquals(100, spec.iterations());
    }
}
