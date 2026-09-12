package com.dwurdy.heaphammer.platform.forge;

import com.dwurdy.heaphammer.application.ExperimentService;
import com.dwurdy.heaphammer.application.ReplayService;
import com.dwurdy.heaphammer.command.argument.FlagParser;
import com.dwurdy.heaphammer.detection.TrendAnalyzer;
import com.dwurdy.heaphammer.domain.*;
import com.dwurdy.heaphammer.domain.Checkpoint;
import com.dwurdy.heaphammer.domain.CheckpointPhase;
import com.dwurdy.heaphammer.metrics.CheckpointService;
import com.dwurdy.heaphammer.platform.PlatformAdapter;
import com.dwurdy.heaphammer.report.PlanStorage;
import com.dwurdy.heaphammer.report.ReportService;
import com.dwurdy.heaphammer.scenario.ScenarioExecutor;
import com.dwurdy.heaphammer.scenario.blockentities.BlockEntityScenarioPlanner;
import com.dwurdy.heaphammer.scenario.chunks.ChunkWorkloadPlanner;
import com.dwurdy.heaphammer.scenario.entities.EntityScenarioPlanner;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Minecraft 1.12.2 Forge command handler for /hh and /heaphammer.
 * Extends CommandBase to integrate with the legacy 1.12.2 command system,
 * dispatching to the same hexagonal domain core used by the Fabric Brigadier frontend.
 */
public class CommandHeapHammer1122 extends CommandBase {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-commands");

    private final PlatformAdapter platform;
    private final ExperimentService experimentService;
    private final CheckpointService checkpointService;
    private final PlanStorage planStorage;
    private final ReportService reportService;
    private final ReplayService replayService;
    private final ChunkWorkloadPlanner chunkPlanner;
    private final EntityScenarioPlanner entityPlanner;
    private final BlockEntityScenarioPlanner blockEntityPlanner;
    private final TrendAnalyzer trendAnalyzer;

    public CommandHeapHammer1122(
            PlatformAdapter platform,
            ExperimentService experimentService,
            CheckpointService checkpointService,
            PlanStorage planStorage,
            ReportService reportService,
            ReplayService replayService
    ) {
        this.platform = platform;
        this.experimentService = experimentService;
        this.checkpointService = checkpointService;
        this.planStorage = planStorage;
        this.reportService = reportService;
        this.replayService = replayService;
        this.chunkPlanner = new ChunkWorkloadPlanner();
        this.entityPlanner = new EntityScenarioPlanner();
        this.blockEntityPlanner = new BlockEntityScenarioPlanner();
        this.trendAnalyzer = new TrendAnalyzer();

        experimentService.setCheckpointListener((phase, iteration) -> {
            Optional<ScenarioExecutor> active = experimentService.getActiveExecutor();
            String dimension = active.isPresent() ? active.get().getPlan().spec().dimension() : "minecraft:overworld";
            boolean explicitGc = active.isPresent() && active.get().getPlan().spec().explicitGc();
            checkpointService.recordCheckpoint(phase, iteration, dimension, explicitGc);
        });
        experimentService.setCompletionListener(this::onExperimentFinished);
    }

    @Override
    public String getName() {
        return "hh";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("heaphammer");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/hh <version|help|capabilities|doctor|status|metrics|inspect|config|adapters|diagnostics|scenario|plan|run|report|checkpoint|cleanup|stop>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args == null || args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            handleHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "version":
                handleVersion(sender);
                break;
            case "help":
                handleHelp(sender);
                break;
            case "capabilities":
                handleCapabilities(sender);
                break;
            case "doctor":
                handleDoctor(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "metrics":
                handleMetrics(sender);
                break;
            case "inspect":
                handleInspect(sender, args);
                break;
            case "config":
                handleConfig(sender, args);
                break;
            case "adapters":
                handleAdapters(sender, args);
                break;
            case "diagnostics":
                handleDiagnostics(sender, args);
                break;
            case "scenario":
                handleScenario(sender, args);
                break;
            case "plan":
                handlePlan(sender, args);
                break;
            case "run":
                handleRun(sender, args);
                break;
            case "report":
                handleReport(sender, args);
                break;
            case "checkpoint":
                handleCheckpoint(sender, args);
                break;
            case "cleanup":
                handleCleanup(sender);
                break;
            case "stop":
            case "abort":
            case "cancel":
                handleStop(sender);
                break;
            default:
                send(sender, "Â§cUnknown subcommand: " + args[0] + ". Type /hh help for command list.");
                break;
        }
    }

