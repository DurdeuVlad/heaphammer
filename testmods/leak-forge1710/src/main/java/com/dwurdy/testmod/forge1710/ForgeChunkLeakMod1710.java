package com.dwurdy.testmod.forge1710;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Disposable Forge 1.7.10 fixture that intentionally retains loaded chunks. */
@Mod(modid = "hhleak1710", name = "HeapHammer Forge Leak Fixture", version = "1.0.0")
public class ForgeChunkLeakMod1710 {
    private static final Logger LOGGER = LoggerFactory.getLogger("HHLeak-Forge1710");
    private static final List<Chunk> RETAINED_CHUNKS = new ArrayList<Chunk>();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("[HHLeak-Forge1710] enabled; retaining every loaded chunk");
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        RETAINED_CHUNKS.add(event.getChunk());
        if (RETAINED_CHUNKS.size() <= 3 || RETAINED_CHUNKS.size() % 10 == 0) {
            LOGGER.info("[HHLeak-Forge1710] retained_chunks={}", RETAINED_CHUNKS.size());
        }
    }

    public static int retainedChunkCount() {
        return RETAINED_CHUNKS.size();
    }
}
