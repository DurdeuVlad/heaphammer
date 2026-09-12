package com.dwurdy.heaphammer.platform.forge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Production MinecraftForge 1.17-1.20.1 implementation of {@link ForgeTicketBridge}
 * that talks directly to {@link ServerChunkCache} region tickets under a dedicated
 * HeapHammer {@link TicketType}. Modern Forge uses the vanilla ticket system, so the
 * bridge's opaque "ticket" object is simply the dimension identifier marker; each
 * force/unforce maps 1:1 onto a region ticket. Holds no live chunk or level
 * references between calls.
 */
public class DirectForgeTicketBridge implements ForgeTicketBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-forge-tickets");

    public static final TicketType<ChunkPos> HEAPHAMMER_TICKET = TicketType.create(
            "heaphammer",
            Comparator.comparingLong(ChunkPos::toLong)
    );

    private final Supplier<MinecraftServer> serverSupplier;

    public DirectForgeTicketBridge(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = Objects.requireNonNull(serverSupplier, "serverSupplier must not be null");
    }

    @Override
    public Object requestTicket(String dimension) {
        if (!isDimensionLoaded(dimension)) {
            LOGGER.warn("Cannot request Forge ticket: dimension '{}' is not loaded or does not exist", dimension);
            return null;
        }
        // Region tickets are acquired per-chunk in forceChunk; the dimension id is the ticket handle.
        return dimension;
    }

    @Override
    public boolean forceChunk(Object ticket, String dimension, int chunkX, int chunkZ) {
        ServerLevel level = getLevel(dimension);
        if (level == null) {
            LOGGER.warn("Cannot force chunk: dimension '{}' is not loaded", dimension);
            return false;
        }
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        // Distance 1 loads the chunk for processing
        level.getChunkSource().addRegionTicket(HEAPHAMMER_TICKET, pos, 1, pos);
        return true;
    }

    @Override
    public boolean unforceChunk(Object ticket, String dimension, int chunkX, int chunkZ) {
        ServerLevel level = getLevel(dimension);
        if (level == null) {
            return false;
        }
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        level.getChunkSource().removeRegionTicket(HEAPHAMMER_TICKET, pos, 1, pos);
        return true;
    }

    @Override
    public void releaseTicket(Object ticket) {
        // No-op: modern region tickets have no persistent ticket object to release;
        // unforceChunk removes the ticket per chunk.
    }

    @Override
    public boolean isDimensionLoaded(String dimension) {
        return getLevel(dimension) != null;
    }

    private ServerLevel getLevel(String dimension) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return null;

        ResourceLocation loc = ResourceLocation.tryParse(dimension);
        if (loc == null) return null;

        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, loc);
        return server.getLevel(key);
    }
}
