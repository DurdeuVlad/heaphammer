package com.dwurdy.testmod.gazetrack;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synthetic "what is the player looking at" leak fixture. Every tick it
 * raycasts each synthetic test player's view vector; when the gaze lands on
 * another player it serializes that target (the MineChess/WAILA pattern of
 * per-tick per-target serialization). LEAK mode keeps every observation,
 * retaining the target ServerPlayer objects after they disconnect. CLEAN
 * mode keeps only the latest observation per observer:target pair.
 */
public final class GazeTrackLeakMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TestMod-GazeTrackLeak");
    private static final String TEST_NAME_PREFIX = "hh_test_";
    private static final double GAZE_RANGE_SQUARED = 32.0 * 32.0;
    /** ~10 degree cone. */
    private static final double MIN_GAZE_DOT = 0.9848;
    private static final CopyOnWriteArrayList<GazeTargetRecord> LEAKED_OBSERVATIONS = new CopyOnWriteArrayList<>();
    private static final Map<String, GazeTargetRecord> CLEAN_OBSERVATIONS = new ConcurrentHashMap<>();
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean LEAK_ENABLED = new AtomicBoolean(false);
    private static final AtomicLong OBSERVATION_COUNT = new AtomicLong();
    /** Diagnostic counters: why candidate pairs were skipped. */
    private static final AtomicLong OBSERVERS_SEEN = new AtomicLong();
    private static final AtomicLong TARGETS_IN_RANGE = new AtomicLong();
    /** Best observed gaze dot, stored as IEEE bits; compared on decoded values so negatives order correctly. */
    private static final AtomicLong BEST_DOT_BITS = new AtomicLong(
            Double.doubleToLongBits(-1.0));

    @Override
    public void onInitialize() {
        LOGGER.info("[TestMod-GazeTrackLeak] Initializing gaze-tracking retention fixture (mode=OFF).");

        ServerTickEvents.END_SERVER_TICK.register(GazeTrackLeakMod::observeGaze);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void observeGaze(MinecraftServer server) {
        if (!isEnabled()) {
            return;
        }
        if (!LEAK_ENABLED.get()) {
            // CLEAN means clean: drop observations whose observer or target has
            // left the server, the same eviction clonecache applies.
            CLEAN_OBSERVATIONS.values().removeIf(record ->
                    server.getPlayerList().getPlayer(record.observerId()) == null
                            || server.getPlayerList().getPlayer(record.targetId()) == null);
        }
        for (ServerPlayer observer : server.getPlayerList().getPlayers()) {
            if (!isTestPlayer(observer.getGameProfile().getName())) {
                continue;
            }
            OBSERVERS_SEEN.incrementAndGet();
            Vec3 eye = observer.getEyePosition();
            Vec3 look = observer.getLookAngle().normalize();
            for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                if (target == observer) {
                    continue;
                }
                Vec3 toTarget = target.getEyePosition().subtract(eye);
                double distanceSquared = toTarget.lengthSqr();
                if (distanceSquared > GAZE_RANGE_SQUARED || distanceSquared < 0.25) {
                    continue;
                }
                TARGETS_IN_RANGE.incrementAndGet();
                double dot = look.dot(toTarget.normalize());
                BEST_DOT_BITS.accumulateAndGet(Double.doubleToLongBits(dot),
                        (a, b) -> Double.longBitsToDouble(a) >= Double.longBitsToDouble(b) ? a : b);
                if (dot < MIN_GAZE_DOT) {
                    continue;
                }
                recordObservation(observer, target);
            }
        }
    }

    /**
     * Serializes the looked-at player like an observation/hover mod would,
     * then routes the record into either the bounded or the leaking store.
     */
    private static void recordObservation(ServerPlayer observer, ServerPlayer target) {
        long seq = OBSERVATION_COUNT.incrementAndGet();
        byte[] serialized = target.saveWithoutId(new CompoundTag()).toString()
                .getBytes(StandardCharsets.UTF_8);
        GazeTargetRecord record = new GazeTargetRecord(observer.getUUID(), target, serialized, seq);
        if (LEAK_ENABLED.get()) {
            LEAKED_OBSERVATIONS.add(record);
        } else {
            CLEAN_OBSERVATIONS.put(observer.getUUID() + ":" + target.getUUID(), record);
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
                Commands.literal("gazetrackleak")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(context -> {
                            int retained = retainedObservationCount();
                            String mode = !ACTIVE.get() ? "OFF" : (LEAK_ENABLED.get() ? "LEAK" : "CLEAN");
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "[TestMod-GazeTrackLeak] mode=%s, retained_observations=%d, observations=%d, "
                                            + "observers=%d, in_range=%d, best_dot=%.4f",
                                    mode, retained, OBSERVATION_COUNT.get(),
                                    OBSERVERS_SEEN.get(), TARGETS_IN_RANGE.get(),
                                    Double.longBitsToDouble(BEST_DOT_BITS.get()))), false);
                            return retained;
                        }))
                        .then(Commands.literal("mode")
                                .then(Commands.literal("off").executes(context -> setMode(context, false, false, true)))
                                .then(Commands.literal("clean").executes(context -> setMode(context, false, true, true)))
                                .then(Commands.literal("leak").executes(context -> setMode(context, true, true, true))))
                        .then(Commands.literal("enable").executes(context -> setMode(context, true, true, true)))
                        .then(Commands.literal("disable").executes(context -> setMode(context, false, false, true)))
                        .then(Commands.literal("clear").executes(context -> {
                            int count = retainedObservationCount();
                            clearAll();
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "[TestMod-GazeTrackLeak] Cleared %d retained observations", count)), false);
                            return count;
                        }))
                        .then(Commands.literal("reset").executes(context -> {
                            clearAll();
                            ACTIVE.set(false);
                            LEAK_ENABLED.set(false);
                            OBSERVATION_COUNT.set(0L);
                            OBSERVERS_SEEN.set(0L);
                            TARGETS_IN_RANGE.set(0L);
                            BEST_DOT_BITS.set(Double.doubleToLongBits(-1.0));
                            context.getSource().sendSuccess(
                                    () -> Component.literal("[TestMod-GazeTrackLeak] Reset; mode=OFF"), false);
                            return 1;
                        }))
        );
    }

    private static int setMode(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                               boolean leak, boolean active, boolean announce) {
        LEAK_ENABLED.set(leak);
        ACTIVE.set(active);
        if (leak) {
            CLEAN_OBSERVATIONS.clear();
        } else {
            LEAKED_OBSERVATIONS.clear();
        }
        if (announce) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "[TestMod-GazeTrackLeak] mode=" + (!active ? "OFF" : (leak ? "LEAK" : "CLEAN"))), false);
        }
        return 1;
    }

    public static int retainedObservationCount() {
        return CLEAN_OBSERVATIONS.size() + LEAKED_OBSERVATIONS.size();
    }

    public static void clearAll() {
        CLEAN_OBSERVATIONS.clear();
        LEAKED_OBSERVATIONS.clear();
    }
}
