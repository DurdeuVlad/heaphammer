package com.dwurdy.testmod.crossmod.consumer;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mod B: Consumer mod for cross-mod collision testing.
 * When tested alone (Mod A absent), it unloads chunk metadata cleanly on CHUNK_UNLOAD -> PASS.
 * When Mod A is present, it subscribes uncleaned closures into Mod A's EventBus -> SUSPICIOUS.
 */
public class CrossModConsumerMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TestMod-CrossModConsumer");
    private static final String CORE_MOD_ID = "testmod-crossmod-core";

    private static final AtomicBoolean CORE_MOD_ACTIVE = new AtomicBoolean(false);
    private static final Map<ChunkPos, Long> FALLBACK_TRANSIENT_MAP = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        boolean coreLoaded = FabricLoader.getInstance().isModLoaded(CORE_MOD_ID);
        CORE_MOD_ACTIVE.set(coreLoaded);

        if (coreLoaded) {
            LOGGER.info("[TestMod-CrossModConsumer] DETECTED {}! Enabling cross-mod subscriber bridge.", CORE_MOD_ID);
        } else {
            LOGGER.info("[TestMod-CrossModConsumer] {} not detected. Running clean isolated fallback.", CORE_MOD_ID);
        }

        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (chunk != null) {
                if (CORE_MOD_ACTIVE.get()) {
                    // Collision mode: register uncleaned closure into Mod A's bus
                    ChunkListenerBridge.registerChunkSubscription(world, chunk);
                } else {
                    // Clean fallback mode: record transient timestamp
                    FALLBACK_TRANSIENT_MAP.put(chunk.getPos(), System.currentTimeMillis());
                }
            }
        });

        // In fallback mode, clean up on unload
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (!CORE_MOD_ACTIVE.get() && chunk != null) {
                FALLBACK_TRANSIENT_MAP.remove(chunk.getPos());
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("crossmodconsumer")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> {
                    boolean core = CORE_MOD_ACTIVE.get();
                    int fallbackCount = FALLBACK_TRANSIENT_MAP.size();
                    int bridgeCount = core ? ChunkListenerBridge.getActiveBridgeCount() : 0;
                    ctx.getSource().sendSuccess(Component.literal(
                        String.format("[TestMod-CrossModConsumer] CoreDetected=%b, BridgesCreated=%d, FallbackCount=%d",
                            core, bridgeCount, fallbackCount)), false);
                    return bridgeCount;
                }))
        );
    }
}
