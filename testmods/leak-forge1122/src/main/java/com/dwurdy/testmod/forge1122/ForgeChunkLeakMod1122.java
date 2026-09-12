package com.dwurdy.testmod.forge1122;

import net.minecraft.util.text.TextComponentString;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Disposable Forge 1.12.2 fixture that intentionally retains loaded chunks. */
@Mod(modid = "hhleak1122", name = "HeapHammer Forge Leak Fixture", version = "1.0.0")
public class ForgeChunkLeakMod1122 {
    private static final Logger LOGGER = LoggerFactory.getLogger("HHLeak-Forge1122");
    private static final List<Chunk> RETAINED_CHUNKS = new ArrayList<>();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("[HHLeak-Forge1122] enabled; retaining every loaded chunk");
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new StatusCommand());
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        RETAINED_CHUNKS.add(event.getChunk());
        if (RETAINED_CHUNKS.size() <= 3 || RETAINED_CHUNKS.size() % 10 == 0) {
            LOGGER.info("[HHLeak-Forge1122] retained_chunks={}", RETAINED_CHUNKS.size());
        }
    }

    public static int retainedChunkCount() {
        return RETAINED_CHUNKS.size();
    }

    private static final class StatusCommand extends CommandBase {
        @Override
        public String getName() {
            return "hhleak1122";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/hhleak1122 status";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
            sender.sendMessage(new TextComponentString(
                    "[HHLeak-Forge1122] retained_chunks=" + RETAINED_CHUNKS.size()));
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }
    }
}
