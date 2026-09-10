package com.dwurdy.heaphammer.scenario.targeting;

import com.github.bsideup.jabel.Desugar;

import java.util.ArrayList;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Persisted deterministic target partition sampled from registries (Section 20, Issue #18).
 */
@Desugar
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
        includedMods = (includedMods == null) ? Collections.emptyList() : Collections.unmodifiableList(Collections.unmodifiableList(new ArrayList<>(includedMods)));
        excludedMods = (excludedMods == null) ? Collections.emptyList() : Collections.unmodifiableList(Collections.unmodifiableList(new ArrayList<>(excludedMods)));
        sampledEntries = (sampledEntries == null) ? Collections.emptyList() : Collections.unmodifiableList(Collections.unmodifiableList(new ArrayList<>(sampledEntries)));
    }
}
