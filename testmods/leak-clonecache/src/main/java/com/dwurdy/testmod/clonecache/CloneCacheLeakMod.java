package com.dwurdy.testmod.clonecache;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synthetic clone-boundary retention fixture. On every ServerPlayer clone
 * (respawn, end-return) a mod is supposed to re-key or invalidate state that
 * referenced the old player instance. CLEAN mode replaces the record keyed by
 * the surviving UUID; LEAK mode keeps the pre-clone ServerPlayer in an
 * append-only list, matching the "old player still in the map" bug class.
 */
public final class CloneCacheLeakMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TestMod-CloneCacheLeak");
    private static final String TEST_NAME_PREFIX = "hh_test_";
    private static final ConcurrentHashMap<UUID, CloneSessionRecord> CLEAN_SESSIONS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<CloneSessionRecord> LEAKED_CLONES = new CopyOnWriteArrayList<>();
    private static final Set<UUID> ACTIVE_TEST_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean LEAK_ENABLED = new AtomicBoolean(false);
    private static final AtomicLong CLONE_COUNT = new AtomicLong();

    @Override
    public void onInitialize() {
        LOGGER.info("[TestMod-CloneCacheLeak] Initializing clone-boundary retention fixture (mode=OFF).");

        // The clone boundary itself: fires for respawn and end-credit returns.
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> observeClone(oldPlayer, newPlayer));
        // Reconcile evicts CLEAN entries once a test player actually leaves.
        ServerTickEvents.END_SERVER_TICK.register(CloneCacheLeakMod::reconcileDisconnects);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void observeClone(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        if (!isEnabled() || oldPlayer == null || newPlayer == null) {
            return;
        }
        if (!isTestPlayer(newPlayer.getGameProfile().getName())) {
            return;
        }
        ACTIVE_TEST_PLAYERS.add(newPlayer.getUUID());
        long seq = CLONE_COUNT.incrementAndGet();
        if (LEAK_ENABLED.get()) {
            // Deliberately retain the pre-clone player instance.
            LEAKED_CLONES.add(new CloneSessionRecord(oldPlayer, seq));
        } else {
            // Same UUID across respawn, so the map self-bounds to live players.
            CLEAN_SESSIONS.put(newPlayer.getUUID(), new CloneSessionRecord(newPlayer, seq));
        }
    }

    private static void reconcileDisconnects(MinecraftServer server) {
        if (!isEnabled()) {
            return;
        }
        Set<UUID> connected = ConcurrentHashMap.newKeySet();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isTestPlayer(player.getGameProfile().getName())) {
                connected.add(player.getUUID());
            }
        }
        ACTIVE_TEST_PLAYERS.retainAll(connected);
        if (!LEAK_ENABLED.get()) {
            CLEAN_SESSIONS.keySet().retainAll(connected);
        }
    }

    private static boolean isEnabled() {
        return ACTIVE.get();
    }

    private static boolean isTestPlayer(String name) {
        return name != null && name.startsWith(TEST_NAME_PREFIX);
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("clonecacheleak")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(context -> {
                            int retained = retainedCloneCount();
                            String mode = !ACTIVE.get() ? "OFF" : (LEAK_ENABLED.get() ? "LEAK" : "CLEAN");
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "[TestMod-CloneCacheLeak] mode=%s, retained_clones=%d, clones=%d",
                                    mode, retained, CLONE_COUNT.get())), false);
                            return retained;
                        }))
                        .then(Commands.literal("mode")
                                .then(Commands.literal("off").executes(context -> setMode(context, false, false, true)))
                                .then(Commands.literal("clean").executes(context -> setMode(context, false, true, true)))
                                .then(Commands.literal("leak").executes(context -> setMode(context, true, true, true))))
                        .then(Commands.literal("enable").executes(context -> setMode(context, true, true, true)))
                        .then(Commands.literal("disable").executes(context -> setMode(context, false, false, true)))
                        .then(Commands.literal("clear").executes(context -> {
                            int count = retainedCloneCount();
                            clearAll();
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "[TestMod-CloneCacheLeak] Cleared %d retained clones", count)), false);
                            return count;
                        }))
                        .then(Commands.literal("reset").executes(context -> {
                            clearAll();
                            ACTIVE.set(false);
                            LEAK_ENABLED.set(false);
                            CLONE_COUNT.set(0L);
                            context.getSource().sendSuccess(
                                    () -> Component.literal("[TestMod-CloneCacheLeak] Reset; mode=OFF"), false);
                            return 1;
                        }))
        );
    }

    private static int setMode(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                               boolean leak, boolean active, boolean announce) {
        LEAK_ENABLED.set(leak);
        ACTIVE.set(active);
        if (leak) {
            CLEAN_SESSIONS.clear();
        } else {
            LEAKED_CLONES.clear();
        }
        if (announce) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "[TestMod-CloneCacheLeak] mode=" + (!active ? "OFF" : (leak ? "LEAK" : "CLEAN"))), false);
        }
        return 1;
    }

    public static int retainedCloneCount() {
        return CLEAN_SESSIONS.size() + LEAKED_CLONES.size();
    }

    public static void clearAll() {
        CLEAN_SESSIONS.clear();
        LEAKED_CLONES.clear();
        ACTIVE_TEST_PLAYERS.clear();
    }
}
