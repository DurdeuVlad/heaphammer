package com.dwurdy.testmod.chunkcache;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Objects;

/**
 * Entry holding hard reference to LevelChunk and associated metadata.
 */
public class RetainedChunkEntry {
    private final ChunkPos pos;
    private final ResourceKey<Level> dimension;
    private final LevelChunk chunk;
    private final long timestamp;
    private final byte[] cacheBuffer = new byte[1024 * 1024];

    public RetainedChunkEntry(ChunkPos pos, ResourceKey<Level> dimension, LevelChunk chunk) {
        this.pos = Objects.requireNonNull(pos, "pos must not be null");
        this.dimension = Objects.requireNonNull(dimension, "dimension must not be null");
        this.chunk = Objects.requireNonNull(chunk, "chunk must not be null");
        this.timestamp = System.currentTimeMillis();
    }

    public ChunkPos getPos() {
        return pos;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public LevelChunk getChunk() {
        return chunk;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
