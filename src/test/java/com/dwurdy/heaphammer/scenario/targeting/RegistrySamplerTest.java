package com.dwurdy.heaphammer.scenario.targeting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegistrySamplerTest {

    private static final List<String> FULL_REGISTRY = List.of(
            "minecraft:pig",
            "minecraft:cow",
            "minecraft:sheep",
            "minecraft:chicken",
            "minecraft:zombie",
            "minecraft:skeleton",
            "minecraft:creeper",
            "minecraft:enderman",
            "create:wrench",
            "create:cogwheel",
            "create:shaft",
            "create:water_wheel",
            "botania:flower",
            "botania:mana_spreader",
            "testmod-leak:block",
            "testmod-crossmod:item"
    );

    @Test
    @DisplayName("Identical inputs and seed produce 100% deterministic TargetPartition")
    void testDeterminismAcrossRuns() {
        RegistrySampler sampler = new RegistrySampler();
        ModFilter filter = new ModFilter(List.of(), List.of());

        TargetPartition part1 = sampler.sample("minecraft:entity_type", FULL_REGISTRY, filter, 0.5, 98765L);
        TargetPartition part2 = sampler.sample("minecraft:entity_type", FULL_REGISTRY, filter, 0.5, 98765L);

        assertEquals(part1.sampledEntries().size(), part2.sampledEntries().size());
        assertEquals(part1.sampledEntries(), part2.sampledEntries());
        assertEquals(part1.totalRegistryEntries(), part2.totalRegistryEntries());
    }

    @Test
    @DisplayName("Different seeds produce different sampled subsets")
    void testDifferentSeedsProduceDifferentSubsets() {
        RegistrySampler sampler = new RegistrySampler();
        ModFilter filter = new ModFilter(List.of(), List.of());

        TargetPartition part1 = sampler.sample("minecraft:entity_type", FULL_REGISTRY, filter, 0.3, 11111L);
        TargetPartition part2 = sampler.sample("minecraft:entity_type", FULL_REGISTRY, filter, 0.3, 99999L);

        assertNotEquals(part1.sampledEntries(), part2.sampledEntries(), "Different seeds must produce different samples");
    }

    @Test
    @DisplayName("Coverage percentage controls sample size accurately")
    void testCoveragePercentage() {
        RegistrySampler sampler = new RegistrySampler();
        ModFilter filter = new ModFilter(List.of(), List.of());

        TargetPartition half = sampler.sample("minecraft:entity_type", FULL_REGISTRY, filter, 0.5, 42L);
        assertEquals(8, half.sampledEntries().size(), "0.5 coverage of 16 entries should produce 8 entries");

        TargetPartition quarter = sampler.sample("minecraft:entity_type", FULL_REGISTRY, filter, 0.25, 42L);
        assertEquals(4, quarter.sampledEntries().size(), "0.25 coverage of 16 entries should produce 4 entries");

        TargetPartition full = sampler.sample("minecraft:entity_type", FULL_REGISTRY, filter, 1.0, 42L);
        assertEquals(16, full.sampledEntries().size(), "1.0 coverage should produce all 16 entries");
    }

    @Test
    @DisplayName("Mod inclusion filter retains only targeted namespace entries")
    void testIncludeModFilter() {
        RegistrySampler sampler = new RegistrySampler();
        ModFilter filter = new ModFilter(List.of("create"), List.of());

        TargetPartition part = sampler.sample("minecraft:block", FULL_REGISTRY, filter, 1.0, 42L);
        assertEquals(4, part.sampledEntries().size());
        for (String entry : part.sampledEntries()) {
            assertTrue(entry.startsWith("create:"));
        }
    }

    @Test
    @DisplayName("Mod exclusion filter removes excluded namespace entries")
    void testExcludeModFilter() {
        RegistrySampler sampler = new RegistrySampler();
        ModFilter filter = new ModFilter(List.of(), List.of("minecraft"));

        TargetPartition part = sampler.sample("minecraft:block", FULL_REGISTRY, filter, 1.0, 42L);
        assertEquals(8, part.sampledEntries().size());
        for (String entry : part.sampledEntries()) {
            assertFalse(entry.startsWith("minecraft:"));
        }
    }

    @Test
    @DisplayName("Wildcard filter correctly matches prefixed namespaces")
    void testWildcardFilter() {
        RegistrySampler sampler = new RegistrySampler();
        ModFilter filter = new ModFilter(List.of("testmod*"), List.of());

        TargetPartition part = sampler.sample("minecraft:block", FULL_REGISTRY, filter, 1.0, 42L);
        assertEquals(2, part.sampledEntries().size());
        assertTrue(part.sampledEntries().contains("testmod-leak:block"));
        assertTrue(part.sampledEntries().contains("testmod-crossmod:item"));
    }

    @Test
    @DisplayName("Empty filter result does not crash and returns safe empty partition")
    void testEmptyFilterDoesNotCrash() {
        RegistrySampler sampler = new RegistrySampler();
        ModFilter filter = new ModFilter(List.of("nonexistent_mod"), List.of());

        TargetPartition part = sampler.sample("minecraft:block", FULL_REGISTRY, filter, 0.5, 42L);
        assertNotNull(part);
        assertTrue(part.sampledEntries().isEmpty());
        assertEquals(FULL_REGISTRY.size(), part.totalRegistryEntries());
    }
}
