package com.dwurdy.testmod.omnitrack;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Testmod simulating multi-subsystem memory leaks across chunk caches,
 * entity tracking registries, and server tick dispatch queues.
 */
public class OmniTrackLeakMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TestMod-OmniTrack");

    // Subsystem 1: Chunk Auditing
    private static final List<ChunkAuditRecord> CHUNK_AUDIT_LOG = new CopyOnWriteArrayList<>();

    // Subsystem 2: Entity Tracking
    private static final Map<UUID, EntityTrackingRecord> ENTITY_TRACKER = new ConcurrentHashMap<>();

    // Subsystem 3: Tick Event Buffer
    private static final TickEventBuffer TICK_BUFFER = new TickEventBuffer();

    private static final AtomicBoolean LEAK_ENABLED = new AtomicBoolean(true);
    private static final AtomicLong TICK_COUNTER = new AtomicLong(0);

    @Override
    public void onInitialize() {
        LOGGER.info("[TestMod-OmniTrack] Initializing multi-subsystem memory leak testmod.");

        // Subsystem 1: Chunk Load hook (omits CHUNK_UNLOAD)
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (LEAK_ENABLED.get() && chunk != null) {
                CHUNK_AUDIT_LOG.add(new ChunkAuditRecord(chunk.getPos(), world.dimension(), chunk));
            }
        });

        // Subsystem 2: Entity Load hook (omits ENTITY_UNLOAD)
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (LEAK_ENABLED.get()) {
                ENTITY_TRACKER.put(entity.getUUID(), new EntityTrackingRecord(entity, world.dimension()));
            }
        });

        // Subsystem 3: Tick event buffer
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (LEAK_ENABLED.get()) {
                long t = TICK_COUNTER.incrementAndGet();
                TICK_BUFFER.recordTick(t);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("omnitrack")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> {
                    int chunks = CHUNK_AUDIT_LOG.size();
                    int entities = ENTITY_TRACKER.size();
                    int ticks = TICK_BUFFER.size();
                    boolean enabled = LEAK_ENABLED.get();

                    ctx.getSource().sendSuccess(Component.literal(
                        String.format("[TestMod-OmniTrack] Status: enabled=%b, chunks=%d, entities=%d, ticks=%d",
                            enabled, chunks, entities, ticks)), false);
                    return chunks + entities + ticks;
                }))
                .then(Commands.literal("enable").executes(ctx -> {
                    LEAK_ENABLED.set(true);
                    ctx.getSource().sendSuccess(Component.literal("[TestMod-OmniTrack] Leak ENABLED"), false);
                    return 1;
                }))
                .then(Commands.literal("disable").executes(ctx -> {
                    LEAK_ENABLED.set(false);
                    ctx.getSource().sendSuccess(Component.literal("[TestMod-OmniTrack] Leak DISABLED"), false);
                    return 0;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    int chunks = CHUNK_AUDIT_LOG.size();
                    int entities = ENTITY_TRACKER.size();
                    int ticks = TICK_BUFFER.size();
                    CHUNK_AUDIT_LOG.clear();
                    ENTITY_TRACKER.clear();
                    TICK_BUFFER.clear();
                    ctx.getSource().sendSuccess(Component.literal(
                        String.format("[TestMod-OmniTrack] Cleared records (chunks=%d, entities=%d, ticks=%d)",
                            chunks, entities, ticks)), false);
                    return chunks + entities + ticks;
                }))
        );
    }

    public static int getChunkAuditCount() {
        return CHUNK_AUDIT_LOG.size();
    }

    public static int getEntityTrackerCount() {
        return ENTITY_TRACKER.size();
    }

    public static int getTickBufferSize() {
        return TICK_BUFFER.size();
    }

    public static void clearAll() {
        CHUNK_AUDIT_LOG.clear();
        ENTITY_TRACKER.clear();
        TICK_BUFFER.clear();
    }

    public static void setEnabled(boolean enabled) {
        LEAK_ENABLED.set(enabled);
    }

    public static boolean isEnabled() {
        return LEAK_ENABLED.get();
    }
}
