package com.dwurdy.heaphammer.domain;

import java.util.HashMap;


import com.github.bsideup.jabel.Desugar;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Snapshot of runtime environment properties for reproducibility validation.
 */
@Desugar
public record EnvironmentFingerprint(
        String heapHammerVersion,
        String minecraftVersion,
        String loaderVersion,
        String javaVersion,
        long worldSeed,
        String modpackHash,
        Map<String, String> installedMods
) {
    public EnvironmentFingerprint {
        Objects.requireNonNull(heapHammerVersion, "heapHammerVersion must not be null");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion must not be null");
        Objects.requireNonNull(loaderVersion, "loaderVersion must not be null");
        Objects.requireNonNull(javaVersion, "javaVersion must not be null");
        installedMods = (installedMods == null) ? Collections.emptyMap() : Collections.unmodifiableMap(Collections.unmodifiableMap(new HashMap<>(installedMods)));
    }
}
