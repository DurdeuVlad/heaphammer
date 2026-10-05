package com.dwurdy.testmod.cachelist;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Victim fixture for the intrusive-optimizer collision archetype. It keeps a
 * lazily built chunk-metadata cache: when the cache is found empty it rebuilds
 * and appends a rebuild audit record. Alone, the cache builds once and stays
 * warm, so even LEAK mode is a stable plateau. Only when a second mod keeps
 * deleting the list does the audit log grow without bound — mirroring
 * optimizer-mod collisions such as the AllTheLeaks ListenerList rebuild leak
 * and the copycats+/ModernFix/FerriteCore blockstate-cache conflicts.
 */
public final class CacheListLeakMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TestMod-CacheListLeak");
    /** The shared list an intrusive optimizer would clear. */
    private static final List<String> CACHE = new CopyOnWriteArrayList<>();
    private static final int CLEAN_AUDIT_CAP = 256;
    private static final Deque<RebuildAuditRecord> CLEAN_AUDIT = new ArrayDeque<>();
    private static final List<RebuildAuditRecord> LEAKED_AUDIT = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean LEAK_ENABLED = new AtomicBoolean(false);
    private static final AtomicLong REBUILD_COUNT = new AtomicLong();
    private static volatile String lastClearReason = "none";

    @Override
    public void onInitialize() {
        LOGGER.info("[TestMod-CacheListLeak] Initializing rebuild-audit fixture (mode=OFF).");

        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (isEnabled() && chunk != null && CACHE.isEmpty()) {
                rebuildCache("chunk-load");
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    /**
     * Public mutation surface exposed to the antagonist optimizer fixture,
     * matching how real optimizer mods reach into foreign internals.
     */
    public static void clearCache(String reason) {
        lastClearReason = reason;
        CACHE.clear();
    }

    public static int cacheSize() {
        return CACHE.size();
    }

    private static void rebuildCache(String reason) {
        long seq = REBUILD_COUNT.incrementAndGet();
        for (int i = 0; i < 64; i++) {
            CACHE.add("entry:" + seq + ":" + i);
        }
        RebuildAuditRecord record = new RebuildAuditRecord(seq, reason + "/" + lastClearReason);
        if (LEAK_ENABLED.get()) {
            // Deliberately omit eviction: every forced rebuild is retained.
            LEAKED_AUDIT.add(record);
        } else {
            CLEAN_AUDIT.addLast(record);
            while (CLEAN_AUDIT.size() > CLEAN_AUDIT_CAP) {
                CLEAN_AUDIT.pollFirst();
            }
        }
    }

    private static boolean isEnabled() {
        return ACTIVE.get();
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("cachelistleak")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(context -> {
                            int retained = retainedAuditCount();
                            String mode = !ACTIVE.get() ? "OFF" : (LEAK_ENABLED.get() ? "LEAK" : "CLEAN");
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "[TestMod-CacheListLeak] mode=%s, cache=%d, rebuilds=%d, retained_audits=%d, last_clear=%s",
                                    mode, CACHE.size(), REBUILD_COUNT.get(), retained, lastClearReason)), false);
                            return retained;
                        }))
                        .then(Commands.literal("mode")
                                .then(Commands.literal("off").executes(context -> setMode(context, false, false, true)))
                                .then(Commands.literal("clean").executes(context -> setMode(context, false, true, true)))
                                .then(Commands.literal("leak").executes(context -> setMode(context, true, true, true))))
                        .then(Commands.literal("enable").executes(context -> setMode(context, true, true, true)))
                        .then(Commands.literal("disable").executes(context -> setMode(context, false, false, true)))
                        .then(Commands.literal("clear").executes(context -> {
                            int count = retainedAuditCount();
                            clearAll();
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "[TestMod-CacheListLeak] Cleared %d retained audits", count)), false);
                            return count;
                        }))
                        .then(Commands.literal("reset").executes(context -> {
                            clearAll();
                            CACHE.clear();
                            ACTIVE.set(false);
                            LEAK_ENABLED.set(false);
                            REBUILD_COUNT.set(0L);
                            lastClearReason = "none";
                            context.getSource().sendSuccess(
                                    () -> Component.literal("[TestMod-CacheListLeak] Reset; mode=OFF"), false);
                            return 1;
                        }))
        );
    }

    private static int setMode(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                               boolean leak, boolean active, boolean announce) {
        LEAK_ENABLED.set(leak);
        ACTIVE.set(active);
        if (leak) {
            CLEAN_AUDIT.clear();
        } else {
            LEAKED_AUDIT.clear();
        }
        if (announce) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "[TestMod-CacheListLeak] mode=" + (!active ? "OFF" : (leak ? "LEAK" : "CLEAN"))), false);
        }
        return 1;
    }

    public static int retainedAuditCount() {
        return CLEAN_AUDIT.size() + LEAKED_AUDIT.size();
    }

    public static void clearAll() {
        CLEAN_AUDIT.clear();
        LEAKED_AUDIT.clear();
    }
}
