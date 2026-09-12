package com.dwurdy.heaphammer;

import com.dwurdy.heaphammer.application.ExperimentService;
import com.dwurdy.heaphammer.application.ReplayService;
import com.dwurdy.heaphammer.command.HeapHammerCommands;
import com.dwurdy.heaphammer.detection.CleanupValidator;
import com.dwurdy.heaphammer.infrastructure.config.ConfigManager;
import com.dwurdy.heaphammer.infrastructure.config.HeapHammerConfig;
import com.dwurdy.heaphammer.metrics.CheckpointService;
import com.dwurdy.heaphammer.metrics.JvmMetricsCollector;
import com.dwurdy.heaphammer.metrics.MinecraftMetricsCollector;
import com.dwurdy.heaphammer.platform.neoforge.DirectNeoForgePlatformAdapter;
import com.dwurdy.heaphammer.platform.neoforge.DirectNeoForgeTicketBridge;
import com.dwurdy.heaphammer.platform.neoforge.NeoForgeChunkTicketManager;
import com.dwurdy.heaphammer.report.PlanStorage;
import com.dwurdy.heaphammer.report.ReportService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

/**
 * NeoForge 1.21.x entry point. Mirrors the Fabric {@code HeapHammer} initializer,
 * wiring the shared domain services onto the NeoForge game event bus.
 */
@Mod(HeapHammerNeoForge.MOD_ID)
public class HeapHammerNeoForge {
	public static final String MOD_ID = "heaphammer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private volatile MinecraftServer currentServer = null;

	private final DirectNeoForgePlatformAdapter platform;
	private final ExperimentService experimentService;
	private final CheckpointService checkpointService;
	private final PlanStorage planStorage;
	private final ReportService reportService;
	private final ReplayService replayService;
	private final HeapHammerCommands commands;

	public HeapHammerNeoForge() {
		LOGGER.info("Initializing HeapHammer (com.dwurdy.heaphammer, NeoForge)...");

		// Load or generate configuration from config/heaphammer.json
		ConfigManager.load(Paths.get(HeapHammerConfig.DEFAULT_CONFIG_PATH));

		DirectNeoForgeTicketBridge bridge = new DirectNeoForgeTicketBridge(() -> currentServer);
		NeoForgeChunkTicketManager ticketManager = new NeoForgeChunkTicketManager(bridge);
		this.platform = new DirectNeoForgePlatformAdapter(ticketManager, () -> currentServer);
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

		IEventBus gameBus = NeoForge.EVENT_BUS;

		gameBus.addListener(ServerStartingEvent.class, event -> {
			currentServer = event.getServer();
			platform.setServerReady(true);
		});

		gameBus.addListener(ServerStartedEvent.class, event -> {
			boolean autoCleanup = ConfigManager.getActiveConfig().isAutoCleanupOnStartup();
			if (autoCleanup || experimentService.getRecoveryJournal().hasInterruptedRun()) {
				LOGGER.info("Executing startup failure recovery and orphaned test state sweep...");
				int cleaned = experimentService.getRecoveryJournal().recoverIfInterrupted(platform);
				if (cleaned > 0) {
					LOGGER.info("Startup recovery successfully cleaned up {} leftover test objects.", cleaned);
				}
			}
		});

		gameBus.addListener(ServerStoppingEvent.class, event -> {
			if (currentServer == event.getServer()) {
				experimentService.stop("Server stopping");
				platform.getChunkTicketManager().releaseAllTickets();
				platform.setServerReady(false);
				currentServer = null;
			}
		});

		gameBus.addListener(ServerTickEvent.Post.class, event -> platform.onServerTick());

		gameBus.addListener(RegisterCommandsEvent.class, event -> commands.register(event.getDispatcher()));

		LOGGER.info("HeapHammer commands and lifecycle registered successfully (NeoForge).");
	}
}
