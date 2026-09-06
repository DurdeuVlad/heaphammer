package com.dwurdy.heaphammer.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Snapshot of runtime environment properties for reproducibility validation.
 */
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
        installedMods = (installedMods == null) ? Map.of() : Collections.unmodifiableMap(Map.copyOf(installedMods));
    }
}
