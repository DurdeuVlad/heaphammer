package com.dwurdy.heaphammer.scenario.chunks;

import com.dwurdy.heaphammer.domain.ExperimentSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Concentric circular rings around center, ordered strictly by increasing Euclidean distance.
 */
public class RingStrategy implements ChunkSelectionStrategy {
    @Override
    public List<ChunkCoord> generateCoords(ExperimentSpec spec) {
        int r = spec.radius();
        List<ChunkCoord> coords = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z <= r * r) {
                    coords.add(new ChunkCoord(x, z));
                }
            }
        }
        coords.sort(Comparator.comparingInt(c -> c.x() * c.x() + c.z() * c.z()));
        return coords;
    }
}
