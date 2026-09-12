package com.dwurdy.testmod.omnitrack;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Audit record holding references to LevelChunk and key sampled block positions.
 */
public class ChunkAuditRecord {
    private final ChunkPos pos;
    private final DimensionType dimension;
    private final LevelChunk chunk;
    private final List<BlockPos> sampledPositions;
    private final long timestamp;
    private final byte[] auditPayload = new byte[1024 * 1024];

    public ChunkAuditRecord(ChunkPos pos, DimensionType dimension, LevelChunk chunk) {
        this.pos = Objects.requireNonNull(pos, "pos must not be null");
        this.dimension = Objects.requireNonNull(dimension, "dimension must not be null");
        this.chunk = Objects.requireNonNull(chunk, "chunk must not be null");
        this.timestamp = System.currentTimeMillis();

        this.sampledPositions = new ArrayList<>(8);
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        for (int y = -64; y < 320; y += 48) {
            this.sampledPositions.add(new BlockPos(minX + 8, y, minZ + 8));
        }
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

    public List<BlockPos> getSampledPositions() {
        return sampledPositions;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
