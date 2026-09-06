package com.dwurdy.heaphammer.adapter;

import com.dwurdy.heaphammer.platform.PlatformAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry and discovery manager for third-party Workload Adapters (Section 15.7, Issue #19).
 */
public class WorkloadAdapterRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-adapters");
    private static final WorkloadAdapterRegistry INSTANCE = new WorkloadAdapterRegistry();

    private final Map<String, WorkloadAdapter> adapters = new ConcurrentHashMap<>();

    public static WorkloadAdapterRegistry getInstance() {
        return INSTANCE;
    }

    public void register(WorkloadAdapter adapter, PlatformAdapter platform) {
        Objects.requireNonNull(adapter, "adapter must not be null");
        String id = adapter.adapterId().toLowerCase(Locale.ROOT);
        adapters.put(id, adapter);

        if (platform != null) {
            try {
                adapter.initialize(platform);
                LOGGER.info("Registered and initialized workload adapter: {} ({})", adapter.displayName(), id);
            } catch (Exception e) {
                LOGGER.error("Error initializing workload adapter: " + id, e);
            }
        }
    }

    public void register(WorkloadAdapter adapter) {
        register(adapter, null);
    }

    public Optional<WorkloadAdapter> unregister(String adapterId, PlatformAdapter platform) {
        if (adapterId == null) return Optional.empty();
        WorkloadAdapter removed = adapters.remove(adapterId.toLowerCase(Locale.ROOT));
        if (removed != null && platform != null) {
            try {
                removed.teardown(platform);
            } catch (Exception e) {
                LOGGER.error("Error tearing down workload adapter: " + adapterId, e);
            }
        }
        return Optional.ofNullable(removed);
    }

    public Optional<WorkloadAdapter> getAdapter(String adapterId) {
        if (adapterId == null) return Optional.empty();
        return Optional.ofNullable(adapters.get(adapterId.toLowerCase(Locale.ROOT)));
    }

    public List<WorkloadAdapter> getAllAdapters() {
        return new ArrayList<>(adapters.values());
    }

    public Optional<WorkloadAdapter> findAdapterForScenario(String scenarioName) {
        if (scenarioName == null) return Optional.empty();
        String target = scenarioName.trim().toLowerCase(Locale.ROOT);
        for (WorkloadAdapter adapter : adapters.values()) {
            for (String scenario : adapter.supportedScenarios()) {
                if (scenario.equalsIgnoreCase(target)) {
                    return Optional.of(adapter);
                }
            }
        }
        return Optional.empty();
    }

    public void loadFromFabricEntrypoints(PlatformAdapter platform) {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            java.lang.reflect.Method getEntrypoints = loaderClass.getMethod("getEntrypoints", String.class, Class.class);
            @SuppressWarnings("unchecked")
            List<WorkloadAdapter> entrypoints = (List<WorkloadAdapter>) getEntrypoints.invoke(loader, "heaphammer:adapter", WorkloadAdapter.class);

            for (WorkloadAdapter adapter : entrypoints) {
                register(adapter, platform);
            }
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            // Running in headless or non-Fabric test environment
        } catch (Exception e) {
            LOGGER.error("Failed discovering workload adapter entrypoints", e);
        }
    }

    public void clear(PlatformAdapter platform) {
        for (WorkloadAdapter adapter : adapters.values()) {
            if (platform != null) {
                try {
                    adapter.teardown(platform);
                } catch (Exception e) {
                    LOGGER.error("Error tearing down adapter on clear: " + adapter.adapterId(), e);
                }
            }
        }
        adapters.clear();
    }
}
