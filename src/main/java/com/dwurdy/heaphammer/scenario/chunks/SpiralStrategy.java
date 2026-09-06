package com.dwurdy.heaphammer.scenario.chunks;

import com.dwurdy.heaphammer.domain.ExperimentSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Concentric spiral outwards from center (0,0) within radius.
 */
public class SpiralStrategy implements ChunkSelectionStrategy {
    @Override
    public List<ChunkCoord> generateCoords(ExperimentSpec spec) {
        int r = spec.radius();
        List<ChunkCoord> coords = new ArrayList<>();
        coords.add(new ChunkCoord(0, 0));

        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;

        int maxSteps = (2 * r + 1) * (2 * r + 1);
        for (int i = 0; i < maxSteps - 1; i++) {
            if ((-r <= x && x <= r) && (-r <= z && z <= r)) {
                if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                    int temp = dx;
                    dx = -dz;
                    dz = temp;
                }
            }
            x += dx;
            z += dz;
            if (Math.abs(x) <= r && Math.abs(z) <= r) {
                coords.add(new ChunkCoord(x, z));
            }
        }
        return coords;
    }
}
