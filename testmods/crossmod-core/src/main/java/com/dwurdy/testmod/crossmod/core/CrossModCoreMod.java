package com.dwurdy.testmod.crossmod.core;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod A: Core event bus provider.
 * Passes clean memory checks when tested in isolation because it holds no chunk references on its own.
 */
public class CrossModCoreMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TestMod-CrossModCore");

    @Override
    public void onInitialize() {
        LOGGER.info("[TestMod-CrossModCore] Initializing Core EventBus Provider.");

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            registerCommands(dispatcher);
        });
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("crossmodcore")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> {
                    int subs = CrossModEventBus.getSubscriptionCount();
                    ctx.getSource().sendSuccess(new TextComponent(
                        String.format("[TestMod-CrossModCore] Active subscriptions: %d", subs)), false);
                    return subs;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    int subs = CrossModEventBus.getSubscriptionCount();
                    CrossModEventBus.clearAll();
                    ctx.getSource().sendSuccess(new TextComponent(
                        String.format("[TestMod-CrossModCore] Cleared %d subscriptions", subs)), false);
                    return subs;
                }))
        );
    }
}