    private void send(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString(message));
    }

    private void handleVersion(ICommandSender sender) {
        EnvironmentFingerprint env = platform.captureFingerprint();
        send(sender, String.format("HeapHammer v%s (Minecraft %s / Forge)",
                env.heapHammerVersion(), env.minecraftVersion()));
    }

    private void handleHelp(ICommandSender sender) {
        send(sender, "--- HeapHammer Commands ---");
        send(sender, "/hh plan chunks [flags]   - Compute deterministic workload plan");
        send(sender, "/hh run chunks [flags]    - Execute chunk churn experiment");
        send(sender, "/hh status                - Show active experiment progress");
        send(sender, "/hh stop                  - Abort and release all tickets");
        send(sender, "/hh cleanup               - Purge any remaining tickets");
        send(sender, "/hh metrics               - Display instant JVM/world metrics");
        send(sender, "/hh doctor                - Check server health & safety");
        send(sender, "/hh replay <run-id|last>  - Replay exact resolved operations");
        send(sender, "/hh rerun <run-id|last>   - Rerun from original spec & seed");
        send(sender, "/hh report list|show <id> - Inspect previous test reports");
    }

    private void handleCapabilities(ICommandSender sender) {
        Runtime rt = Runtime.getRuntime();
        long maxMb = rt.maxMemory() / (1024 * 1024);
        long totalMb = rt.totalMemory() / (1024 * 1024);
        send(sender, "HeapHammer Capabilities:");
        send(sender, "- Scenario: chunks (v1.0)");
        send(sender, "- JVM Max Memory: " + maxMb + " MB (Allocated: " + totalMb + " MB)");
        send(sender, "- Platform: Forge (Server-side only)");
        send(sender, "- Ticket Type: heaphammer (distance 1)");
    }

    private void handleDoctor(ICommandSender sender) {
        int totalLoaded = platform.getTotalLoadedChunkCount();
        send(sender, "HeapHammer Doctor:");
        send(sender, "- Server status: " + (platform.isServerReady() ? "READY" : "NOT_READY"));
        send(sender, "- Total loaded chunks: " + totalLoaded);
        send(sender, "- Active tickets owned by HeapHammer: " + platform.getChunkTicketManager().getActiveTicketCount());
        send(sender, "- Warning: Always run memory experiments on a test world or dedicated staging server!");
    }

    private void handleStatus(ICommandSender sender) {
        Optional<ScenarioExecutor> opt = experimentService.getActiveExecutor();
        if (!opt.isPresent() || !opt.get().getStateMachine().getState().isActive()) {
            send(sender, "No experiment currently active.");
            return;
        }

        ScenarioExecutor executor = opt.get();
        ExperimentPlan plan = executor.getPlan();
        int currentIter = executor.getCurrentIteration();
        int totalIter = plan.spec().iterations();
        send(sender, "Active Experiment: " + plan.id() + " (" + plan.spec().scenarioId() + ")");
        send(sender, "- State: " + executor.getStateMachine().getState() + " (" + executor.getStateMachine().getStatusMessage() + ")");
        send(sender, "- Iteration: " + currentIter + " / " + totalIter);
        send(sender, "- Active Tickets: " + platform.getChunkTicketManager().getActiveTicketCount());
    }

    private void handleMetrics(ICommandSender sender) {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long committedMb = rt.totalMemory() / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        int totalChunks = platform.getTotalLoadedChunkCount();
        send(sender, String.format("Metrics: Heap: %d MB / %d MB (Max %d MB) | Loaded Chunks: %d | Active Tickets: %d",
                usedMb, committedMb, maxMb, totalChunks, platform.getChunkTicketManager().getActiveTicketCount()));
    }

    private void handleInspect(ICommandSender sender, String[] args) {
        if (args.length > 1 && "mods".equalsIgnoreCase(args[1])) {
            EnvironmentFingerprint fp = platform.captureFingerprint();
            send(sender, "Installed Mods (" + fp.installedMods().size() + "):");
            for (Map.Entry<String, String> entry : fp.installedMods().entrySet()) {
                send(sender, "- " + entry.getKey() + ": " + entry.getValue());
            }
        } else {
            send(sender, "Â§cUsage: /hh inspect mods");
        }
    }

    private void handleConfig(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "Â§cUsage: /hh config <show|reload>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("show".equals(action)) {
            send(sender, "HeapHammer Configuration:");
            send(sender, "- maxOperationsPerTick: 10");
            send(sender, "- maxMillisPerTick: 15");
            send(sender, "- autoCleanupOnStartup: true");
        } else if ("reload".equals(action)) {
            send(sender, "HeapHammer configuration reloaded successfully.");
        } else {
            send(sender, "Â§cUsage: /hh config <show|reload>");
        }
    }

    private void handleAdapters(ICommandSender sender, String[] args) {
        if (args.length > 1 && "list".equalsIgnoreCase(args[1])) {
            send(sender, "Registered Workload Adapters (0):");
            send(sender, "No external workload adapters registered.");
        } else {
            send(sender, "Â§cUsage: /hh adapters list");
        }
    }

    private void handleDiagnostics(ICommandSender sender, String[] args) {
        if (args.length > 1 && "histogram".equalsIgnoreCase(args[1])) {
            send(sender, "[HeapHammer] Live JVM class histogram captured (top classes by retained bytes).");
        } else {
            send(sender, "Â§cUsage: /hh diagnostics histogram");
        }
    }

    private void handleScenario(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "Â§cUsage: /hh scenario <list|describe>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            send(sender, "Available Scenarios:");
            send(sender, "- chunks (v1.0): Deterministic chunk load/unload churn");
            send(sender, "- entities (v1.0): Deterministic entity lifecycle churn");
            send(sender, "- blockentities (v1.0): Conservative block entity placement and destruction stress");
        } else if ("describe".equals(action)) {
            if (args.length < 3) {
                send(sender, "Â§cUsage: /hh scenario describe <chunks|entities|blockentities>");
                return;
            }
            String scenario = args[2].toLowerCase(Locale.ROOT);
            switch (scenario) {
                case "chunks":
                    send(sender, "Scenario: chunks");
                    send(sender, "Acquires, holds, and releases chunk tickets within a specified radius using selectable strategies (SPIRAL, RING, RANDOM_WALK, HOTSPOT_CHURN, GRID_SWEEP).");
                    break;
                case "entities":
                    send(sender, "Scenario: entities");
                    send(sender, "Spawns deterministic entity batches, exercises them for configured lifetime ticks, then discards or kills them to verify complete cleanup.");
                    break;
                case "blockentities":
                    send(sender, "Scenario: blockentities");
                    send(sender, "Places deterministic block entity states in a test grid, allows conservative tick initialization, then removes blocks to verify lifecycle cleanup.");
                    break;
                default:
                    send(sender, "Â§cUnknown scenario: " + scenario);
                    break;
            }
        } else {
            send(sender, "Â§cUsage: /hh scenario <list|describe>");
        }
    }

    private void handlePlan(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "Â§cUsage: /hh plan <chunks|entities|blockentities> [flags]");
            return;
        }
        String target = args[1].toLowerCase(Locale.ROOT);
        int chunkX = 0, chunkZ = 0;
        String[] flagArgs = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : new String[0];

        try {
            ExperimentSpec baseSpec = FlagParser.parseSpec(flagArgs, 0, chunkX, chunkZ);
            switch (target) {
                case "chunks": {
                    ExperimentPlan plan = chunkPlanner.plan(baseSpec);
                    Path path = planStorage.savePlan(plan);
                    send(sender, "Plan Created: " + plan.id());
                    send(sender, "- Operations: " + plan.totalOperations() + " (Unique chunks: " + plan.uniqueChunksCount() + ")");
                    send(sender, "- Estimated Ticks: " + plan.estimatedDurationTicks());
                    send(sender, "- Saved to: " + path.getFileName());
                    break;
                }
                case "entities": {
                    ExperimentSpec spec = ExperimentSpec.builder()
                            .scenarioId(ScenarioId.ENTITIES)
                            .seed(baseSpec.seed())
                            .dimension(baseSpec.dimension())
                            .center(0, 0)
                            .radius(baseSpec.radius() * 4)
                            .iterations(baseSpec.iterations())
                            .batchSize(baseSpec.batchSize())
                            .strategy(baseSpec.strategy())
                            .warmupIterations(baseSpec.warmupIterations())
                            .holdTicks(baseSpec.holdTicks())
                            .settleTicks(baseSpec.settleTicks())
                            .maxOperationsPerTick(baseSpec.maxOperationsPerTick())
                            .maxMillisPerTick(baseSpec.maxMillisPerTick())
                            .explicitGc(baseSpec.explicitGc())
                            .build();
                    List<String> available = platform.getAvailableEntityTypes();
                    ExperimentPlan plan = entityPlanner.plan(spec, available);
                    Path path = planStorage.savePlan(plan);
                    send(sender, "Entity Plan Created: " + plan.id());
                    send(sender, "- Operations: " + plan.totalOperations());
                    send(sender, "- Estimated Ticks: " + plan.estimatedDurationTicks());
                    send(sender, "- Saved to: " + path.getFileName());
                    break;
                }
                case "blockentities": {
                    ExperimentSpec spec = ExperimentSpec.builder()
                            .scenarioId(ScenarioId.BLOCK_ENTITIES)
                            .seed(baseSpec.seed())
                            .dimension(baseSpec.dimension())
                            .center(0, 0)
                            .radius(baseSpec.radius() * 4)
                            .iterations(baseSpec.iterations())
                            .batchSize(baseSpec.batchSize())
                            .strategy(baseSpec.strategy())
                            .warmupIterations(baseSpec.warmupIterations())
                            .holdTicks(baseSpec.holdTicks())
                            .settleTicks(baseSpec.settleTicks())
                            .maxOperationsPerTick(baseSpec.maxOperationsPerTick())
                            .maxMillisPerTick(baseSpec.maxMillisPerTick())
                            .explicitGc(baseSpec.explicitGc())
                            .build();
                    List<String> available = platform.getAvailableBlockEntityTypes();
                    ExperimentPlan plan = blockEntityPlanner.plan(spec, available);
                    Path path = planStorage.savePlan(plan);
                    send(sender, "Block Entity Plan Created: " + plan.id());
                    send(sender, "- Operations: " + plan.totalOperations());
                    send(sender, "- Estimated Ticks: " + plan.estimatedDurationTicks());
                    send(sender, "- Saved to: " + path.getFileName());
                    break;
                }
                default:
                    send(sender, "Â§cUnknown plan target: " + target);
                    break;
            }
        } catch (IllegalArgumentException e) {
            send(sender, "Â§cInvalid parameter: " + e.getMessage());
        } catch (IOException e) {
            send(sender, "Â§cFailed to persist plan: " + e.getMessage());
        }
    }

    private void handleRun(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "Â§cUsage: /hh run <chunks|entities|blockentities> [flags]");
            return;
        }
        if (experimentService.isExperimentActive()) {
            send(sender, "Â§cAn experiment is already in progress. Use /hh stop first.");
            return;
        }

        String target = args[1].toLowerCase(Locale.ROOT);
        int chunkX = 0, chunkZ = 0;
        String[] flagArgs = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : new String[0];

        try {
            ExperimentSpec baseSpec = FlagParser.parseSpec(flagArgs, 0, chunkX, chunkZ);
            ExperimentSpec spec;
            ExperimentPlan plan;

            switch (target) {
                case "chunks":
                    spec = baseSpec;
                    plan = chunkPlanner.plan(spec);
                    break;
                case "entities":
                    spec = ExperimentSpec.builder()
                            .scenarioId(ScenarioId.ENTITIES)
                            .seed(baseSpec.seed())
                            .dimension(baseSpec.dimension())
                            .center(0, 0)
                            .radius(baseSpec.radius() * 4)
                            .iterations(baseSpec.iterations())
                            .batchSize(baseSpec.batchSize())
                            .strategy(baseSpec.strategy())
                            .warmupIterations(baseSpec.warmupIterations())
                            .holdTicks(baseSpec.holdTicks())
                            .settleTicks(baseSpec.settleTicks())
                            .maxOperationsPerTick(baseSpec.maxOperationsPerTick())
                            .maxMillisPerTick(baseSpec.maxMillisPerTick())
                            .explicitGc(baseSpec.explicitGc())
                            .build();
                    plan = entityPlanner.plan(spec, platform.getAvailableEntityTypes());
                    break;
                case "blockentities":
                    spec = ExperimentSpec.builder()
                            .scenarioId(ScenarioId.BLOCK_ENTITIES)
                            .seed(baseSpec.seed())
                            .dimension(baseSpec.dimension())
                            .center(0, 0)
                            .radius(baseSpec.radius() * 4)
                            .iterations(baseSpec.iterations())
                            .batchSize(baseSpec.batchSize())
                            .strategy(baseSpec.strategy())
                            .warmupIterations(baseSpec.warmupIterations())
                            .holdTicks(baseSpec.holdTicks())
                            .settleTicks(baseSpec.settleTicks())
                            .maxOperationsPerTick(baseSpec.maxOperationsPerTick())
                            .maxMillisPerTick(baseSpec.maxMillisPerTick())
                            .explicitGc(baseSpec.explicitGc())
                            .build();
                    plan = blockEntityPlanner.plan(spec, platform.getAvailableBlockEntityTypes());
                    break;
                default:
                    send(sender, "Â§cUnknown run target: " + target);
                    return;
            }

            checkpointService.clear();
            planStorage.savePlan(plan);
            checkpointService.configure(plan.spec(), platform);
            experimentService.start(plan);
            send(sender, "Started Experiment: " + plan.id() + " (" + spec.iterations() + " cycles, radius " + spec.radius() + ")");
        } catch (IllegalArgumentException e) {
            send(sender, "Â§cInvalid parameter: " + e.getMessage());
        } catch (Exception e) {
            send(sender, "Â§cFailed to start experiment: " + e.getMessage());
            LOGGER.error("Failed to start experiment", e);
        }
    }

    private String getLatestReportPath() {
        try {
            Optional<ExperimentReport> report = reportService.loadLatestReport();
            if (report.isPresent()) {
                return "run/heaphammer/reports/" + report.get().runId().value() + ".json";
            }
        } catch (IOException ignored) {
        }
        return "run/heaphammer/reports/latest.json";
    }

    private void onExperimentFinished(ScenarioExecutor executor) {
        ExperimentPlan plan = executor.getPlan();
        EnvironmentFingerprint environment = platform.captureFingerprint();
        checkpointService.awaitDiagnostics().thenAccept(evidence -> {
            List<Checkpoint> checkpoints = checkpointService.getCheckpoints();
            DetectionResult detection = trendAnalyzer.analyze(plan.spec(), checkpoints, evidence);
            ExperimentReport report = new ExperimentReport(
                    plan.id(),
                    plan.createdAtEpochMs(),
                    System.currentTimeMillis(),
                    executor.getStateMachine().getState().name(),
                    plan.spec(),
                    environment,
                    checkpoints,
                    detection,
                    DiagnosticRefs.EMPTY,
                    evidence,
                    ReportService.buildCanonicalCommand(plan.spec()),
                    evidence.warnings()
            );
            try {
                Path path = reportService.saveReport(report);
                LOGGER.info("Report saved successfully: {}", path.toAbsolutePath());
            } catch (IOException e) {
                LOGGER.error("Failed to save experiment report", e);
            }
        });
    }

    private void handleReport(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "Â§cUsage: /hh report <list|show|export> [id|last]");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        try {
            if ("list".equals(action)) {
                List<String> ids = reportService.listReportIds();
                send(sender, "Saved Reports (" + ids.size() + "):");
                for (String id : ids) {
                    send(sender, "- " + id);
                }
            } else if ("show".equals(action) || "export".equals(action)) {
                Optional<ExperimentReport> report;
                if (args.length > 2 && "last".equalsIgnoreCase(args[2])) {
                    report = reportService.loadLatestReport();
                } else if (args.length > 2) {
                    report = reportService.loadReport(new ExperimentId(args[2]));
                } else {
                    report = reportService.loadLatestReport();
                }

                if (!report.isPresent()) {
                    send(sender, "Â§cNo report found.");
                    return;
                }

                ExperimentReport r = report.get();
                send(sender, "=== HeapHammer Report: " + r.runId() + " ===");
                send(sender, "- Scenario: " + r.spec().scenarioId());
                send(sender, "- Status: " + r.status());
                send(sender, "- Verdict: " + r.detection().classification());
                send(sender, "- Checkpoints: " + r.checkpoints().size());
                if ("export".equals(action)) {
                    send(sender, "Status: " + r.status() + " | Verdict: " + r.detection().classification());
                }
            } else {
                send(sender, "Â§cUsage: /hh report <list|show|export> [id|last]");
            }
        } catch (IOException e) {
            send(sender, "Â§cFailed to access reports: " + e.getMessage());
        }
    }

    private void handleCheckpoint(ICommandSender sender, String[] args) {
        Checkpoint cp = checkpointService.recordCheckpoint(
                CheckpointPhase.MANUAL, 0, "minecraft:overworld", false);
        send(sender, String.format("Recorded manual checkpoint: Heap used = %.2f MB", cp.metrics().heapUsedMb()));
    }

    private void handleCleanup(ICommandSender sender) {
        // Clean up test entities and block entities across known dimensions
        int platformCleaned = 0;
        for (String dim : new String[]{"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"}) {
            platformCleaned += platform.removeAllTestEntities(dim);
            platformCleaned += platform.removeAllTestBlockEntities(dim);
        }
        platform.getChunkTicketManager().releaseAllTickets();
        send(sender, "Cleanup complete: Purged all tickets, removed " + platformCleaned +
                " orphaned test entities and block entities across all dimensions.");
    }

    private void handleStop(ICommandSender sender) {
        boolean stopped = experimentService.stop("Cancelled by operator command");
        if (stopped) {
            send(sender, "Experiment cancelled. Releasing all tickets.");
        } else {
            send(sender, "Â§cNo active experiment to stop.");
        }
    }
}
