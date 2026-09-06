package com.dwurdy.heaphammer.platform.fabric;

import com.dwurdy.heaphammer.domain.EnvironmentFingerprint;
import com.dwurdy.heaphammer.platform.ChunkTicketManager;
import com.dwurdy.heaphammer.platform.PlatformAdapter;
import com.google.common.collect.Iterables;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Platform adapter implementing Minecraft 1.21.1 and Fabric Loader integration.
 */
public class FabricPlatformAdapter implements PlatformAdapter {
    private final Supplier<MinecraftServer> serverSupplier;
    private final FabricChunkTicketManager ticketManager;
    private final List<Consumer<Long>> tickListeners = new CopyOnWriteArrayList<>();
    private long serverTickCounter = 0;

    public FabricPlatformAdapter(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = Objects.requireNonNull(serverSupplier, "serverSupplier must not be null");
        this.ticketManager = new FabricChunkTicketManager(serverSupplier);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server == serverSupplier.get()) {
                serverTickCounter++;
                for (Consumer<Long> listener : tickListeners) {
                    try {
                        listener.accept(serverTickCounter);
                    } catch (Exception e) {
                        // Suppress listener failure from crashing the server tick
                    }
                }
            }
        });
    }

    @Override
    public ChunkTicketManager getChunkTicketManager() {
        return ticketManager;
    }

    @Override
    public EnvironmentFingerprint captureFingerprint() {
        String heapHammerVersion = FabricLoader.getInstance()
                .getModContainer("heaphammer")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("1.0.0-alpha.1");

        String mcVersion = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("1.21.1");

        String loaderVersion = FabricLoader.getInstance()
                .getModContainer("fabricloader")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("0.19.5");

        String javaVersion = System.getProperty("java.version", "21");

        MinecraftServer server = serverSupplier.get();
        long worldSeed = 0L;
        if (server != null && server.getWorldData() != null && server.getWorldData().worldGenOptions() != null) {
            worldSeed = server.getWorldData().worldGenOptions().seed();
        }

        Map<String, String> mods = new TreeMap<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            mods.put(container.getMetadata().getId(), container.getMetadata().getVersion().getFriendlyString());
        }

        String modpackHash = Integer.toHexString(mods.hashCode());

        return new EnvironmentFingerprint(
                heapHammerVersion,
                mcVersion,
                loaderVersion,
                javaVersion,
                worldSeed,
                modpackHash,
                mods
        );
    }

    @Override
    public int getLoadedChunkCount(String dimension) {
        ServerLevel level = getLevel(dimension);
        return level != null ? level.getChunkSource().getLoadedChunksCount() : 0;
    }

    @Override
    public int getTotalLoadedChunkCount() {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return 0;
        int total = 0;
        for (ServerLevel level : server.getAllLevels()) {
            total += level.getChunkSource().getLoadedChunksCount();
        }
        return total;
    }

    @Override
    public int getActiveEntityCount(String dimension) {
        ServerLevel level = getLevel(dimension);
        if (level == null) return 0;
        return Iterables.size(level.getAllEntities());
    }

    @Override
    public boolean isDimensionAvailable(String dimension) {
        return getLevel(dimension) != null;
    }

    @Override
    public void registerServerTickHook(Consumer<Long> tickConsumer) {
        tickListeners.add(Objects.requireNonNull(tickConsumer, "tickConsumer must not be null"));
    }

    @Override
    public boolean isServerReady() {
        MinecraftServer server = serverSupplier.get();
        return server != null && server.isRunning();
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
