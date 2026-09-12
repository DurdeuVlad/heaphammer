package com.dwurdy.heaphammer.command;

import com.dwurdy.heaphammer.application.ExperimentService;
import com.dwurdy.heaphammer.application.ReplayService;
import com.dwurdy.heaphammer.command.argument.FlagParser;
import com.dwurdy.heaphammer.detection.TrendAnalyzer;
import com.dwurdy.heaphammer.domain.*;
import com.dwurdy.heaphammer.metrics.CheckpointService;
import com.dwurdy.heaphammer.platform.PlatformAdapter;
import com.dwurdy.heaphammer.diagnostics.*;
import com.dwurdy.heaphammer.report.PlanStorage;
import com.dwurdy.heaphammer.report.ReportDiff;
import com.dwurdy.heaphammer.report.ReportDiffer;
import com.dwurdy.heaphammer.report.ReportService;
import com.dwurdy.heaphammer.scenario.ScenarioExecutor;
import com.dwurdy.heaphammer.scenario.blockentities.BlockEntityScenarioPlanner;
import com.dwurdy.heaphammer.scenario.chunks.ChunkScenarioExecutor;
import com.dwurdy.heaphammer.scenario.chunks.ChunkWorkloadPlanner;
import com.dwurdy.heaphammer.scenario.entities.EntityScenarioPlanner;
import com.dwurdy.heaphammer.scenario.players.PlayerScenarioPlanner;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Brigadier command tree registration for HeapHammer (Sections 7, 8, 9, 10).
 */
