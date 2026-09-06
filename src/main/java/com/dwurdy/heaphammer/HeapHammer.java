package com.dwurdy.heaphammer;

import com.dwurdy.heaphammer.application.ExperimentService;
import com.dwurdy.heaphammer.application.ReplayService;
import com.dwurdy.heaphammer.command.HeapHammerCommands;
import com.dwurdy.heaphammer.detection.CleanupValidator;
import com.dwurdy.heaphammer.metrics.CheckpointService;
import com.dwurdy.heaphammer.metrics.JvmMetricsCollector;
import com.dwurdy.heaphammer.metrics.MinecraftMetricsCollector;
import com.dwurdy.heaphammer.platform.fabric.FabricPlatformAdapter;
import com.dwurdy.heaphammer.report.PlanStorage;
import com.dwurdy.heaphammer.report.ReportService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

public class HeapHammer implements ModInitializer {
	public static final String MOD_ID = "heaphammer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static MinecraftServer currentServer = null;

	private FabricPlatformAdapter platform;
	private ExperimentService experimentService;
	private CheckpointService checkpointService;
	private PlanStorage planStorage;
	private ReportService reportService;
	private ReplayService replayService;
	private HeapHammerCommands commands;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing HeapHammer (com.dwurdy.heaphammer)...");

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			currentServer = server;
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (currentServer == server) {
				if (experimentService != null) {
					experimentService.stop("Server stopping");
				}
				if (platform != null) {
					platform.getChunkTicketManager().releaseAllTickets();
				}
				currentServer = null;
			}
		});

		this.platform = new FabricPlatformAdapter(() -> currentServer);
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

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			commands.register(dispatcher);
		});

		LOGGER.info("HeapHammer commands and lifecycle registered successfully.");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
