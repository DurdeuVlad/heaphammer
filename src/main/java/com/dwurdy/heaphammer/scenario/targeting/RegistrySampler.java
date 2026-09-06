package com.dwurdy.heaphammer.scenario.targeting;

import java.util.*;

/**
 * Deterministic coverage-based sampler for game registries (Section 20, Issue #18).
 */
public class RegistrySampler {

    public TargetPartition sample(String registryKey, List<String> allEntries, ModFilter filter, double coverage, long seed) {
        Objects.requireNonNull(registryKey, "registryKey must not be null");
        Objects.requireNonNull(allEntries, "allEntries must not be null");
        ModFilter effectiveFilter = (filter != null) ? filter : ModFilter.all();

        if (coverage <= 0.0 || coverage > 1.0) {
            throw new IllegalArgumentException("coverage must be > 0.0 and <= 1.0 (got: " + coverage + ")");
        }

        // 1. Filter entries by namespace
        List<String> filtered = new ArrayList<>();
        for (String entry : allEntries) {
            if (effectiveFilter.matches(entry)) {
                filtered.add(entry);
            }
        }

        String partitionId = "part-" + Long.toHexString(seed) + "-" + Integer.toHexString(registryKey.hashCode());

        if (filtered.isEmpty()) {
            return new TargetPartition(
                    partitionId,
                    registryKey,
                    coverage,
                    effectiveFilter.getIncludedMods(),
                    effectiveFilter.getExcludedMods(),
                    List.of(),
                    allEntries.size()
            );
        }

        // 2. Canonical sort before shuffle to guarantee platform independence
        Collections.sort(filtered);

        // 3. Deterministic shuffle based on experiment seed
        List<String> pool = new ArrayList<>(filtered);
        Collections.shuffle(pool, new Random(seed));

        // 4. Slice top sampleSize
        int sampleSize = Math.min(pool.size(), Math.max(1, (int) Math.round(filtered.size() * coverage)));
        List<String> selected = new ArrayList<>(pool.subList(0, sampleSize));

        // 5. Sort sampled entries for canonical output presentation
        Collections.sort(selected);

        return new TargetPartition(
                partitionId,
                registryKey,
                coverage,
                effectiveFilter.getIncludedMods(),
                effectiveFilter.getExcludedMods(),
                selected,
                allEntries.size()
        );
    }
}
