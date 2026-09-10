package com.dwurdy.heaphammer;

import com.dwurdy.heaphammer.platform.forge1710.CommandHeapHammer1710;
import com.dwurdy.heaphammer.platform.forge1710.ForgePlatformAdapter1710;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.ICommand;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Forge 1.7.10 entrypoint for HeapHammer.
 * Wires the platform adapter, command handler, and server lifecycle events.
 */
@Mod(modid = "heaphammer", name = "HeapHammer", version = "1.0.1", acceptableRemoteVersions = "*")
public class HeapHammerForge1710 {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-forge1710");

    private ForgePlatformAdapter1710 platform;
    private CommandHeapHammer1710 commandHandler;

    @EventHandler
    public void init(FMLInitializationEvent event) {
        platform = new ForgePlatformAdapter1710(this);
        commandHandler = new CommandHeapHammer1710(platform);
        FMLCommonHandler.instance().bus().register(this);
        LOGGER.info("HeapHammer v1.0.1 initialized (Minecraft 1.7.10 / Forge)");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && platform != null) {
            platform.onServerTick();
        }
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandAdapter(commandHandler));
        LOGGER.info("HeapHammer /hh command registered");
    }

    @EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        platform.setServerReady(true);
        LOGGER.info("HeapHammer server-ready signal fired");
    }

    /**
     * Adapter wrapping CommandHeapHammer1710 into Forge 1.7.10's ICommand interface.
     */
    public static class CommandAdapter implements ICommand {
        private final CommandHeapHammer1710 handler;

        public CommandAdapter(CommandHeapHammer1710 handler) {
            this.handler = handler;
        }

        @Override
        public String getCommandName() {
            return handler.getCommandName();
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return handler.getCommandUsage();
        }

        @Override
        public List<String> getCommandAliases() {
            return handler.getCommandAliases();
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            handler.processCommand(sender, args);
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) {
            return true;
        }

        @Override
        public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
            return null;
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
            return false;
        }

        @Override
        public int compareTo(Object o) {
            if (o instanceof ICommand) {
                return getCommandName().compareTo(((ICommand) o).getCommandName());
            }
            return 0;
        }
    }
}
