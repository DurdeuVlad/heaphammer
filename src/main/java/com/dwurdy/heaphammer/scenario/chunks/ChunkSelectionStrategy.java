package com.dwurdy.heaphammer.scenario.chunks;

import com.github.bsideup.jabel.Desugar;

import com.dwurdy.heaphammer.domain.ExperimentSpec;

import java.util.List;

/**
 * Strategy interface for generating candidate chunk coordinate offsets (relative to center).
 */
public interface ChunkSelectionStrategy {
    @Desugar
    record ChunkCoord(int x, int z) {}

    /**
     * Generate an ordered list of chunk coordinate offsets relative to center for the given spec.
     */
    List<ChunkCoord> generateCoords(ExperimentSpec spec);
}