public class HeapHammerCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-commands");

    private final PlatformAdapter platform;
    private final ExperimentService experimentService;
    private final CheckpointService checkpointService;
    private final PlanStorage planStorage;
    private final ReportService reportService;
    private final ReplayService replayService;
    private final ChunkWorkloadPlanner planner;
    private final EntityScenarioPlanner entityPlanner;
    private final BlockEntityScenarioPlanner blockEntityPlanner;
    private final PlayerScenarioPlanner playerPlanner;
    private final TrendAnalyzer trendAnalyzer;

    private final ClassHistogramCollector histogramCollector;
    private final JfrTrigger jfrTrigger;
    private final HeapDumpService heapDumpService;

    public HeapHammerCommands(
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
        this.planner = new ChunkWorkloadPlanner();
        this.entityPlanner = new EntityScenarioPlanner();
        this.blockEntityPlanner = new BlockEntityScenarioPlanner();
        this.playerPlanner = new PlayerScenarioPlanner();
        this.trendAnalyzer = new TrendAnalyzer();
        this.histogramCollector = new ClassHistogramCollector();
        this.jfrTrigger = new JfrTrigger();
        this.heapDumpService = new HeapDumpService();

        // Wire experiment service callbacks
        experimentService.setCheckpointListener((phase, iter) -> {
            Optional<ScenarioExecutor> active = experimentService.getActiveExecutor();
            String dim = active.map(e -> e.getPlan().spec().dimension()).orElse("minecraft:overworld");
            boolean explicitGc = active.map(e -> e.getPlan().spec().explicitGc()).orElse(false);
            if (phase == CheckpointPhase.ITERATION_CLEANUP || phase == CheckpointPhase.WARMUP) {
                com.dwurdy.heaphammer.fixture.SyntheticLeakFixture.onCycle(iter, 10);
            }
            checkpointService.recordCheckpoint(phase, iter, dim, explicitGc);
        });

        experimentService.setCompletionListener(executor -> {
            onExperimentFinished(executor);
        });

        // Discover external workload adapters
        com.dwurdy.heaphammer.adapter.WorkloadAdapterRegistry.getInstance().loadFromFabricEntrypoints(platform);
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Root /hh command requires OP level 2 or heaphammer.use permission
        var root = Commands.literal("hh").requires(s -> CommandPermissions.check(s, CommandPermissions.PERM_USE));

        // Info commands (requires OP / permission)
        root.then(Commands.literal("version").requires(s -> CommandPermissions.check(s, "heaphammer.version")).executes(this::cmdVersion));
        root.then(Commands.literal("help").requires(s -> CommandPermissions.check(s, "heaphammer.help"))
                .executes(this::cmdHelp)
                .then(Commands.argument("topic", StringArgumentType.string()).executes(this::cmdHelpTopic)));
        root.then(Commands.literal("capabilities").requires(s -> CommandPermissions.check(s, "heaphammer.capabilities")).executes(this::cmdCapabilities));
        root.then(Commands.literal("doctor").requires(s -> CommandPermissions.check(s, "heaphammer.doctor")).executes(this::cmdDoctor));
        root.then(Commands.literal("status").requires(s -> CommandPermissions.check(s, "heaphammer.status")).executes(this::cmdStatus));
        root.then(Commands.literal("metrics").requires(s -> CommandPermissions.check(s, "heaphammer.metrics")).executes(this::cmdMetrics));
        root.then(Commands.literal("inspect").requires(s -> CommandPermissions.check(s, "heaphammer.inspect"))
                .then(Commands.literal("mods").executes(this::cmdInspectMods)));

        // Operator commands (requires level 2)
        root.then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(this::cmdStop));
        root.then(Commands.literal("abort").requires(s -> s.hasPermission(2)).executes(this::cmdStop));
        root.then(Commands.literal("cancel").requires(s -> s.hasPermission(2)).executes(this::cmdStop));
        root.then(Commands.literal("cleanup").requires(s -> s.hasPermission(2)).executes(this::cmdCleanup));
        root.then(Commands.literal("checkpoint").requires(s -> s.hasPermission(2))
                .executes(this::cmdCheckpoint)
                .then(Commands.literal("--diagnostics").executes(this::cmdCheckpointDiagnostics)));

        // Scenario command branch
        var scenario = Commands.literal("scenario").requires(s -> CommandPermissions.check(s, "heaphammer.scenario"));
        scenario.then(Commands.literal("list").executes(this::cmdScenarioList));
        scenario.then(Commands.literal("describe")
                .then(Commands.literal("chunks").executes(this::cmdScenarioDescribeChunks))
                .then(Commands.literal("entities").executes(this::cmdScenarioDescribeEntities))
                .then(Commands.literal("blockentities").executes(this::cmdScenarioDescribeBlockEntities))
                .then(Commands.literal("players").executes(this::cmdScenarioDescribePlayers)));
        root.then(scenario);

        // Planning
        var plan = Commands.literal("plan").requires(s -> CommandPermissions.check(s, CommandPermissions.PERM_PLAN));
        plan.then(Commands.literal("chunks")
                .executes(ctx -> cmdPlanChunks(ctx, ""))
                .then(Commands.argument("flags", StringArgumentType.greedyString())
                        .executes(ctx -> cmdPlanChunks(ctx, StringArgumentType.getString(ctx, "flags")))));
        plan.then(Commands.literal("entities")
                .executes(ctx -> cmdPlanEntities(ctx, ""))
                .then(Commands.argument("flags", StringArgumentType.greedyString())
                        .executes(ctx -> cmdPlanEntities(ctx, StringArgumentType.getString(ctx, "flags")))));
        plan.then(Commands.literal("blockentities")
                .executes(ctx -> cmdPlanBlockEntities(ctx, ""))
                .then(Commands.argument("flags", StringArgumentType.greedyString())
                        .executes(ctx -> cmdPlanBlockEntities(ctx, StringArgumentType.getString(ctx, "flags")))));
        plan.then(Commands.literal("players")
                .executes(ctx -> cmdPlanPlayers(ctx, ""))
                .then(Commands.argument("flags", StringArgumentType.greedyString())
                        .executes(ctx -> cmdPlanPlayers(ctx, StringArgumentType.getString(ctx, "flags")))));
        root.then(plan);

        // Running
        var run = Commands.literal("run").requires(s -> CommandPermissions.check(s, CommandPermissions.PERM_RUN));
        run.then(Commands.literal("chunks")
                .executes(ctx -> cmdRunChunks(ctx, ""))
                .then(Commands.argument("flags", StringArgumentType.greedyString())
                        .executes(ctx -> cmdRunChunks(ctx, StringArgumentType.getString(ctx, "flags")))));
        run.then(Commands.literal("entities")
                .executes(ctx -> cmdRunEntities(ctx, ""))
                .then(Commands.argument("flags", StringArgumentType.greedyString())
                        .executes(ctx -> cmdRunEntities(ctx, StringArgumentType.getString(ctx, "flags")))));
        run.then(Commands.literal("blockentities")
                .executes(ctx -> cmdRunBlockEntities(ctx, ""))
                .then(Commands.argument("flags", StringArgumentType.greedyString())
                        .executes(ctx -> cmdRunBlockEntities(ctx, StringArgumentType.getString(ctx, "flags")))));
        run.then(Commands.literal("players")
                .executes(ctx -> cmdRunPlayers(ctx, ""))
                .then(Commands.argument("flags", StringArgumentType.greedyString())
                        .executes(ctx -> cmdRunPlayers(ctx, StringArgumentType.getString(ctx, "flags")))));
        root.then(run);

        // Replay & Rerun
        root.then(Commands.literal("replay").requires(s -> CommandPermissions.check(s, "heaphammer.replay"))
                .then(Commands.argument("target", StringArgumentType.greedyString())
                        .executes(ctx -> cmdReplay(ctx, StringArgumentType.getString(ctx, "target")))));

        root.then(Commands.literal("rerun").requires(s -> CommandPermissions.check(s, "heaphammer.rerun"))
                .then(Commands.argument("target", StringArgumentType.greedyString())
                        .executes(ctx -> cmdRerun(ctx, StringArgumentType.getString(ctx, "target")))));

        // Reports
        var report = Commands.literal("report").requires(s -> CommandPermissions.check(s, CommandPermissions.PERM_REPORT));
        report.then(Commands.literal("list").executes(this::cmdReportList));
        report.then(Commands.literal("show")
                .then(Commands.argument("target", StringArgumentType.greedyString())
                        .executes(ctx -> cmdReportShow(ctx, StringArgumentType.getString(ctx, "target")))));
        report.then(Commands.literal("export")
                .then(Commands.argument("target", StringArgumentType.greedyString())
                        .executes(ctx -> cmdReportExport(ctx, StringArgumentType.getString(ctx, "target")))));
        report.then(Commands.literal("diff")
                .then(Commands.argument("runA", StringArgumentType.string())
                        .then(Commands.argument("runB", StringArgumentType.string())
                                .executes(this::cmdReportDiff))));
        root.then(report);
        root.then(Commands.literal("compare").requires(s -> CommandPermissions.check(s, CommandPermissions.PERM_REPORT))
                .then(Commands.argument("runA", StringArgumentType.string())
                        .then(Commands.argument("runB", StringArgumentType.string())
                                .executes(this::cmdReportDiff))));

        // Diagnostics
        var diagnostics = Commands.literal("diagnostics").requires(s -> CommandPermissions.check(s, CommandPermissions.PERM_DIAGNOSTICS));
        diagnostics.then(Commands.literal("histogram").executes(this::cmdDiagnosticsHistogram));
        diagnostics.then(Commands.literal("heapdump").executes(this::cmdDiagnosticsHeapdump));
        var jfr = Commands.literal("jfr");
        jfr.then(Commands.literal("start").executes(this::cmdDiagnosticsJfrStart));
        jfr.then(Commands.literal("stop").executes(this::cmdDiagnosticsJfrStop));
        jfr.then(Commands.literal("dump").executes(this::cmdDiagnosticsJfrDump));
        diagnostics.then(jfr);
        root.then(diagnostics);

        // Fixtures (operator only)
        var fixture = Commands.literal("fixture").requires(s -> CommandPermissions.check(s, "heaphammer.fixture"));
        fixture.then(Commands.literal("reset").executes(this::cmdFixtureReset));
        fixture.then(Commands.argument("mode", StringArgumentType.string())
                .executes(ctx -> cmdFixture(ctx, StringArgumentType.getString(ctx, "mode"), 10))
                .then(Commands.argument("sizeMb", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> cmdFixture(ctx, StringArgumentType.getString(ctx, "mode"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "sizeMb")))));
        root.then(fixture);

        // Adapters (operator only)
        var adapters = Commands.literal("adapters").requires(s -> CommandPermissions.check(s, "heaphammer.adapters"));
        adapters.then(Commands.literal("list").executes(this::cmdAdaptersList));
        root.then(adapters);

        // Configuration (operator only)
        var config = Commands.literal("config").requires(s -> CommandPermissions.check(s, "heaphammer.config"));
        config.then(Commands.literal("show").executes(this::cmdConfigShow));
        config.then(Commands.literal("reload").executes(this::cmdConfigReload));
        root.then(config);

        dispatcher.register(root);
    }

    private int cmdAdaptersList(CommandContext<CommandSourceStack> ctx) {
        com.dwurdy.heaphammer.adapter.WorkloadAdapterRegistry.getInstance().loadFromFabricEntrypoints(platform);
        List<com.dwurdy.heaphammer.adapter.WorkloadAdapter> list =
                com.dwurdy.heaphammer.adapter.WorkloadAdapterRegistry.getInstance().getAllAdapters();
        if (list.isEmpty()) {
            ctx.getSource().sendSuccess(new TextComponent("No external workload adapters registered.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder("Registered Workload Adapters (" + list.size() + "):\n");
        for (com.dwurdy.heaphammer.adapter.WorkloadAdapter adapter : list) {
            sb.append("- ").append(adapter.displayName()).append(" (ID: ").append(adapter.adapterId())
              .append("): Scenarios: [").append(String.join(", ", adapter.supportedScenarios())).append("]\n");
        }
        ctx.getSource().sendSuccess(new TextComponent(sb.toString().trim()).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private int cmdFixtureReset(CommandContext<CommandSourceStack> ctx) {
        com.dwurdy.heaphammer.fixture.SyntheticLeakFixture.reset();
        ctx.getSource().sendSuccess(new TextComponent("Synthetic fixture reset to OFF and storage cleared.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private int cmdFixture(CommandContext<CommandSourceStack> ctx, String modeStr, int sizeMb) {
        try {
            var mode = com.dwurdy.heaphammer.fixture.SyntheticLeakFixture.Mode.valueOf(modeStr.trim().toUpperCase(Locale.ROOT));
            com.dwurdy.heaphammer.fixture.SyntheticLeakFixture.setMode(mode);
            ctx.getSource().sendSuccess(new TextComponent("Synthetic fixture set to " + mode + " (" + sizeMb + " MB/cycle).").withStyle(ChatFormatting.GOLD), true);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(new TextComponent("Unknown fixture mode: " + modeStr + ". Valid modes: OFF, LEAK, CLEAN, BOUNDED."));
        }
        return 1;
    }

    private int cmdVersion(CommandContext<CommandSourceStack> ctx) {
        EnvironmentFingerprint env = platform.captureFingerprint();
        ctx.getSource().sendSuccess(new TextComponent(
                String.format(Locale.ROOT, "HeapHammer v%s (Minecraft %s / Fabric)",
                        env.heapHammerVersion(), env.minecraftVersion()))
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private int cmdHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(new TextComponent(
                "--- HeapHammer Commands ---\n" +
                "/hh plan chunks [flags]   - Compute deterministic workload plan\n" +
                "/hh run chunks [flags]    - Execute chunk churn experiment\n" +
                "/hh plan|run entities [flags] - Entity churn; --profile=persistent|unticked_ring\n" +
                "/hh plan|run players [flags] - Real player lifecycle; --logins-per-cycle=N\n" +
                "/hh compare <run-a> <run-b> - Compare two evidence-backed reports\n" +
                "/hh capabilities          - Show supported and unsupported platform features\n" +
                "/hh status                - Show active experiment progress\n" +
                "/hh stop                  - Abort and release all tickets\n" +
                "/hh cleanup               - Purge any remaining tickets\n" +
                "/hh metrics               - Display instant JVM/world metrics\n" +
                "/hh doctor                - Check server health & safety\n" +
                "/hh replay <run-id|last>  - Replay exact resolved operations\n" +
                "/hh rerun <run-id|last>   - Rerun from original spec & seed\n" +
                "/hh report list|show <id> - Inspect previous test reports"
        ).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private int cmdHelpTopic(CommandContext<CommandSourceStack> ctx) {
        String topic = StringArgumentType.getString(ctx, "topic");
        ctx.getSource().sendSuccess(new TextComponent("Help topic: " + topic).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private int cmdCapabilities(CommandContext<CommandSourceStack> ctx) {
        Runtime rt = Runtime.getRuntime();
        long maxMb = rt.maxMemory() / (1024 * 1024);
        long totalMb = rt.totalMemory() / (1024 * 1024);
        ctx.getSource().sendSuccess(new TextComponent(
                "HeapHammer Capabilities:\n" +
                "- JVM Max Memory: " + maxMb + " MB (Allocated: " + totalMb + " MB)\n" +
                "- Platform: " + platform.captureFingerprint().loaderVersion() + " (Server-side only)\n" +
                "- Ticket Type: heaphammer (distance 1)\n" + capabilities
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private int cmdDoctor(CommandContext<CommandSourceStack> ctx) {
        int totalLoaded = platform.getTotalLoadedChunkCount();
        ctx.getSource().sendSuccess(new TextComponent(
                "HeapHammer Doctor:\n" +
                "- Server status: READY\n" +
                "- Total loaded chunks: " + totalLoaded + "\n" +
                "- Active tickets owned by HeapHammer: " + platform.getChunkTicketManager().getActiveTicketCount() + "\n" +
                "- Warning: Always run memory experiments on a test world or dedicated staging server!"
        ).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private int cmdStatus(CommandContext<CommandSourceStack> ctx) {
        Optional<ScenarioExecutor> opt = experimentService.getActiveExecutor();
        if (opt.isEmpty() || !opt.get().getStateMachine().getState().isActive()) {
            ctx.getSource().sendSuccess(new TextComponent("No experiment currently active.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        ScenarioExecutor executor = opt.get();
        ExperimentPlan plan = executor.getPlan();
        int currentIter = executor.getCurrentIteration();
        int totalIter = plan.spec().iterations();
        ctx.getSource().sendSuccess(new TextComponent(
                "Active Experiment: " + plan.id() + " (" + plan.spec().scenarioId() + ")\n" +
                "- State: " + executor.getStateMachine().getState() + " (" + executor.getStateMachine().getStatusMessage() + ")\n" +
                "- Iteration: " + currentIter + " / " + totalIter + "\n" +
                "- Active Tickets: " + platform.getChunkTicketManager().getActiveTicketCount()
        ).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private int cmdMetrics(CommandContext<CommandSourceStack> ctx) {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long committedMb = rt.totalMemory() / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        int totalChunks = platform.getTotalLoadedChunkCount();

        ctx.getSource().sendSuccess(new TextComponent(String.format(Locale.ROOT,
                "Metrics: Heap: %d MB / %d MB (Max %d MB) | Loaded Chunks: %d | Active Tickets: %d",
                usedMb, committedMb, maxMb, totalChunks, platform.getChunkTicketManager().getActiveTicketCount()
        )).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private int cmdStop(CommandContext<CommandSourceStack> ctx) {
        boolean stopped = experimentService.stop("Cancelled by operator command");
        if (stopped) {
            ctx.getSource().sendSuccess(new TextComponent("Experiment cancelled. Releasing all tickets.").withStyle(ChatFormatting.RED), true);
        } else {
            ctx.getSource().sendFailure(new TextComponent("No active experiment to stop."));
        }
        return 1;
    }

    private int cmdCleanup(CommandContext<CommandSourceStack> ctx) {
        int countBefore = platform.getChunkTicketManager().getActiveTicketCount();
        platform.getChunkTicketManager().releaseAllTickets();
        String dim = "minecraft:overworld";
        int entitiesPurged = platform.removeAllTestEntities(dim);
        int blockEntitiesPurged = platform.removeAllTestBlockEntities(dim);
        ctx.getSource().sendSuccess(new TextComponent("Purged " + countBefore + " tickets, " +
                entitiesPurged + " test entities, and " + blockEntitiesPurged + " test block entities.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private int cmdCheckpoint(CommandContext<CommandSourceStack> ctx) {
        Checkpoint cp = checkpointService.recordCheckpoint(CheckpointPhase.MANUAL, 0, "minecraft:overworld", false);
        ctx.getSource().sendSuccess(new TextComponent("Recorded manual checkpoint: Heap used = " +
                String.format(Locale.ROOT, "%.2f MB", cp.metrics().heapUsedMb())).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private int cmdScenarioList(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(new TextComponent(
                "Available Scenarios:\n" +
                "- chunks (v1.0): Deterministic chunk load/unload churn\n" +
                "- entities (v1.0): Deterministic entity lifecycle churn\n" +
                "- blockentities (v1.0): Conservative block entity placement and destruction stress\n" +
                "- players (v1.1): Authentic login/logout and player lifecycle churn (capability-gated)"
        ).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private int cmdScenarioDescribeChunks(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(new TextComponent(
                "Scenario: chunks\n" +
                "Acquires, holds, and releases chunk tickets within a specified radius using selectable strategies (SPIRAL, RING, RANDOM_WALK, HOTSPOT_CHURN, GRID_SWEEP)."
        ).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private int cmdScenarioDescribeEntities(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(new TextComponent(
                "Scenario: entities\n" +
                "Spawns deterministic entity batches, exercises them for configured lifetime ticks, then discards or kills them to verify complete cleanup."
        ).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private int cmdScenarioDescribeBlockEntities(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(new TextComponent(
                "Scenario: blockentities\n" +
                "Places deterministic block entity states in a test grid, allows conservative tick initialization, then removes blocks to verify lifecycle cleanup."
        ).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private int cmdScenarioDescribePlayers(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Scenario: players\n" +
                "Runs deterministic test-player lifecycle actions through the server's real login/logout path. " +
                "Use --logins-per-cycle and --actions=join,respawn,teleport,dimchange,quit; the platform must report PLAYER_LIFECYCLE support."
        ).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private int cmdPlanChunks(CommandContext<CommandSourceStack> ctx, String flagsString) {
        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;

        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        try {
            ExperimentSpec spec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);
            ExperimentPlan plan = planner.plan(spec);
            Path path = planStorage.savePlan(plan);
            ctx.getSource().sendSuccess(new TextComponent(
                    "Plan Created: " + plan.id() + "\n" +
                    "- Operations: " + plan.totalOperations() + " (Unique chunks: " + plan.uniqueChunksCount() + ")\n" +
                    "- Estimated Ticks: " + plan.estimatedDurationTicks() + "\n" +
                    "- Saved to: " + path.getFileName()
            ).withStyle(ChatFormatting.GREEN), false);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid parameter: " + e.getMessage()));
        } catch (IOException e) {
            ctx.getSource().sendFailure(new TextComponent("Failed to persist plan: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdRunChunks(CommandContext<CommandSourceStack> ctx, String flagsString) {
        if (experimentService.isExperimentActive()) {
            ctx.getSource().sendFailure(new TextComponent("An experiment is already in progress. Use /hh stop first."));
            return 0;
        }

        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;

        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        try {
            ExperimentSpec spec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);
            checkpointService.clear();
            ExperimentPlan plan = planner.plan(spec);
            planStorage.savePlan(plan);
            checkpointService.configure(plan.spec(), platform);
            experimentService.start(plan);
            ctx.getSource().sendSuccess(new TextComponent(
                    "Started Experiment: " + plan.id() + " (" + spec.iterations() + " cycles, radius " + spec.radius() + ")"
            ).withStyle(ChatFormatting.GOLD), true);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid parameter: " + e.getMessage()));
        } catch (Exception e) {
            ctx.getSource().sendFailure(new TextComponent("Failed to start experiment: " + e.getMessage()));
        }
        return 1;
    }

    private ExperimentSpec scenarioSpec(ExperimentSpec baseSpec, ScenarioId scenarioId, int centerX, int centerZ, int radius) {
        return ExperimentSpec.builder()
                .scenarioId(scenarioId)
                .seed(baseSpec.seed())
                .dimension(baseSpec.dimension())
                .center(centerX, centerZ)
                .radius(radius)
                .iterations(baseSpec.iterations())
                .batchSize(baseSpec.batchSize())
                .strategy(baseSpec.strategy())
                .warmupIterations(baseSpec.warmupIterations())
                .holdTicks(baseSpec.holdTicks())
                .settleTicks(baseSpec.settleTicks())
                .maxOperationsPerTick(baseSpec.maxOperationsPerTick())
                .maxMillisPerTick(baseSpec.maxMillisPerTick())
                .explicitGc(baseSpec.explicitGc())
                .coverage(baseSpec.coverage())
                .includeMods(baseSpec.includeMods())
                .excludeMods(baseSpec.excludeMods())
                .entityProfile(baseSpec.entityProfile())
                .loginsPerCycle(baseSpec.loginsPerCycle())
                .playerActions(baseSpec.playerActions())
                .durationSeconds(baseSpec.durationSeconds())
                .intervalSeconds(baseSpec.intervalSeconds())
                .diagnosticCollectors(baseSpec.diagnosticCollectors())
                .trackedClasses(baseSpec.trackedClasses())
                .build();
    }

    private int cmdPlanPlayers(CommandContext<CommandSourceStack> ctx, String flagsString) {
        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;
        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        try {
            ExperimentSpec baseSpec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);
            ExperimentSpec spec = scenarioSpec(baseSpec, ScenarioId.PLAYERS,
                    (int) Math.floor(pos.x), (int) Math.floor(pos.z), baseSpec.radius());
            ExperimentPlan plan = playerPlanner.plan(spec);
            Path path = planStorage.savePlan(plan);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Player Plan Created: " + plan.id() + "\n" +
                    "- Operations: " + plan.totalOperations() + "\n" +
                    "- Estimated Ticks: " + plan.estimatedDurationTicks() + "\n" +
                    "- Saved to: " + path.getFileName()
            ).withStyle(ChatFormatting.GREEN), false);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid parameter: " + e.getMessage()));
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Failed to persist player plan: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdRunPlayers(CommandContext<CommandSourceStack> ctx, String flagsString) {
        if (experimentService.isExperimentActive()) {
            ctx.getSource().sendFailure(Component.literal("An experiment is already in progress. Use /hh stop first."));
            return 0;
        }
        if (!platform.getPlayerLifecyclePort().isPresent()) {
            ctx.getSource().sendFailure(Component.literal(
                    "Player lifecycle is unsupported by this platform: " +
                            platform.getCapabilities().status(PlatformCapability.PLAYER_LIFECYCLE).reason()));
            return 0;
        }
        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;
        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        try {
            ExperimentSpec baseSpec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);
            ExperimentSpec spec = scenarioSpec(baseSpec, ScenarioId.PLAYERS,
                    (int) Math.floor(pos.x), (int) Math.floor(pos.z), baseSpec.radius());
            checkpointService.clear();
            ExperimentPlan plan = playerPlanner.plan(spec);
            planStorage.savePlan(plan);
            checkpointService.configure(plan.spec(), platform);
            experimentService.start(plan);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Started Player Experiment: " + plan.id() + " (" + spec.iterations() + " cycles, " +
                            spec.loginsPerCycle() + " logins/cycle)"
            ).withStyle(ChatFormatting.GOLD), true);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid parameter: " + e.getMessage()));
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Failed to start player experiment: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdPlanEntities(CommandContext<CommandSourceStack> ctx, String flagsString) {
        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;

        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        try {
            ExperimentSpec baseSpec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);
            ExperimentSpec spec = scenarioSpec(baseSpec, ScenarioId.ENTITIES,
                    (int) Math.floor(pos.x), (int) Math.floor(pos.z), baseSpec.radius() * 4);

            List<String> available = platform.getAvailableEntityTypes();
            ExperimentPlan plan = entityPlanner.plan(spec, available);
            Path path = planStorage.savePlan(plan);
            ctx.getSource().sendSuccess(new TextComponent(
                    "Entity Plan Created: " + plan.id() + "\n" +
                    "- Operations: " + plan.totalOperations() + "\n" +
                    "- Estimated Ticks: " + plan.estimatedDurationTicks() + "\n" +
                    "- Saved to: " + path.getFileName()
            ).withStyle(ChatFormatting.GREEN), false);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid parameter: " + e.getMessage()));
        } catch (IOException e) {
            ctx.getSource().sendFailure(new TextComponent("Failed to persist entity plan: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdRunEntities(CommandContext<CommandSourceStack> ctx, String flagsString) {
        if (experimentService.isExperimentActive()) {
            ctx.getSource().sendFailure(new TextComponent("An experiment is already in progress. Use /hh stop first."));
            return 0;
        }

        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;

        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        try {
            ExperimentSpec baseSpec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);
            ExperimentSpec spec = scenarioSpec(baseSpec, ScenarioId.ENTITIES,
                    (int) Math.floor(pos.x), (int) Math.floor(pos.z), baseSpec.radius() * 4);

            checkpointService.clear();
            List<String> available = platform.getAvailableEntityTypes();
            ExperimentPlan plan = entityPlanner.plan(spec, available);
            planStorage.savePlan(plan);
            checkpointService.configure(plan.spec(), platform);
            experimentService.start(plan);
            ctx.getSource().sendSuccess(new TextComponent(
                    "Started Entity Experiment: " + plan.id() + " (" + spec.iterations() + " cycles, " + spec.batchSize() + " entities/batch)"
            ).withStyle(ChatFormatting.GOLD), true);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid parameter: " + e.getMessage()));
        } catch (Exception e) {
            ctx.getSource().sendFailure(new TextComponent("Failed to start entity experiment: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdPlanBlockEntities(CommandContext<CommandSourceStack> ctx, String flagsString) {
        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;

        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        try {
            ExperimentSpec baseSpec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);
            ExperimentSpec spec = scenarioSpec(baseSpec, ScenarioId.BLOCK_ENTITIES,
                    (int) Math.floor(pos.x), (int) Math.floor(pos.z), baseSpec.radius());

            List<String> available = platform.getAvailableBlockEntityTypes();
            ExperimentPlan plan = blockEntityPlanner.plan(spec, available);
            Path path = planStorage.savePlan(plan);
            ctx.getSource().sendSuccess(new TextComponent(
                    "Block Entity Plan Created: " + plan.id() + "\n" +
                    "- Operations: " + plan.totalOperations() + "\n" +
                    "- Estimated Ticks: " + plan.estimatedDurationTicks() + "\n" +
                    "- Saved to: " + path.getFileName()
            ).withStyle(ChatFormatting.GREEN), false);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid parameter: " + e.getMessage()));
        } catch (IOException e) {
            ctx.getSource().sendFailure(new TextComponent("Failed to persist block entity plan: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdRunBlockEntities(CommandContext<CommandSourceStack> ctx, String flagsString) {
        if (experimentService.isExperimentActive()) {
            ctx.getSource().sendFailure(new TextComponent("An experiment is already in progress. Use /hh stop first."));
            return 0;
        }

        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;

        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        try {
            ExperimentSpec baseSpec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);
            ExperimentSpec spec = scenarioSpec(baseSpec, ScenarioId.BLOCK_ENTITIES,
                    (int) Math.floor(pos.x), (int) Math.floor(pos.z), baseSpec.radius());

            checkpointService.clear();
            List<String> available = platform.getAvailableBlockEntityTypes();
            ExperimentPlan plan = blockEntityPlanner.plan(spec, available);
            planStorage.savePlan(plan);
            checkpointService.configure(plan.spec(), platform);
            experimentService.start(plan);
            ctx.getSource().sendSuccess(new TextComponent(
                    "Started Block Entity Experiment: " + plan.id() + " (" + spec.iterations() + " cycles, " + spec.batchSize() + " block entities/batch)"
            ).withStyle(ChatFormatting.GOLD), true);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid parameter: " + e.getMessage()));
        } catch (Exception e) {
            ctx.getSource().sendFailure(new TextComponent("Failed to start block entity experiment: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdReplay(CommandContext<CommandSourceStack> ctx, String target) {
        try {
            checkpointService.clear();
            ScenarioExecutor executor = replayService.replay(target);
            ctx.getSource().sendSuccess(new TextComponent("Replaying plan: " + executor.getPlan().id()).withStyle(ChatFormatting.GOLD), true);
        } catch (Exception e) {
            ctx.getSource().sendFailure(new TextComponent("Replay failed: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdRerun(CommandContext<CommandSourceStack> ctx, String target) {
        try {
            checkpointService.clear();
            ScenarioExecutor executor = replayService.rerun(target);
            ctx.getSource().sendSuccess(new TextComponent("Rerunning spec for: " + executor.getPlan().id()).withStyle(ChatFormatting.GOLD), true);
        } catch (Exception e) {
            ctx.getSource().sendFailure(new TextComponent("Rerun failed: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdReportList(CommandContext<CommandSourceStack> ctx) {
        try {
            List<String> reports = reportService.listReportIds();
            if (reports.isEmpty()) {
                ctx.getSource().sendSuccess(new TextComponent("No reports found.").withStyle(ChatFormatting.GRAY), false);
            } else {
                ctx.getSource().sendSuccess(new TextComponent("Saved Reports (" + reports.size() + "):\n- " +
                        String.join("\n- ", reports.stream().limit(10).toList())).withStyle(ChatFormatting.YELLOW), false);
            }
        } catch (IOException e) {
            ctx.getSource().sendFailure(new TextComponent("Error listing reports: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdReportShow(CommandContext<CommandSourceStack> ctx, String target) {
        try {
            Optional<ExperimentReport> repOpt = "last".equalsIgnoreCase(target) ?
                    reportService.loadLatestReport() : reportService.loadReport(ExperimentId.of(target));
            if (repOpt.isEmpty()) {
                ctx.getSource().sendFailure(new TextComponent("Report not found: " + target));
            } else {
                String summary = reportService.formatSummary(repOpt.get());
                ctx.getSource().sendSuccess(new TextComponent(summary).withStyle(ChatFormatting.YELLOW), false);
            }
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
        } catch (IOException e) {
            ctx.getSource().sendFailure(new TextComponent("Error loading report: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdReportExport(CommandContext<CommandSourceStack> ctx, String target) {
        cmdReportShow(ctx, target);
        return 1;
    }

    private int cmdReportDiff(CommandContext<CommandSourceStack> ctx) {
        String targetA = StringArgumentType.getString(ctx, "runA");
        String targetB = StringArgumentType.getString(ctx, "runB");
        try {
            Optional<ExperimentReport> repA = "last".equalsIgnoreCase(targetA) ?
                    reportService.loadLatestReport() : reportService.loadReport(ExperimentId.of(targetA));
            Optional<ExperimentReport> repB = "last".equalsIgnoreCase(targetB) ?
                    reportService.loadLatestReport() : reportService.loadReport(ExperimentId.of(targetB));

            if (repA.isEmpty()) {
                ctx.getSource().sendFailure(new TextComponent("Report A not found: " + targetA));
                return 0;
            }
            if (repB.isEmpty()) {
                ctx.getSource().sendFailure(new TextComponent("Report B not found: " + targetB));
                return 0;
            }

            ReportDiff diff = ReportDiffer.diff(repA.get(), repB.get());
            ctx.getSource().sendSuccess(new TextComponent(
                    "--- Report Diff (" + diff.runA().value() + " vs " + diff.runB().value() + ") ---\n" +
                    "Environment: " + diff.environmentComparison() + "\n" +
                    String.format(Locale.ROOT, "Initial Heap: %.2f MB -> %.2f MB\n",
                            diff.initialHeapA() / (1024.0 * 1024.0), diff.initialHeapB() / (1024.0 * 1024.0)) +
                    String.format(Locale.ROOT, "Final Heap:   %.2f MB -> %.2f MB\n",
                            diff.finalHeapA() / (1024.0 * 1024.0), diff.finalHeapB() / (1024.0 * 1024.0)) +
                    String.format(Locale.ROOT, "Net Delta:    %+.2f MB vs %+.2f MB (Diff: %+.2f MB)\n",
                            diff.netDeltaA() / (1024.0 * 1024.0), diff.netDeltaB() / (1024.0 * 1024.0), diff.netDeltaDiffMb()) +
                    String.format(Locale.ROOT, "Retained Slope: %+.2f MB/cyc vs %+.2f MB/cyc (Diff: %+.2f MB/cyc)\n",
                            diff.slopeA() / (1024.0 * 1024.0), diff.slopeB() / (1024.0 * 1024.0), diff.slopeDiffMb()) +
                    String.format(Locale.ROOT, "R²: %.3f vs %.3f (Diff: %+.3f)\n",
                            diff.rSquaredA(), diff.rSquaredB(), diff.rSquaredDifference()) +
                    "Classification: " + diff.classificationA() + " -> " + diff.classificationB() +
                    (diff.classificationChanged() ? " (CHANGED)" : "") +
                    (diff.warnings().isEmpty() ? "" : "\nWarnings: " + String.join("; ", diff.warnings()))
            ).withStyle(ChatFormatting.AQUA), false);
        } catch (IOException e) {
            ctx.getSource().sendFailure(new TextComponent("Error reading reports: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdCheckpointDiagnostics(CommandContext<CommandSourceStack> ctx) {
        cmdCheckpoint(ctx);
        if (!histogramCollector.isSupported()) {
            ctx.getSource().sendSuccess(new TextComponent("Diagnostics: Histogram capture unsupported on this JVM.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        Optional<ClassHistogram> hist = histogramCollector.capture(5);
        if (hist.isPresent() && !hist.get().entries().isEmpty()) {
            StringBuilder sb = new StringBuilder("Top 5 Classes:\n");
            for (ClassHistogramEntry e : hist.get().entries()) {
                sb.append(String.format(Locale.ROOT, "- #%d %s: %d (%.2f MB)\n",
                        e.rank(), e.className(), e.instances(), e.bytesMb()));
            }
            ctx.getSource().sendSuccess(new TextComponent(sb.toString()).withStyle(ChatFormatting.GOLD), false);
        }
        return 1;
    }

    private int cmdDiagnosticsHistogram(CommandContext<CommandSourceStack> ctx) {
        if (!histogramCollector.isSupported()) {
            ctx.getSource().sendFailure(new TextComponent("Class histogram capture is not supported on this JVM."));
            return 0;
        }
        ctx.getSource().sendSuccess(new TextComponent("Capturing JVM class histogram...").withStyle(ChatFormatting.GRAY), false);
        Optional<ClassHistogram> histOpt = histogramCollector.capture(10);
        if (histOpt.isEmpty() || histOpt.get().entries().isEmpty()) {
            ctx.getSource().sendFailure(new TextComponent("Failed to capture class histogram."));
            return 0;
        }
        ClassHistogram hist = histOpt.get();
        StringBuilder sb = new StringBuilder("--- JVM Class Histogram Top 10 ---\n");
        for (ClassHistogramEntry entry : hist.entries()) {
            sb.append(String.format(Locale.ROOT, "#%d %s: %d instances (%.2f MB)\n",
                    entry.rank(), entry.className(), entry.instances(), entry.bytesMb()));
        }
        sb.append(String.format(Locale.ROOT, "Total: %d instances, %.2f MB",
                hist.totalInstances(), hist.totalBytes() / (1024.0 * 1024.0)));
        ctx.getSource().sendSuccess(new TextComponent(sb.toString()).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private int cmdDiagnosticsHeapdump(CommandContext<CommandSourceStack> ctx) {
        if (!heapDumpService.isSupported()) {
            ctx.getSource().sendFailure(new TextComponent("Heap dumping is not supported on this JVM (HotSpotDiagnosticMXBean missing)."));
            return 0;
        }
        Path dumpDir = Path.of("heaphammer", "reports", "heapdumps");
        String filename = "manual-" + System.currentTimeMillis() + ".hprof";
        ctx.getSource().sendSuccess(new TextComponent("Triggering async heap dump to " + filename + " (Warning: temporary STW pause possible)...")
                .withStyle(ChatFormatting.RED), true);
        heapDumpService.dumpHeapAsync(dumpDir, filename, true)
                .thenAccept(path -> LOGGER.info("Manual heap dump written to {}", path))
                .exceptionally(ex -> {
                    LOGGER.error("Manual heap dump failed", ex);
                    return null;
                });
        return 1;
    }

    private int cmdDiagnosticsJfrStart(CommandContext<CommandSourceStack> ctx) {
        if (!JfrTrigger.isAvailable()) {
            ctx.getSource().sendFailure(new TextComponent("Java Flight Recorder is not available on this JVM."));
            return 0;
        }
        boolean started = jfrTrigger.start("HeapHammer-Manual-" + System.currentTimeMillis());
        if (started) {
            ctx.getSource().sendSuccess(new TextComponent("JFR recording started.").withStyle(ChatFormatting.GREEN), true);
        } else {
            ctx.getSource().sendFailure(new TextComponent("Failed to start JFR recording (may already be active)."));
        }
        return 1;
    }

    private int cmdDiagnosticsJfrStop(CommandContext<CommandSourceStack> ctx) {
        if (!jfrTrigger.isRecording()) {
            ctx.getSource().sendFailure(new TextComponent("No active JFR recording."));
            return 0;
        }
        jfrTrigger.stop();
        ctx.getSource().sendSuccess(new TextComponent("JFR recording stopped.").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private int cmdDiagnosticsJfrDump(CommandContext<CommandSourceStack> ctx) {
        if (!jfrTrigger.isRecording()) {
            ctx.getSource().sendFailure(new TextComponent("No active JFR recording to dump."));
            return 0;
        }
        Path dir = Path.of("heaphammer", "reports", "jfr");
        Path dumped = jfrTrigger.dump(dir, "manual-" + System.currentTimeMillis());
        if (dumped != null) {
            ctx.getSource().sendSuccess(new TextComponent("Dumped JFR to: " + dumped).withStyle(ChatFormatting.GOLD), true);
        } else {
            ctx.getSource().sendFailure(new TextComponent("Failed to dump JFR recording."));
        }
        return 1;
    }

    private int cmdInspectMods(CommandContext<CommandSourceStack> ctx) {
        EnvironmentFingerprint env = platform.captureFingerprint();
        ctx.getSource().sendSuccess(new TextComponent(
                "Installed Mods (" + env.installedMods().size() + "):\n" +
                "Hash: " + env.modpackHash() + "\n" +
                String.join(", ", env.installedMods().keySet().stream().limit(15).toList()) + "..."
        ).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private int cmdConfigShow(CommandContext<CommandSourceStack> ctx) {
        com.dwurdy.heaphammer.infrastructure.config.HeapHammerConfig cfg =
                com.dwurdy.heaphammer.infrastructure.config.ConfigManager.getActiveConfig();
        StringBuilder sb = new StringBuilder("=== HeapHammer Configuration ===\n");
        sb.append("- Max Radius: ").append(cfg.getMaxRadius()).append(" chunks\n");
        sb.append("- Max Iterations: ").append(cfg.getMaxIterations()).append("\n");
        sb.append("- Max Batch Size: ").append(cfg.getMaxBatchSize()).append("\n");
        sb.append("- Max Warmup: ").append(cfg.getMaxWarmupIterations()).append("\n");
        sb.append("- Max Hold/Settle: ").append(cfg.getMaxHoldTicks()).append(" / ").append(cfg.getMaxSettleTicks()).append(" ticks\n");
        sb.append("- Max Ops/Tick: ").append(cfg.getMaxOperationsPerTick()).append(" ops (max ").append(cfg.getMaxMillisPerTick()).append(" ms)\n");
        sb.append("- Circuit Breaker: ").append(cfg.isCircuitBreakerEnabled() ? "ENABLED (min free " + cfg.getMinFreeMemoryMb() + " MB)" : "DISABLED").append("\n");
        sb.append("- Auto Cleanup on Startup: ").append(cfg.isAutoCleanupOnStartup() ? "YES" : "NO");
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private int cmdConfigReload(CommandContext<CommandSourceStack> ctx) {
        com.dwurdy.heaphammer.infrastructure.config.ConfigManager.reload();
        ctx.getSource().sendSuccess(() -> Component.literal("HeapHammer configuration reloaded successfully from disk.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private void onExperimentFinished(ScenarioExecutor executor) {
        ExperimentPlan plan = executor.getPlan();
        EnvironmentFingerprint env = platform.captureFingerprint();
        checkpointService.awaitDiagnostics().thenAccept(evidence -> {
            List<Checkpoint> cps = checkpointService.getCheckpoints();
            DetectionResult detection = trendAnalyzer.analyze(plan.spec(), cps, evidence);
            String canonical = ReportService.buildCanonicalCommand(plan.spec());
            ExperimentReport report = new ExperimentReport(
                    plan.id(),
                    plan.createdAtEpochMs(),
                    System.currentTimeMillis(),
                    executor.getStateMachine().getState().name(),
                    plan.spec(),
                    env,
                    cps,
                    detection,
                    DiagnosticRefs.EMPTY,
                    evidence,
                    canonical,
                    evidence.warnings()
            );

        String canonical = ReportService.buildCanonicalCommand(plan.spec());
        ExperimentReport report = new ExperimentReport(
                plan.id(),
                plan.createdAtEpochMs(),
                System.currentTimeMillis(),
                executor.getStateMachine().getState().name(),
                plan.spec(),
                env,
                cps,
                detection,
                canonical,
                List.of()
        );

        try {
            Path path = reportService.saveReport(report);
            LOGGER.info("Report saved successfully: {}", path.toAbsolutePath());
            System.out.println("Report saved successfully: " + path.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to save experiment report", e);
            System.err.println("Failed to save experiment report: " + e.getMessage());
        }
    }
}
