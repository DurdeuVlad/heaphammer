package com.dwurdy.testmod.persistententity;

import com.mojang.brigadier.CommandDispatcher;
import com.dwurdy.heaphammer.platform.fabric.FabricPlatformAdapter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synthetic persistent-entity leak fixture. It targets only HeapHammer-tagged
 * mobs, models correct unload cleanup, and deliberately omits that cleanup in
 * LEAK mode. It does not write world-store files directly.
 */
public final class PersistentEntityLeakMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TestMod-PersistentEntityLeak");
    private static final ConcurrentHashMap<UUID, PersistentEntityRecord> CLEAN_ENTITIES = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<PersistentEntityRecord> LEAKED_ENTITIES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean LEAK_ENABLED = new AtomicBoolean(false);
    private static final AtomicLong LOAD_COUNT = new AtomicLong();
    private static final AtomicLong UNLOAD_COUNT = new AtomicLong();

    @Override
    public void onInitialize() {
        LOGGER.info("[TestMod-PersistentEntityLeak] Initializing persistent-entity fixture (mode=OFF).");

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!isEnabled() || !isHeapHammerPersistentMob(entity)) {
                return;
            }
            Mob mob = (Mob) entity;
            mob.setPersistenceRequired();
            PersistentEntityRecord record = new PersistentEntityRecord(mob, world.dimension().location().toString(),
                    LOAD_COUNT.incrementAndGet());
            if (LEAK_ENABLED.get()) {
                // Deliberately omit an ENTITY_UNLOAD cleanup hook in LEAK mode.
                LEAKED_ENTITIES.add(record);
            } else {
                CLEAN_ENTITIES.put(mob.getUUID(), record);
            }
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (!isEnabled() || entity == null) {
                return;
            }
            if (entity.getTags().contains(FabricPlatformAdapter.TEST_ENTITY_TAG)) {
                UNLOAD_COUNT.incrementAndGet();
                if (!LEAK_ENABLED.get()) {
                    CLEAN_ENTITIES.remove(entity.getUUID());
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static boolean isEnabled() {
        return ACTIVE.get();
    }

    private static boolean isHeapHammerPersistentMob(Entity entity) {
        return entity != null
                && entity.getTags().contains(FabricPlatformAdapter.TEST_ENTITY_TAG)
                && entity instanceof Mob mob
                && mob.isPersistenceRequired();
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("persistententityleak")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(context -> {
                            int retained = retainedEntityCount();
                            String mode = !ACTIVE.get() ? "OFF" : (LEAK_ENABLED.get() ? "LEAK" : "CLEAN");
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "[TestMod-PersistentEntityLeak] mode=%s, retained_entities=%d, loads=%d, unloads=%d",
                                    mode, retained, LOAD_COUNT.get(), UNLOAD_COUNT.get())), false);
                            return retained;
                        }))
                        .then(Commands.literal("mode")
                                .then(Commands.literal("off").executes(context -> setMode(context, false, false, true)))
                                .then(Commands.literal("clean").executes(context -> setMode(context, false, true, true)))
                                .then(Commands.literal("leak").executes(context -> setMode(context, true, true, true))))
                        .then(Commands.literal("enable").executes(context -> setMode(context, true, true, true)))
                        .then(Commands.literal("disable").executes(context -> setMode(context, false, false, true)))
                        .then(Commands.literal("clear").executes(context -> {
                            int count = retainedEntityCount();
                            clearAll();
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "[TestMod-PersistentEntityLeak] Cleared %d retained entities", count)), false);
                            return count;
                        }))
                        .then(Commands.literal("reset").executes(context -> {
                            clearAll();
                            ACTIVE.set(false);
                            LEAK_ENABLED.set(false);
                            LOAD_COUNT.set(0L);
                            UNLOAD_COUNT.set(0L);
                            context.getSource().sendSuccess(
                                    () -> Component.literal("[TestMod-PersistentEntityLeak] Reset; mode=OFF"), false);
                            return 1;
                        }))
        );
    }

    private static int setMode(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                               boolean leak, boolean active, boolean announce) {
        LEAK_ENABLED.set(leak);
        ACTIVE.set(active);
        if (leak) {
            CLEAN_ENTITIES.clear();
        } else {
            LEAKED_ENTITIES.clear();
        }
        if (announce) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "[TestMod-PersistentEntityLeak] mode=" + (!active ? "OFF" : (leak ? "LEAK" : "CLEAN"))), false);
        }
        return 1;
    }

    public static int retainedEntityCount() {
        return CLEAN_ENTITIES.size() + LEAKED_ENTITIES.size();
    }

    public static void clearAll() {
        CLEAN_ENTITIES.clear();
        LEAKED_ENTITIES.clear();
    }
}
