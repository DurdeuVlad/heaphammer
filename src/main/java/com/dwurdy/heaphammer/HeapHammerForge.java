package com.dwurdy.heaphammer;

import com.dwurdy.heaphammer.application.ExperimentService;
import com.dwurdy.heaphammer.application.ReplayService;
import com.dwurdy.heaphammer.detection.CleanupValidator;
import com.dwurdy.heaphammer.metrics.CheckpointService;
import com.dwurdy.heaphammer.metrics.JvmMetricsCollector;
import com.dwurdy.heaphammer.metrics.MinecraftMetricsCollector;
import com.dwurdy.heaphammer.platform.forge.CommandHeapHammer1122;
import com.dwurdy.heaphammer.platform.forge.ForgePlatformAdapter;
import com.dwurdy.heaphammer.report.PlanStorage;
import com.dwurdy.heaphammer.report.ReportService;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

/**
 * Forge 1.12.2 mod entrypoint for HeapHammer.
 * Wires the hexagonal domain core to Forge lifecycle events and the legacy command system.
 */
@Mod(modid = HeapHammerForge.MOD_ID, name = "HeapHammer", version = "1.0.1", acceptableRemoteVersions = "*")
public class HeapHammerForge {
    public static final String MOD_ID = "heaphammer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ForgePlatformAdapter platform;
    private ExperimentService experimentService;
    private CheckpointService checkpointService;
    private PlanStorage planStorage;
    private ReportService reportService;
    private ReplayService replayService;
    private CommandHeapHammer1122 commands;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Initializing HeapHammer (com.dwurdy.heaphammer)...");

        this.platform = new ForgePlatformAdapter(this);
        this.experimentService = new ExperimentService(platform);

        JvmMetricsCollector jvmMetrics = new JvmMetricsCollector();
        MinecraftMetricsCollector mcMetrics = new MinecraftMetricsCollector(platform);
        CleanupValidator cleanupValidator = new CleanupValidator(platform);

        this.checkpointService = new CheckpointService(jvmMetrics, mcMetrics, cleanupValidator);
        this.planStorage = new PlanStorage(Paths.get("heaphammer"));
        this.reportService = new ReportService(Paths.get("heaphammer"));
        this.replayService = new ReplayService(experimentService, planStorage, reportService);

        this.commands = new CommandHeapHammer1122(
                platform,
                experimentService,
                checkpointService,
                planStorage,
                reportService,
                replayService
        );

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("HeapHammer commands and lifecycle registered successfully.");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && platform != null) {
            platform.onServerTick();
        }
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(commands);
        LOGGER.info("HeapHammer /hh command registered.");
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        if (platform != null) {
            platform.setServerReady(true);
        }
    }

    @Mod.EventHandler
    public void onServerStopped(FMLServerStoppedEvent event) {
        if (experimentService != null) {
            experimentService.stop("Server stopping");
        }
        if (platform != null) {
            platform.getChunkTicketManager().releaseAllTickets();
            platform.setServerReady(false);
        }
    }

    public CommandHeapHammer1122 getCommands() {
        return commands;
    }
}
