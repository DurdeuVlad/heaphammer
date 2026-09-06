package com.dwurdy.heaphammer.scenario.chunks;

import com.dwurdy.heaphammer.domain.ExperimentSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Deterministic random walk bounded within spec.radius().
 */
public class RandomWalkStrategy implements ChunkSelectionStrategy {
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Override
    public List<ChunkCoord> generateCoords(ExperimentSpec spec) {
        SplittableRandom rng = new SplittableRandom(spec.seed());
        int r = spec.radius();
        int targetCount = Math.min((2 * r + 1) * (2 * r + 1), spec.iterations() * spec.batchSize());

        Set<ChunkCoord> visited = new LinkedHashSet<>();
        int currX = 0;
        int currZ = 0;
        visited.add(new ChunkCoord(currX, currZ));

        int maxSteps = targetCount * 5;
        for (int i = 0; i < maxSteps && visited.size() < targetCount; i++) {
            int[] dir = DIRS[rng.nextInt(DIRS.length)];
            int nextX = currX + dir[0];
            int nextZ = currZ + dir[1];
            if (Math.abs(nextX) <= r && Math.abs(nextZ) <= r) {
                currX = nextX;
                currZ = nextZ;
                visited.add(new ChunkCoord(currX, currZ));
            }
        }
        return new ArrayList<>(visited);
    }
}
