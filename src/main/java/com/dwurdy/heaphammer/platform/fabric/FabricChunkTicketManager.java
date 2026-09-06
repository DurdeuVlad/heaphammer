package com.dwurdy.heaphammer.platform.fabric;

import com.dwurdy.heaphammer.platform.ChunkTicketManager;
import net.minecraft.core.Registry;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Server-side chunk ticket lifecycle manager for Minecraft 1.21.1 on Fabric.
 */
public class FabricChunkTicketManager implements ChunkTicketManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-tickets");

    public static final TicketType<ChunkPos> HEAPHAMMER_TICKET = TicketType.create(
            "heaphammer",
            Comparator.comparingLong(ChunkPos::toLong)
    );

    private final Supplier<MinecraftServer> serverSupplier;
    private final Map<String, Set<Long>> activeTicketsByDimension = new ConcurrentHashMap<>();

    public FabricChunkTicketManager(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = Objects.requireNonNull(serverSupplier, "serverSupplier must not be null");
    }

    @Override
    public boolean acquireTicket(String dimension, int chunkX, int chunkZ) {
        ServerLevel level = getLevel(dimension);
        if (level == null) {
            LOGGER.warn("Cannot acquire ticket: dimension '{}' is not loaded or does not exist", dimension);
            return false;
        }

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        ServerChunkCache chunkSource = level.getChunkSource();
        // Distance 1 loads the chunk for processing
        chunkSource.addRegionTicket(HEAPHAMMER_TICKET, pos, 1, pos);

        activeTicketsByDimension
                .computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet())
                .add(pos.toLong());
        return true;
    }

    @Override
    public boolean releaseTicket(String dimension, int chunkX, int chunkZ) {
        Set<Long> set = activeTicketsByDimension.get(dimension);
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        long key = pos.toLong();

        if (set == null || !set.remove(key)) {
            return false;
        }

        ServerLevel level = getLevel(dimension);
        if (level != null) {
            ServerChunkCache chunkSource = level.getChunkSource();
            chunkSource.removeRegionTicket(HEAPHAMMER_TICKET, pos, 1, pos);
        }
        return true;
    }

    @Override
    public int getActiveTicketCount() {
        return activeTicketsByDimension.values().stream().mapToInt(Set::size).sum();
    }

    @Override
    public Set<Long> getActiveTicketChunkKeys(String dimension) {
        Set<Long> set = activeTicketsByDimension.get(dimension);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(set));
    }

    @Override
    public void releaseAllTickets() {
        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            activeTicketsByDimension.clear();
            return;
        }

        for (Map.Entry<String, Set<Long>> entry : activeTicketsByDimension.entrySet()) {
            String dimension = entry.getKey();
            Set<Long> keys = entry.getValue();
            ServerLevel level = getLevel(dimension);
            if (level != null) {
                ServerChunkCache chunkSource = level.getChunkSource();
                for (Long key : keys) {
                    ChunkPos pos = new ChunkPos(key);
                    chunkSource.removeRegionTicket(HEAPHAMMER_TICKET, pos, 1, pos);
                }
            }
            keys.clear();
        }
        activeTicketsByDimension.clear();
        LOGGER.info("All HeapHammer chunk tickets have been released.");
    }

    private ServerLevel getLevel(String dimension) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return null;

        ResourceLocation loc = ResourceLocation.tryParse(dimension);
        if (loc == null) return null;

        ResourceKey<Level> key = ResourceKey.create(Registry.DIMENSION_REGISTRY, loc);
        return server.getLevel(key);
    }
}
