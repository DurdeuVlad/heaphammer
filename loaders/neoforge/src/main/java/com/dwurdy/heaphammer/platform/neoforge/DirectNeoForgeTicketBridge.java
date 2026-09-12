package com.dwurdy.heaphammer.platform.neoforge;

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
 * Production NeoForge 1.21.x implementation of {@link NeoForgeTicketBridge} that talks
 * directly to {@link ServerChunkCache} region tickets under a dedicated HeapHammer
 * {@link TicketType}. Holds no live chunk or level references between calls.
 */
public class DirectNeoForgeTicketBridge implements NeoForgeTicketBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-neoforge-tickets");

    public static final TicketType<ChunkPos> HEAPHAMMER_TICKET = TicketType.create(
            "heaphammer",
            Comparator.comparingLong(ChunkPos::toLong)
    );

    private final Supplier<MinecraftServer> serverSupplier;

    public DirectNeoForgeTicketBridge(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = Objects.requireNonNull(serverSupplier, "serverSupplier must not be null");
    }

    @Override
    public boolean addRegionTicket(String dimension, int chunkX, int chunkZ) {
        ServerLevel level = getLevel(dimension);
        if (level == null) {
            LOGGER.warn("Cannot add region ticket: dimension '{}' is not loaded or does not exist", dimension);
            return false;
        }
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        // Distance 1 loads the chunk for processing
        level.getChunkSource().addRegionTicket(HEAPHAMMER_TICKET, pos, 1, pos);
        return true;
    }

    @Override
    public boolean removeRegionTicket(String dimension, int chunkX, int chunkZ) {
        ServerLevel level = getLevel(dimension);
        if (level == null) {
            return false;
        }
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        level.getChunkSource().removeRegionTicket(HEAPHAMMER_TICKET, pos, 1, pos);
        return true;
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
