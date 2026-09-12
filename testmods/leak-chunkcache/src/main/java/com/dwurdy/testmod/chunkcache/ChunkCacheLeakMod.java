package com.dwurdy.testmod.chunkcache;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Testmod simulating a single-subsystem chunk cache memory leak.
 * Hooks ServerChunkEvents.CHUNK_LOAD and stores LevelChunk in static map,
 * deliberately failing to listen to CHUNK_UNLOAD.
 */
public class ChunkCacheLeakMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TestMod-ChunkCache");
    private static final Map<ChunkPos, RetainedChunkEntry> CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean LEAK_ENABLED = new AtomicBoolean(true);

    @Override
    public void onInitialize() {
        LOGGER.info("[TestMod-ChunkCache] Initializing single-subsystem chunk cache leak mod.");

        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (LEAK_ENABLED.get() && chunk instanceof LevelChunk) {
                LevelChunk levelChunk = (LevelChunk) chunk;
                ChunkPos pos = levelChunk.getPos();
                CACHE.put(pos, new RetainedChunkEntry(pos, world.dimension(), levelChunk));
                LOGGER.debug("[TestMod-ChunkCache] Cached chunk at {} (Total cached: {})", pos, CACHE.size());
            }
        });

        // Deliberately DO NOT register ServerChunkEvents.CHUNK_UNLOAD!
        // This causes genuine LevelChunk retention in static memory.

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            registerCommands(dispatcher);
        });
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("chunkcacheleak")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> {
                    int count = CACHE.size();
                    boolean enabled = LEAK_ENABLED.get();
                    ctx.getSource().sendSuccess(new TextComponent(
                        String.format("[TestMod-ChunkCache] Status: enabled=%b, cached_chunks=%d",
                            enabled, count)), false);
                    return count;
                }))
                .then(Commands.literal("enable").executes(ctx -> {
                    LEAK_ENABLED.set(true);
                    ctx.getSource().sendSuccess(new TextComponent("[TestMod-ChunkCache] Leak ENABLED"), false);
                    return 1;
                }))
                .then(Commands.literal("disable").executes(ctx -> {
                    LEAK_ENABLED.set(false);
                    ctx.getSource().sendSuccess(new TextComponent("[TestMod-ChunkCache] Leak DISABLED"), false);
                    return 0;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    int size = CACHE.size();
                    CACHE.clear();
                    ctx.getSource().sendSuccess(new TextComponent(
                        String.format("[TestMod-ChunkCache] Cleared %d cached chunks", size)), false);
                    return size;
                }))
        );
    }

    public static int getCachedCount() {
        return CACHE.size();
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static void setEnabled(boolean enabled) {
        LEAK_ENABLED.set(enabled);
    }

    public static boolean isEnabled() {
        return LEAK_ENABLED.get();
    }
}
