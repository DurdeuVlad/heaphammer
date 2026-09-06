package com.dwurdy.heaphammer.scenario.chunks;

import com.dwurdy.heaphammer.domain.ExperimentSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Focuses on cycling the same small cluster of chunks to maximize churn on specific chunk lifecycles.
 */
public class HotspotChurnStrategy implements ChunkSelectionStrategy {
    @Override
    public List<ChunkCoord> generateCoords(ExperimentSpec spec) {
        int r = Math.min(1, spec.radius());
        List<ChunkCoord> coords = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                coords.add(new ChunkCoord(x, z));
            }
        }
        return coords;
    }
}
