package com.dwurdy.heaphammer;

import com.dwurdy.heaphammer.application.ExperimentService;
import com.dwurdy.heaphammer.application.ReplayService;
import com.dwurdy.heaphammer.command.HeapHammerCommands;
import com.dwurdy.heaphammer.detection.CleanupValidator;
import com.dwurdy.heaphammer.metrics.CheckpointService;
import com.dwurdy.heaphammer.metrics.JvmMetricsCollector;
import com.dwurdy.heaphammer.metrics.MinecraftMetricsCollector;
import com.dwurdy.heaphammer.platform.forge.DirectForgePlatformAdapter;
import com.dwurdy.heaphammer.platform.forge.DirectForgeTicketBridge;
import com.dwurdy.heaphammer.platform.forge.ForgeChunkTicketManager;
import com.dwurdy.heaphammer.report.PlanStorage;
import com.dwurdy.heaphammer.report.ReportService;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fmlserverevents.FMLServerStartingEvent;
import net.minecraftforge.fmlserverevents.FMLServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

/**
 * MinecraftForge 1.17-1.20.1 entry point. Mirrors the Fabric {@code HeapHammer}
 * initializer, wiring the shared domain services onto the Forge game event bus.
 */
@Mod(HeapHammerForge.MOD_ID)
public class HeapHammerForge {
	public static final String MOD_ID = "heaphammer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private volatile MinecraftServer currentServer = null;

	private final DirectForgePlatformAdapter platform;
	private final ExperimentService experimentService;
	private final CheckpointService checkpointService;
	private final PlanStorage planStorage;
	private final ReportService reportService;
	private final ReplayService replayService;
	private final HeapHammerCommands commands;

	public HeapHammerForge() {
		LOGGER.info("Initializing HeapHammer (com.dwurdy.heaphammer, Forge)...");


		DirectForgeTicketBridge bridge = new DirectForgeTicketBridge(() -> currentServer);
		ForgeChunkTicketManager ticketManager = new ForgeChunkTicketManager(bridge);
		this.platform = new DirectForgePlatformAdapter(ticketManager, () -> currentServer);
		this.experimentService = new ExperimentService(platform);

		JvmMetricsCollector jvmMetrics = new JvmMetricsCollector();
		MinecraftMetricsCollector mcMetrics = new MinecraftMetricsCollector(platform);
		CleanupValidator cleanupValidator = new CleanupValidator(platform);

		this.checkpointService = new CheckpointService(jvmMetrics, mcMetrics, cleanupValidator);
		this.planStorage = new PlanStorage(Paths.get("heaphammer"));
		this.reportService = new ReportService(Paths.get("heaphammer"));
		this.replayService = new ReplayService(experimentService, planStorage, reportService);

		this.commands = new HeapHammerCommands(
				platform,
				experimentService,
				checkpointService,
				planStorage,
				reportService,
				replayService
		);

		IEventBus gameBus = MinecraftForge.EVENT_BUS;

		gameBus.addListener((FMLServerStartingEvent event) -> {
			currentServer = event.getServer();
			platform.setServerReady(true);
		});

		gameBus.addListener((FMLServerStoppingEvent event) -> {
			if (currentServer == event.getServer()) {
				experimentService.stop("Server stopping");
				platform.getChunkTicketManager().releaseAllTickets();
				platform.setServerReady(false);
				currentServer = null;
			}
		});

		gameBus.addListener((TickEvent.ServerTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				platform.onServerTick();
			}
		});

		gameBus.addListener((RegisterCommandsEvent event) -> commands.register(event.getDispatcher()));

		LOGGER.info("HeapHammer commands and lifecycle registered successfully (Forge).");
	}
}
