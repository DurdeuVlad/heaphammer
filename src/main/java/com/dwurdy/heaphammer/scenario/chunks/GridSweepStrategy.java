package com.dwurdy.heaphammer.scenario.chunks;

import com.dwurdy.heaphammer.domain.ExperimentSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Sweeps chunk grid row by row from (-radius, -radius) to (radius, radius).
 */
public class GridSweepStrategy implements ChunkSelectionStrategy {
    @Override
    public List<ChunkCoord> generateCoords(ExperimentSpec spec) {
        int r = spec.radius();
        List<ChunkCoord> coords = new ArrayList<>();
        for (int z = -r; z <= r; z++) {
            for (int x = -r; x <= r; x++) {
                coords.add(new ChunkCoord(x, z));
            }
        }
        return coords;
    }
}
