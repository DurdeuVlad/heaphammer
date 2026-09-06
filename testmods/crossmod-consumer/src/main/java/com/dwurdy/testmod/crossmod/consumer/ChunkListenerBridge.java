package com.dwurdy.testmod.crossmod.consumer;

import com.dwurdy.testmod.crossmod.core.CrossModEventBus;
import com.dwurdy.testmod.crossmod.core.EventSubscription;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bridge class that connects to Mod A's CrossModEventBus.
 * Creates an event subscriber holding strong reference to LevelChunk and ServerLevel.
 */
public class ChunkListenerBridge {
    private static final AtomicInteger ACTIVE_BRIDGES = new AtomicInteger(0);

    public static class RetainedBridgeHolder {
        private final ChunkPos pos;
        private final ServerLevel level;
        private final LevelChunk chunk;
        private final EventSubscription subscription;
        private final byte[] statePayload = new byte[1024 * 1024];

        public RetainedBridgeHolder(ServerLevel level, LevelChunk chunk) {
            this.level = Objects.requireNonNull(level, "level must not be null");
            this.chunk = Objects.requireNonNull(chunk, "chunk must not be null");
            this.pos = chunk.getPos();

            // Register into Mod A's static EventBus
            this.subscription = CrossModEventBus.subscribe("chunk_tick_" + pos.x + "_" + pos.z, event -> {
                // Lambda closure captures 'this', retaining level, chunk, and statePayload!
                this.onBridgeEvent(event);
            });
            ACTIVE_BRIDGES.incrementAndGet();
        }

        private void onBridgeEvent(Object event) {
            // Simulated cross-mod event dispatch
        }

        public ChunkPos getPos() {
            return pos;
        }

        public LevelChunk getChunk() {
            return chunk;
        }

        public ServerLevel getLevel() {
            return level;
        }

        public EventSubscription getSubscription() {
            return subscription;
        }
    }

    public static void registerChunkSubscription(ServerLevel level, LevelChunk chunk) {
        new RetainedBridgeHolder(level, chunk);
    }

    public static int getActiveBridgeCount() {
        return ACTIVE_BRIDGES.get();
    }
}
