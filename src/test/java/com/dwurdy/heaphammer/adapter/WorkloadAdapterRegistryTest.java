package com.dwurdy.heaphammer.adapter;

import com.dwurdy.heaphammer.application.ExperimentService;
import com.dwurdy.heaphammer.domain.*;
import com.dwurdy.heaphammer.platform.MockPlatformAdapter;
import com.dwurdy.heaphammer.platform.PlatformAdapter;
import com.dwurdy.heaphammer.scenario.ScenarioExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadAdapterRegistryTest {

    private WorkloadAdapterRegistry registry;
    private MockPlatformAdapter platform;

    @BeforeEach
    void setUp() {
        registry = WorkloadAdapterRegistry.getInstance();
        platform = new MockPlatformAdapter();
        registry.clear(platform);
    }

    @AfterEach
    void tearDown() {
        registry.clear(platform);
    }

    @Test
    @DisplayName("Register, lookup, and unregister workload adapter")
    void testRegisterAndLookup() {
        AtomicBoolean initialized = new AtomicBoolean(false);
        AtomicBoolean tornDown = new AtomicBoolean(false);

        WorkloadAdapter adapter = new WorkloadAdapter() {
            @Override
            public String adapterId() {
                return "test-tech-mod";
            }

            @Override
            public String displayName() {
                return "Test Tech Mod";
            }

            @Override
            public List<String> supportedScenarios() {
                return List.of("ENERGY_GRID", "MACHINE_NETWORK");
            }

            @Override
            public void initialize(PlatformAdapter p) {
                initialized.set(true);
            }

            @Override
            public Optional<ScenarioExecutor> createExecutor(
                    ExperimentPlan plan,
                    PlatformAdapter p,
                    BiConsumer<CheckpointPhase, Integer> checkpointTrigger,
                    Consumer<ExperimentState> completionCallback
            ) {
                return Optional.empty();
            }

            @Override
            public void teardown(PlatformAdapter p) {
                tornDown.set(true);
            }
        };

        registry.register(adapter, platform);
        assertTrue(initialized.get(), "initialize must be called upon registration");

        Optional<WorkloadAdapter> found = registry.getAdapter("test-tech-mod");
        assertTrue(found.isPresent());
        assertEquals("Test Tech Mod", found.get().displayName());

        Optional<WorkloadAdapter> byScenario = registry.findAdapterForScenario("energy_grid");
        assertTrue(byScenario.isPresent());
        assertEquals("test-tech-mod", byScenario.get().adapterId());

        Optional<WorkloadAdapter> unregistered = registry.unregister("test-tech-mod", platform);
        assertTrue(unregistered.isPresent());
        assertTrue(tornDown.get(), "teardown must be called upon unregistration");
        assertTrue(registry.getAdapter("test-tech-mod").isEmpty());
    }

    @Test
    @DisplayName("Error isolation: faulty adapter initialize or teardown does not propagate exception")
    void testFaultyAdapterErrorIsolation() {
        WorkloadAdapter faulty = new WorkloadAdapter() {
            @Override
            public String adapterId() {
                return "faulty";
            }

            @Override
            public List<String> supportedScenarios() {
                return List.of("CRASH_SCENARIO");
            }

            @Override
            public void initialize(PlatformAdapter p) {
                throw new RuntimeException("Simulated initialization failure in external mod");
            }

            @Override
            public Optional<ScenarioExecutor> createExecutor(
                    ExperimentPlan plan,
                    PlatformAdapter p,
                    BiConsumer<CheckpointPhase, Integer> checkpointTrigger,
                    Consumer<ExperimentState> completionCallback
            ) {
                return Optional.empty();
            }

            @Override
            public void teardown(PlatformAdapter p) {
                throw new RuntimeException("Simulated teardown failure in external mod");
            }
        };

        assertDoesNotThrow(() -> registry.register(faulty, platform));
        assertDoesNotThrow(() -> registry.unregister("faulty", platform));
    }

    @Test
    @DisplayName("ExperimentService dispatches to registered adapter when scenario matches")
    void testExperimentServiceDispatchesToAdapter() {
        AtomicBoolean customExecutorCreated = new AtomicBoolean(false);

        WorkloadAdapter adapter = new WorkloadAdapter() {
            @Override
            public String adapterId() {
                return "custom-mod";
            }

            @Override
            public List<String> supportedScenarios() {
                return List.of("CUSTOM_NETWORK");
            }

            @Override
            public Optional<ScenarioExecutor> createExecutor(
                    ExperimentPlan plan,
                    PlatformAdapter p,
                    BiConsumer<CheckpointPhase, Integer> checkpointTrigger,
                    Consumer<ExperimentState> completionCallback
            ) {
                customExecutorCreated.set(true);
                return Optional.of(new ScenarioExecutor() {
                    private final com.dwurdy.heaphammer.application.ExperimentStateMachine sm =
                            new com.dwurdy.heaphammer.application.ExperimentStateMachine();

                    @Override
                    public com.dwurdy.heaphammer.application.ExperimentStateMachine getStateMachine() {
                        return sm;
                    }

                    @Override
                    public ExperimentPlan getPlan() {
                        return plan;
                    }

                    @Override
                    public int getCurrentIteration() {
                        return 0;
                    }

                    @Override
                    public void tick() {}

                    @Override
                    public void stop(String reason) {
                        sm.transitionTo(ExperimentState.ABORTED, reason);
                    }
                });
            }
        };

        registry.register(adapter, platform);

        ExperimentService service = new ExperimentService(platform);
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.of("CUSTOM_NETWORK"))
                .build();

        ExperimentPlan plan = new ExperimentPlan(ExperimentId.generate(), System.currentTimeMillis(), spec, List.of(), 100, 0);

        ScenarioExecutor executor = service.start(plan);
        assertNotNull(executor);
        assertTrue(customExecutorCreated.get(), "Custom adapter createExecutor must be invoked by ExperimentService");
    }
}
