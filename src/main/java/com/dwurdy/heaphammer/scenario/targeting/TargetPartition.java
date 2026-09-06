package com.dwurdy.heaphammer.scenario.targeting;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Persisted deterministic target partition sampled from registries (Section 20, Issue #18).
 */
public record TargetPartition(
        String partitionId,
        String registryKey,
        double coverage,
        List<String> includedMods,
        List<String> excludedMods,
        List<String> sampledEntries,
        int totalRegistryEntries
) {
    public TargetPartition {
        Objects.requireNonNull(partitionId, "partitionId must not be null");
        Objects.requireNonNull(registryKey, "registryKey must not be null");
        includedMods = (includedMods == null) ? List.of() : Collections.unmodifiableList(List.copyOf(includedMods));
        excludedMods = (excludedMods == null) ? List.of() : Collections.unmodifiableList(List.copyOf(excludedMods));
        sampledEntries = (sampledEntries == null) ? List.of() : Collections.unmodifiableList(List.copyOf(sampledEntries));
    }
}
