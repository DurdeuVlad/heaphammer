package com.dwurdy.testmod.playersession;

import com.dwurdy.heaphammer.platform.PlayerLifecycleObservers;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
 * Synthetic player-session leak fixture for HeapHammer's authentic login/logout
 * workload. It uses HeapHammer's loader-neutral join observer so the fixture
 * sees the same synthetic player path as production integrations.
 */
public final class PlayerSessionLeakMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TestMod-PlayerSessionLeak");
    private static final String TEST_NAME_PREFIX = "hh_test_";
    private static final ConcurrentHashMap<UUID, PlayerSessionRecord> CLEAN_SESSIONS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<PlayerSessionRecord> LEAKED_SESSIONS = new CopyOnWriteArrayList<>();
    private static final Set<UUID> ACTIVE_TEST_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean LEAK_ENABLED = new AtomicBoolean(false);
    private static final AtomicLong JOIN_COUNT = new AtomicLong();
    private static final AtomicLong DISCONNECT_COUNT = new AtomicLong();

    @Override
    public void onInitialize() {
        LOGGER.info("[TestMod-PlayerSessionLeak] Initializing player-session retention fixture (mode=OFF).");

        PlayerLifecycleObservers.registerJoinObserver(PlayerSessionLeakMod::observeJoinedPlayer);
        // This fallback covers real Fabric client logins on runtimes that emit
        // the Fabric networking event in addition to HeapHammer's observer.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler != null) {
                observeJoinedPlayer(handler.getPlayer());
            }
        });
        // Synthetic connections do not always emit Fabric join/disconnect
        // events, so reconcile both joins and UUIDs against the authoritative
        // player list at the end of each server tick.
        ServerTickEvents.END_SERVER_TICK.register(PlayerSessionLeakMod::reconcileDisconnects);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void observeJoinedPlayer(Object value) {
        if (!isEnabled() || !(value instanceof ServerPlayer player)) {
            return;
        }
        String name = player.getGameProfile().getName();
        if (!isTestPlayer(name) || !ACTIVE_TEST_PLAYERS.add(player.getUUID())) {
            return;
        }

        PlayerSessionRecord record = new PlayerSessionRecord(player, JOIN_COUNT.incrementAndGet());
        if (LEAK_ENABLED.get()) {
            // Deliberately omit disconnect cleanup in LEAK mode.
            LEAKED_SESSIONS.add(record);
        } else {
            CLEAN_SESSIONS.put(player.getUUID(), record);
        }
    }

    private static void reconcileDisconnects(MinecraftServer server) {
        if (!isEnabled()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isTestPlayer(player.getGameProfile().getName())) {
                observeJoinedPlayer(player);
            }
        }
        if (ACTIVE_TEST_PLAYERS.isEmpty()) {
            return;
        }
        Set<UUID> connected = ConcurrentHashMap.newKeySet();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isTestPlayer(player.getGameProfile().getName())) {
                connected.add(player.getUUID());
            }
        }
        for (UUID playerId : ACTIVE_TEST_PLAYERS.toArray(new UUID[0])) {
            if (!connected.contains(playerId) && ACTIVE_TEST_PLAYERS.remove(playerId)) {
                DISCONNECT_COUNT.incrementAndGet();
                if (!LEAK_ENABLED.get()) {
                    CLEAN_SESSIONS.remove(playerId);
                }
            }
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
                Commands.literal("playersessionleak")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(context -> {
                            int retained = retainedSessionCount();
                            String mode = !ACTIVE.get() ? "OFF" : (LEAK_ENABLED.get() ? "LEAK" : "CLEAN");
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "[TestMod-PlayerSessionLeak] mode=%s, retained_sessions=%d, joins=%d, disconnects=%d",
                                    mode, retained, JOIN_COUNT.get(), DISCONNECT_COUNT.get())), false);
                            return retained;
                        }))
                        .then(Commands.literal("mode")
                                .then(Commands.literal("off").executes(context -> setMode(context, false, false, true)))
                                .then(Commands.literal("clean").executes(context -> setMode(context, false, true, true)))
                                .then(Commands.literal("leak").executes(context -> setMode(context, true, true, true))))
                        .then(Commands.literal("enable").executes(context -> setMode(context, true, true, true)))
                        .then(Commands.literal("disable").executes(context -> setMode(context, false, false, true)))
                        .then(Commands.literal("clear").executes(context -> {
                            int count = retainedSessionCount();
                            clearAll();
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "[TestMod-PlayerSessionLeak] Cleared %d retained sessions", count)), false);
                            return count;
                        }))
                        .then(Commands.literal("reset").executes(context -> {
                            clearAll();
                            ACTIVE.set(false);
                            LEAK_ENABLED.set(false);
                            JOIN_COUNT.set(0L);
                            DISCONNECT_COUNT.set(0L);
                            context.getSource().sendSuccess(
                                    () -> Component.literal("[TestMod-PlayerSessionLeak] Reset; mode=OFF"), false);
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
            LEAKED_SESSIONS.clear();
        }
        if (announce) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "[TestMod-PlayerSessionLeak] mode=" + (!active ? "OFF" : (leak ? "LEAK" : "CLEAN"))), false);
        }
        return 1;
    }

    public static int retainedSessionCount() {
        return CLEAN_SESSIONS.size() + LEAKED_SESSIONS.size();
    }

    public static void clearAll() {
        CLEAN_SESSIONS.clear();
        LEAKED_SESSIONS.clear();
        ACTIVE_TEST_PLAYERS.clear();
    }
}
