package com.dwurdy.testmod.chunkcache;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.Objects;

/**
 * Entry holding hard reference to LevelChunk and associated metadata.
 */
public class RetainedChunkEntry {
    private final ChunkPos pos;
    private final DimensionType dimension;
    private final LevelChunk chunk;
    private final long timestamp;
    private final byte[] cacheBuffer = new byte[1024 * 1024];

    public RetainedChunkEntry(ChunkPos pos, DimensionType dimension, LevelChunk chunk) {
        this.pos = Objects.requireNonNull(pos, "pos must not be null");
        this.dimension = Objects.requireNonNull(dimension, "dimension must not be null");
        this.chunk = Objects.requireNonNull(chunk, "chunk must not be null");
        this.timestamp = System.currentTimeMillis();
    }

    public ChunkPos getPos() {
        return pos;
    }

    public DimensionType getDimension() {
        return dimension;
    }

    public LevelChunk getChunk() {
        return chunk;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
