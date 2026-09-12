package com.dwurdy.heaphammer.platform.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.world.ForgeChunkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Production MinecraftForge 1.16.5 implementation of {@link ForgeTicketBridge}
 * backed by {@link ForgeChunkManager} forced chunks under a dedicated HeapHammer
 * ticket owner UUID. Pre-1.17 Forge has no vanilla region tickets, so the opaque
 * "ticket" object is the dimension identifier marker; force/unforce map 1:1 onto
 * ForgeChunkManager calls. Holds no live chunk or level references between calls.
 */
public class DirectForgeTicketBridge implements ForgeTicketBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-forge-tickets");

    private static final UUID HEAPHAMMER_OWNER =
            UUID.fromString("7f2c3a5e-9b1d-4e8a-b6f3-0d2c1a4e5b7f");

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
        // ForgeChunkManager owns the ticket objects internally; the dimension id is the handle.
        return dimension;
    }

    @Override
    public boolean forceChunk(Object ticket, String dimension, int chunkX, int chunkZ) {
        ServerWorld level = getLevel(dimension);
        if (level == null) {
            LOGGER.warn("Cannot force chunk: dimension '{}' is not loaded", dimension);
            return false;
        }
        return ForgeChunkManager.forceChunk(level, "heaphammer", HEAPHAMMER_OWNER, chunkX, chunkZ, true, true);
    }

    @Override
    public boolean unforceChunk(Object ticket, String dimension, int chunkX, int chunkZ) {
        ServerWorld level = getLevel(dimension);
        if (level == null) {
            return false;
        }
        return ForgeChunkManager.forceChunk(level, "heaphammer", HEAPHAMMER_OWNER, chunkX, chunkZ, false, true);
    }

    @Override
    public void releaseTicket(Object ticket) {
        // No-op: ForgeChunkManager force/unforce is per-chunk; unforceChunk already
        // removes the forced-chunk entry. Nothing else to release.
    }

    @Override
    public boolean isDimensionLoaded(String dimension) {
        return getLevel(dimension) != null;
    }

    private ServerWorld getLevel(String dimension) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return null;

        ResourceLocation loc = ResourceLocation.tryParse(dimension);
        if (loc == null) return null;

        RegistryKey<World> key = RegistryKey.create(Registry.DIMENSION_REGISTRY, loc);
        return server.getLevel(key);
    }
}
