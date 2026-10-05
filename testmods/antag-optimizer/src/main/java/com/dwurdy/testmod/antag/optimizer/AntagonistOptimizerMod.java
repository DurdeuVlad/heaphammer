package com.dwurdy.testmod.antag.optimizer;

import com.dwurdy.testmod.cachelist.CacheListLeakMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Intrusive-optimizer antagonist fixture: every interval ticks it clears the
 * victim mod's internal cache list, the way optimization mods forcibly evict
 * or rebuild foreign state (ModernFix/FerriteCore blockstate caches,
 * AllTheLeaks listener-list rebuilds). Alone it is a no-op; paired with
 * testmod-leak-cachelist it turns every cache clear into an unbounded
 * rebuild-audit growth — the second cross-mod collision axis.
 */
public final class AntagonistOptimizerMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TestMod-AntagOptimizer");
    private static final String VICTIM_MOD_ID = "testmod-leak-cachelist";
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final AtomicBoolean VICTIM_PRESENT = new AtomicBoolean(false);
    private static final AtomicInteger INTERVAL_TICKS = new AtomicInteger(50);
    private static final AtomicLong CLEAR_COUNT = new AtomicLong();
    private static long tickCounter;

    @Override
    public void onInitialize() {
        boolean victimLoaded = FabricLoader.getInstance().isModLoaded(VICTIM_MOD_ID);
        VICTIM_PRESENT.set(victimLoaded);
        LOGGER.info("[TestMod-AntagOptimizer] Initializing intrusive-optimizer fixture ({} detected: {}).",
                VICTIM_MOD_ID, victimLoaded);

        ServerTickEvents.END_SERVER_TICK.register(AntagonistOptimizerMod::sweep);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void sweep(MinecraftServer server) {
        if (!ACTIVE.get() || !VICTIM_PRESENT.get()) {
            return;
        }
        if (++tickCounter % INTERVAL_TICKS.get() == 0L) {
            clearVictimCache("interval-sweep");
        }
    }

    private static void clearVictimCache(String reason) {
        try {
            CacheListLeakMod.clearCache(reason);
            CLEAR_COUNT.incrementAndGet();
        } catch (Throwable ignored) {
            // Absent or renamed victim: an optimizer failing silently is the
            // honest standalone behavior.
        }
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("antagoptimizer")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "[TestMod-AntagOptimizer] active=%s, victim=%s, interval=%d, clears=%d",
                                    ACTIVE.get(), VICTIM_PRESENT.get(), INTERVAL_TICKS.get(), CLEAR_COUNT.get())), false);
                            return (int) CLEAR_COUNT.get();
                        }))
                        .then(Commands.literal("on").executes(context -> setActive(context, true)))
                        .then(Commands.literal("off").executes(context -> setActive(context, false)))
                        .then(Commands.literal("once").executes(context -> {
                            clearVictimCache("manual-once");
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "[TestMod-AntagOptimizer] Cleared victim cache once"), false);
                            return 1;
                        }))
                        .then(Commands.literal("interval")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            int ticks = IntegerArgumentType.getInteger(context, "ticks");
                                            INTERVAL_TICKS.set(ticks);
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    "[TestMod-AntagOptimizer] interval=" + ticks), false);
                                            return ticks;
                                        })))
                        .then(Commands.literal("reset").executes(context -> {
                            ACTIVE.set(false);
                            CLEAR_COUNT.set(0L);
                            tickCounter = 0L;
                            context.getSource().sendSuccess(
                                    () -> Component.literal("[TestMod-AntagOptimizer] Reset; mode=OFF"), false);
                            return 1;
                        }))
        );
    }

    private static int setActive(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                 boolean active) {
        ACTIVE.set(active);
        context.getSource().sendSuccess(() -> Component.literal(
                "[TestMod-AntagOptimizer] active=" + active), false);
        return 1;
    }
}
