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

    @Test
    @DisplayName("toBuilder round-trip preserves every field, including playerCohort")
    void testToBuilderRoundTrip() {
        ExperimentSpec original = ExperimentSpec.builder()
                .scenarioId(ScenarioId.PLAYERS)
                .seed(1234L)
                .dimension("minecraft:the_nether")
                .center(12, -34)
                .radius(8)
                .iterations(3)
                .batchSize(5)
                .strategy("LINEAR")
                .warmupIterations(1)
                .holdTicks(7)
                .settleTicks(9)
                .maxOperationsPerTick(4)
                .maxMillisPerTick(10)
                .explicitGc(true)
                .coverage(0.5)
                .includeMods(java.util.List.of("mod-a"))
                .excludeMods(java.util.List.of("mod-b"))
                .entityProfile(EntityWorkloadProfile.PERSISTENT)
                .loginsPerCycle(4)
                .playerActions(java.util.List.of(PlayerAction.JOIN, PlayerAction.LOOKAT, PlayerAction.QUIT))
                .playerCohort(true)
                .diagnosticCollectors(java.util.List.of(DiagnosticCollector.RETENTION))
                .trackedClasses(java.util.List.of("com.example.Tracked"))
                .build();

        ExperimentSpec copy = original.toBuilder().build();
        assertEquals(original.scenarioId(), copy.scenarioId());
        assertEquals(original.seed(), copy.seed());
        assertEquals(original.dimension(), copy.dimension());
        assertEquals(original.centerX(), copy.centerX());
        assertEquals(original.centerZ(), copy.centerZ());
        assertEquals(original.radius(), copy.radius());
        assertEquals(original.iterations(), copy.iterations());
        assertEquals(original.batchSize(), copy.batchSize());
        assertEquals(original.strategy(), copy.strategy());
        assertEquals(original.warmupIterations(), copy.warmupIterations());
        assertEquals(original.holdTicks(), copy.holdTicks());
        assertEquals(original.settleTicks(), copy.settleTicks());
        assertEquals(original.maxOperationsPerTick(), copy.maxOperationsPerTick());
        assertEquals(original.maxMillisPerTick(), copy.maxMillisPerTick());
        assertEquals(original.explicitGc(), copy.explicitGc());
        assertEquals(original.coverage(), copy.coverage());
        assertEquals(original.includeMods(), copy.includeMods());
        assertEquals(original.excludeMods(), copy.excludeMods());
        assertEquals(original.entityProfile(), copy.entityProfile());
        assertEquals(original.loginsPerCycle(), copy.loginsPerCycle());
        assertEquals(original.playerActions(), copy.playerActions());
        assertEquals(original.playerCohort(), copy.playerCohort());
        assertEquals(original.durationSeconds(), copy.durationSeconds());
        assertEquals(original.intervalSeconds(), copy.intervalSeconds());
        assertEquals(original.diagnosticCollectors(), copy.diagnosticCollectors());
        assertEquals(original.trackedClasses(), copy.trackedClasses());
    }
}
