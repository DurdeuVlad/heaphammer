package com.dwurdy.heaphammer.domain;

import com.dwurdy.heaphammer.infrastructure.json.GsonCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Release110DomainTest {

    @Test
    @DisplayName("1.1.0 player and diagnostic options round-trip through the experiment spec")
    void experimentSpecRoundTripsRelease110Options() {
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.PLAYERS)
                .loginsPerCycle(4)
                .playerActions(List.of(PlayerAction.JOIN, PlayerAction.QUIT, PlayerAction.RESPAWN))
                .entityProfile(EntityWorkloadProfile.PERSISTENT)
                .durationSeconds(7200L)
                .intervalSeconds(300L)
                .diagnosticCollectors(List.of(
                        DiagnosticCollector.HISTOGRAM,
                        DiagnosticCollector.RETENTION,
                        DiagnosticCollector.WORLD_STORE,
                        DiagnosticCollector.EVENT_METRICS
                ))
                .trackedClasses(List.of("net.minecraft.server.level.ServerPlayer"))
                .build();

        ExperimentSpec restored = GsonCodec.fromJson(GsonCodec.toJson(spec), ExperimentSpec.class);

        assertEquals(ScenarioId.PLAYERS, restored.scenarioId());
        assertEquals(4, restored.loginsPerCycle());
        assertEquals(List.of(PlayerAction.JOIN, PlayerAction.QUIT, PlayerAction.RESPAWN), restored.playerActions());
        assertEquals(EntityWorkloadProfile.PERSISTENT, restored.entityProfile());
        assertEquals(7200L, restored.durationSeconds());
        assertEquals(300L, restored.intervalSeconds());
        assertTrue(restored.diagnosticCollectors().contains(DiagnosticCollector.EVENT_METRICS));
        assertEquals(List.of("net.minecraft.server.level.ServerPlayer"), restored.trackedClasses());
    }

    @Test
    @DisplayName("capabilities expose explicit support and unsupported reasons")
    void capabilitiesAreExplicit() {
        PlatformCapabilities capabilities = PlatformCapabilities.builder()
                .supported(PlatformCapability.PLAYER_LIFECYCLE)
                .unsupported(PlatformCapability.EVENT_METRICS, "loader event bus is not safely interceptable")
                .build();

        assertTrue(capabilities.supports(PlatformCapability.PLAYER_LIFECYCLE));
        assertFalse(capabilities.supports(PlatformCapability.EVENT_METRICS));
        assertEquals("loader event bus is not safely interceptable",
                capabilities.status(PlatformCapability.EVENT_METRICS).reason());
    }

    @Test
    @DisplayName("soak schedules require a positive interval and never exceed the duration")
    void soakScheduleValidatesBounds() {
        SoakSchedule schedule = new SoakSchedule(7200L, 300L);

        assertEquals(24L, schedule.maxCheckpoints());
        assertThrows(IllegalArgumentException.class, () -> new SoakSchedule(60L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new SoakSchedule(60L, 61L));
    }
}
