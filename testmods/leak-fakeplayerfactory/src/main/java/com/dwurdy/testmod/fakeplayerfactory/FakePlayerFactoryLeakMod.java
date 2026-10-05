package com.dwurdy.testmod.fakeplayerfactory;

import com.dwurdy.heaphammer.platform.fabric.FabricPlatformAdapter;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synthetic machinery fixture: every HeapHammer-tagged entity that loads is
 * "operated on" by a freshly constructed ServerPlayer (the deployer/breaker
 * archetype). LEAK mode retains the operator object, mirroring mods that
 * spawn FakePlayers with random UUIDs and never dispose of them. CLEAN mode
 * detaches advancement listeners and drops the reference, modelling a mod
 * that correctly disposes its operators.
 */
public final class FakePlayerFactoryLeakMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TestMod-FakePlayerFactory");
    private static final String OPERATOR_PREFIX = "hh_factory_";
    private static final CopyOnWriteArrayList<FactoryPlayerRecord> LEAKED_OPERATORS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean LEAK_ENABLED = new AtomicBoolean(false);
    private static final AtomicLong OPERATOR_COUNT = new AtomicLong();

    @Override
    public void onInitialize() {
        LOGGER.info("[TestMod-FakePlayerFactory] Initializing fake-operator factory fixture (mode=OFF).");

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!isEnabled() || entity == null || !entity.getTags().contains(FabricPlatformAdapter.TEST_ENTITY_TAG)) {
                return;
            }
            deployOperator(world);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void deployOperator(ServerLevel level) {
        // Machinery mods build operators with fresh UUIDs; sharing the real
        // player's UUID is exactly what avoids the advancement-listener leak
        // in NeoForge #1487, so a random UUID is the correct leak trigger.
        ServerPlayer operator = constructOperator(level, OPERATOR_PREFIX + (OPERATOR_COUNT.get() + 1));
        if (operator == null) {
            return;
        }
        long seq = OPERATOR_COUNT.incrementAndGet();
        if (LEAK_ENABLED.get()) {
            // Deliberately retain the whole operator: its PlayerAdvancements
            // and CriteriaTrigger listeners are pinned forever.
            LEAKED_OPERATORS.add(new FactoryPlayerRecord(operator, seq));
        } else {
            detachListeners(operator);
        }
    }

    /**
     * ServerPlayer's constructor signature changed across the supported
     * branches (ClientInformation was added in 1.20.2), so construction is
     * reflective to keep one source tree portable.
     */
    private static ServerPlayer constructOperator(ServerLevel level, String name) {
        try {
            GameProfile profile = new GameProfile(UUID.randomUUID(), name);
            try {
                Class<?> clientInfoType = Class.forName("net.minecraft.server.level.ClientInformation");
                Object clientInfo = clientInfoType.getDeclaredMethod("createDefault").invoke(null);
                Constructor<ServerPlayer> ctor = ServerPlayer.class.getDeclaredConstructor(
                        MinecraftServer.class, ServerLevel.class, GameProfile.class, clientInfoType);
                return ctor.newInstance(level.getServer(), level, profile, clientInfo);
            } catch (ReflectiveOperationException e) {
                // ClientInformation exists only on >= 1.20.2; fall back to the
                // three-argument ServerPlayer constructor on older branches.
                Constructor<ServerPlayer> ctor = ServerPlayer.class.getDeclaredConstructor(
                        MinecraftServer.class, ServerLevel.class, GameProfile.class);
                return ctor.newInstance(level.getServer(), level, profile);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[TestMod-FakePlayerFactory] Operator construction failed: {}", e.toString());
            return null;
        }
    }

    /** Models a well-behaved mod: unregister advancement listeners before dropping the operator. */
    private static void detachListeners(ServerPlayer operator) {
        try {
            Object advancements = ServerPlayer.class.getMethod("getAdvancements").invoke(operator);
            if (advancements == null) {
                // Never placed through PlayerList, so no listeners were registered.
                return;
            }
            advancements.getClass().getMethod("stopListening").invoke(advancements);
        } catch (ReflectiveOperationException ignored) {
            // Versions without stopListening simply leave the clean-path as a best-effort dispose.
        }
    }

    private static boolean isEnabled() {
        return ACTIVE.get();
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("fakeplayerfactory")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(context -> {
                            int retained = retainedOperatorCount();
                            String mode = !ACTIVE.get() ? "OFF" : (LEAK_ENABLED.get() ? "LEAK" : "CLEAN");
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "[TestMod-FakePlayerFactory] mode=%s, retained_operators=%d, deployed=%d",
                                    mode, retained, OPERATOR_COUNT.get())), false);
                            return retained;
                        }))
                        .then(Commands.literal("mode")
                                .then(Commands.literal("off").executes(context -> setMode(context, false, false, true)))
                                .then(Commands.literal("clean").executes(context -> setMode(context, false, true, true)))
                                .then(Commands.literal("leak").executes(context -> setMode(context, true, true, true))))
                        .then(Commands.literal("enable").executes(context -> setMode(context, true, true, true)))
                        .then(Commands.literal("disable").executes(context -> setMode(context, false, false, true)))
                        .then(Commands.literal("clear").executes(context -> {
                            int count = retainedOperatorCount();
                            LEAKED_OPERATORS.clear();
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "[TestMod-FakePlayerFactory] Cleared %d retained operators", count)), false);
                            return count;
                        }))
                        .then(Commands.literal("reset").executes(context -> {
                            LEAKED_OPERATORS.clear();
                            ACTIVE.set(false);
                            LEAK_ENABLED.set(false);
                            OPERATOR_COUNT.set(0L);
                            context.getSource().sendSuccess(
                                    () -> Component.literal("[TestMod-FakePlayerFactory] Reset; mode=OFF"), false);
                            return 1;
                        }))
        );
    }

    private static int setMode(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                               boolean leak, boolean active, boolean announce) {
        LEAK_ENABLED.set(leak);
        ACTIVE.set(active);
        if (!leak) {
            LEAKED_OPERATORS.clear();
        }
        if (announce) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "[TestMod-FakePlayerFactory] mode=" + (!active ? "OFF" : (leak ? "LEAK" : "CLEAN"))), false);
        }
        return 1;
    }

    public static int retainedOperatorCount() {
        return LEAKED_OPERATORS.size();
    }
}
