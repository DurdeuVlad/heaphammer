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
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;

import net.minecraft.util.text.StringTextComponent;
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
public class ForgeHeapHammerCommands {
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
    private final TrendAnalyzer trendAnalyzer;

    private final ClassHistogramCollector histogramCollector;
    private final JfrTrigger jfrTrigger;
    private final HeapDumpService heapDumpService;

    public ForgeHeapHammerCommands(
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

    public void register(CommandDispatcher<CommandSource> dispatcher) {
        var root = Commands.literal("hh");

        // Info commands (available to all)
        root.then(Commands.literal("version").executes(this::cmdVersion));
        root.then(Commands.literal("help")
                .executes(this::cmdHelp)
                .then(Commands.argument("topic", StringArgumentType.string()).executes(this::cmdHelpTopic)));
        root.then(Commands.literal("capabilities").executes(this::cmdCapabilities));
        root.then(Commands.literal("doctor").executes(this::cmdDoctor));
        root.then(Commands.literal("status").executes(this::cmdStatus));
        root.then(Commands.literal("metrics").executes(this::cmdMetrics));
        root.then(Commands.literal("inspect").then(Commands.literal("mods").executes(this::cmdInspectMods)));

        // Operator commands (requires level 2)
        root.then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(this::cmdStop));
        root.then(Commands.literal("abort").requires(s -> s.hasPermission(2)).executes(this::cmdStop));
        root.then(Commands.literal("cancel").requires(s -> s.hasPermission(2)).executes(this::cmdStop));
        root.then(Commands.literal("cleanup").requires(s -> s.hasPermission(2)).executes(this::cmdCleanup));
        root.then(Commands.literal("checkpoint").requires(s -> s.hasPermission(2))
                .executes(this::cmdCheckpoint)
                .then(Commands.literal("--diagnostics").executes(this::cmdCheckpointDiagnostics)));

        // Scenario command branch
        var scenario = Commands.literal("scenario");
        scenario.then(Commands.literal("list").executes(this::cmdScenarioList));
        scenario.then(Commands.literal("describe")
                .then(Commands.literal("chunks").executes(this::cmdScenarioDescribeChunks))
                .then(Commands.literal("entities").executes(this::cmdScenarioDescribeEntities))
                .then(Commands.literal("blockentities").executes(this::cmdScenarioDescribeBlockEntities)));
        root.then(scenario);

        // Planning
        var plan = Commands.literal("plan");
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
        root.then(plan);

        // Running
        var run = Commands.literal("run").requires(s -> s.hasPermission(2));
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
        root.then(run);

        // Replay & Rerun
        root.then(Commands.literal("replay").requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", StringArgumentType.string())
                        .executes(ctx -> cmdReplay(ctx, StringArgumentType.getString(ctx, "target")))));

        root.then(Commands.literal("rerun").requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", StringArgumentType.string())
                        .executes(ctx -> cmdRerun(ctx, StringArgumentType.getString(ctx, "target")))));

        // Reports
        var report = Commands.literal("report");
        report.then(Commands.literal("list").executes(this::cmdReportList));
        report.then(Commands.literal("show")
                .then(Commands.argument("target", StringArgumentType.string())
                        .executes(ctx -> cmdReportShow(ctx, StringArgumentType.getString(ctx, "target")))));
        report.then(Commands.literal("export")
                .then(Commands.argument("target", StringArgumentType.string())
                        .executes(ctx -> cmdReportExport(ctx, StringArgumentType.getString(ctx, "target")))));
        report.then(Commands.literal("diff")
                .then(Commands.argument("runA", StringArgumentType.string())
                        .then(Commands.argument("runB", StringArgumentType.string())
                                .executes(this::cmdReportDiff))));
        root.then(report);

        // Diagnostics
        var diagnostics = Commands.literal("diagnostics").requires(s -> s.hasPermission(2));
        diagnostics.then(Commands.literal("histogram").executes(this::cmdDiagnosticsHistogram));
        diagnostics.then(Commands.literal("heapdump").executes(this::cmdDiagnosticsHeapdump));
        var jfr = Commands.literal("jfr");
        jfr.then(Commands.literal("start").executes(this::cmdDiagnosticsJfrStart));
        jfr.then(Commands.literal("stop").executes(this::cmdDiagnosticsJfrStop));
        jfr.then(Commands.literal("dump").executes(this::cmdDiagnosticsJfrDump));
        diagnostics.then(jfr);
        root.then(diagnostics);

        // Fixtures (operator only)
        var fixture = Commands.literal("fixture").requires(s -> s.hasPermission(2));
        fixture.then(Commands.literal("reset").executes(this::cmdFixtureReset));
        fixture.then(Commands.argument("mode", StringArgumentType.string())
                .executes(ctx -> cmdFixture(ctx, StringArgumentType.getString(ctx, "mode"), 10))
                .then(Commands.argument("sizeMb", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> cmdFixture(ctx, StringArgumentType.getString(ctx, "mode"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "sizeMb")))));
        root.then(fixture);

        // Adapters (operator only)
        var adapters = Commands.literal("adapters").requires(s -> s.hasPermission(2));
        adapters.then(Commands.literal("list").executes(this::cmdAdaptersList));
        root.then(adapters);

        dispatcher.register(root);
    }

    private int cmdAdaptersList(CommandContext<CommandSource> ctx) {
        com.dwurdy.heaphammer.adapter.WorkloadAdapterRegistry.getInstance().loadFromFabricEntrypoints(platform);
        List<com.dwurdy.heaphammer.adapter.WorkloadAdapter> list =
                com.dwurdy.heaphammer.adapter.WorkloadAdapterRegistry.getInstance().getAllAdapters();
        if (list.isEmpty()) {
            ctx.getSource().sendSuccess(new StringTextComponent("No external workload adapters registered.").withStyle(TextFormatting.GRAY), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder("Registered Workload Adapters (" + list.size() + "):\n");
        for (com.dwurdy.heaphammer.adapter.WorkloadAdapter adapter : list) {
            sb.append("- ").append(adapter.displayName()).append(" (ID: ").append(adapter.adapterId())
              .append("): Scenarios: [").append(String.join(", ", adapter.supportedScenarios())).append("]\n");
        }
        ctx.getSource().sendSuccess(new StringTextComponent(sb.toString().trim()).withStyle(TextFormatting.AQUA), false);
        return 1;
    }

    private int cmdFixtureReset(CommandContext<CommandSource> ctx) {
        com.dwurdy.heaphammer.fixture.SyntheticLeakFixture.reset();
        ctx.getSource().sendSuccess(new StringTextComponent("Synthetic fixture reset to OFF and storage cleared.").withStyle(TextFormatting.GREEN), true);
        return 1;
    }

    private int cmdFixture(CommandContext<CommandSource> ctx, String modeStr, int sizeMb) {
        try {
            var mode = com.dwurdy.heaphammer.fixture.SyntheticLeakFixture.Mode.valueOf(modeStr.trim().toUpperCase(Locale.ROOT));
            com.dwurdy.heaphammer.fixture.SyntheticLeakFixture.setMode(mode);
            ctx.getSource().sendSuccess(new StringTextComponent("Synthetic fixture set to " + mode + " (" + sizeMb + " MB/cycle).").withStyle(TextFormatting.GOLD), true);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(new StringTextComponent("Unknown fixture mode: " + modeStr + ". Valid modes: OFF, LEAK, CLEAN, BOUNDED."));
        }
        return 1;
    }

    private int cmdVersion(CommandContext<CommandSource> ctx) {
        EnvironmentFingerprint env = platform.captureFingerprint();
        ctx.getSource().sendSuccess(new StringTextComponent(
                String.format(Locale.ROOT, "HeapHammer v%s (Minecraft %s / Fabric)",
                        env.heapHammerVersion(), env.minecraftVersion()))
                .withStyle(TextFormatting.GOLD), false);
        return 1;
    }

    private int cmdHelp(CommandContext<CommandSource> ctx) {
        ctx.getSource().sendSuccess(new StringTextComponent(
                "--- HeapHammer Commands ---\n" +
                "/hh plan chunks [flags]   - Compute deterministic workload plan\n" +
                "/hh run chunks [flags]    - Execute chunk churn experiment\n" +
                "/hh status                - Show active experiment progress\n" +
                "/hh stop                  - Abort and release all tickets\n" +
                "/hh cleanup               - Purge any remaining tickets\n" +
                "/hh metrics               - Display instant JVM/world metrics\n" +
                "/hh doctor                - Check server health & safety\n" +
                "/hh replay <run-id|last>  - Replay exact resolved operations\n" +
                "/hh rerun <run-id|last>   - Rerun from original spec & seed\n" +
                "/hh report list|show <id> - Inspect previous test reports"
        ).withStyle(TextFormatting.YELLOW), false);
        return 1;
    }

    private int cmdHelpTopic(CommandContext<CommandSource> ctx) {
        String topic = StringArgumentType.getString(ctx, "topic");
        ctx.getSource().sendSuccess(new StringTextComponent("Help topic: " + topic).withStyle(TextFormatting.AQUA), false);
        return 1;
    }

    private int cmdCapabilities(CommandContext<CommandSource> ctx) {
        Runtime rt = Runtime.getRuntime();
        long maxMb = rt.maxMemory() / (1024 * 1024);
        long totalMb = rt.totalMemory() / (1024 * 1024);
        ctx.getSource().sendSuccess(new StringTextComponent(
                "HeapHammer Capabilities:\n" +
                "- Scenario: chunks (v1.0)\n" +
                "- JVM Max Memory: " + maxMb + " MB (Allocated: " + totalMb + " MB)\n" +
                "- Platform: Fabric (Server-side only)\n" +
                "- Ticket Type: heaphammer (distance 1)"
        ).withStyle(TextFormatting.GREEN), false);
        return 1;
    }

    private int cmdDoctor(CommandContext<CommandSource> ctx) {
        int totalLoaded = platform.getTotalLoadedChunkCount();
        ctx.getSource().sendSuccess(new StringTextComponent(
                "HeapHammer Doctor:\n" +
                "- Server status: READY\n" +
                "- Total loaded chunks: " + totalLoaded + "\n" +
                "- Active tickets owned by HeapHammer: " + platform.getChunkTicketManager().getActiveTicketCount() + "\n" +
                "- Warning: Always run memory experiments on a test world or dedicated staging server!"
        ).withStyle(TextFormatting.AQUA), false);
        return 1;
    }

    private int cmdStatus(CommandContext<CommandSource> ctx) {
        Optional<ScenarioExecutor> opt = experimentService.getActiveExecutor();
        if (opt.isEmpty() || !opt.get().getStateMachine().getState().isActive()) {
            ctx.getSource().sendSuccess(new StringTextComponent("No experiment currently active.").withStyle(TextFormatting.GRAY), false);
            return 1;
        }

        ScenarioExecutor executor = opt.get();
        ExperimentPlan plan = executor.getPlan();
        int currentIter = executor.getCurrentIteration();
        int totalIter = plan.spec().iterations();
        ctx.getSource().sendSuccess(new StringTextComponent(
                "Active Experiment: " + plan.id() + " (" + plan.spec().scenarioId() + ")\n" +
                "- State: " + executor.getStateMachine().getState() + " (" + executor.getStateMachine().getStatusMessage() + ")\n" +
                "- Iteration: " + currentIter + " / " + totalIter + "\n" +
                "- Active Tickets: " + platform.getChunkTicketManager().getActiveTicketCount()
        ).withStyle(TextFormatting.YELLOW), false);
        return 1;
    }

    private int cmdMetrics(CommandContext<CommandSource> ctx) {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long committedMb = rt.totalMemory() / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        int totalChunks = platform.getTotalLoadedChunkCount();

        ctx.getSource().sendSuccess(new StringTextComponent(String.format(Locale.ROOT,
                "Metrics: Heap: %d MB / %d MB (Max %d MB) | Loaded Chunks: %d | Active Tickets: %d",
                usedMb, committedMb, maxMb, totalChunks, platform.getChunkTicketManager().getActiveTicketCount()
        )).withStyle(TextFormatting.AQUA), false);
        return 1;
    }

    private int cmdStop(CommandContext<CommandSource> ctx) {
        boolean stopped = experimentService.stop("Cancelled by operator command");
        if (stopped) {
            ctx.getSource().sendSuccess(new StringTextComponent("Experiment cancelled. Releasing all tickets.").withStyle(TextFormatting.RED), true);
        } else {
            ctx.getSource().sendFailure(new StringTextComponent("No active experiment to stop."));
        }
        return 1;
    }

    private int cmdCleanup(CommandContext<CommandSource> ctx) {
        int countBefore = platform.getChunkTicketManager().getActiveTicketCount();
        platform.getChunkTicketManager().releaseAllTickets();
        String dim = "minecraft:overworld";
        int entitiesPurged = platform.removeAllTestEntities(dim);
        int blockEntitiesPurged = platform.removeAllTestBlockEntities(dim);
        ctx.getSource().sendSuccess(new StringTextComponent("Purged " + countBefore + " tickets, " +
                entitiesPurged + " test entities, and " + blockEntitiesPurged + " test block entities.").withStyle(TextFormatting.GREEN), true);
        return 1;
    }

    private int cmdCheckpoint(CommandContext<CommandSource> ctx) {
        Checkpoint cp = checkpointService.recordCheckpoint(CheckpointPhase.MANUAL, 0, "minecraft:overworld", false);
        ctx.getSource().sendSuccess(new StringTextComponent("Recorded manual checkpoint: Heap used = " +
                String.format(Locale.ROOT, "%.2f MB", cp.metrics().heapUsedMb())).withStyle(TextFormatting.GREEN), false);
        return 1;
    }

    private int cmdScenarioList(CommandContext<CommandSource> ctx) {
        ctx.getSource().sendSuccess(new StringTextComponent(
                "Available Scenarios:\n" +
                "- chunks (v1.0): Deterministic chunk load/unload churn\n" +
                "- entities (v1.0): Deterministic entity lifecycle churn\n" +
                "- blockentities (v1.0): Conservative block entity placement and destruction stress"
        ).withStyle(TextFormatting.YELLOW), false);
        return 1;
    }

    private int cmdScenarioDescribeChunks(CommandContext<CommandSource> ctx) {
        ctx.getSource().sendSuccess(new StringTextComponent(
                "Scenario: chunks\n" +
                "Acquires, holds, and releases chunk tickets within a specified radius using selectable strategies (SPIRAL, RING, RANDOM_WALK, HOTSPOT_CHURN, GRID_SWEEP)."
        ).withStyle(TextFormatting.YELLOW), false);
        return 1;
    }

    private int cmdScenarioDescribeEntities(CommandContext<CommandSource> ctx) {
        ctx.getSource().sendSuccess(new StringTextComponent(
                "Scenario: entities\n" +
                "Spawns deterministic entity batches, exercises them for configured lifetime ticks, then discards or kills them to verify complete cleanup."
        ).withStyle(TextFormatting.YELLOW), false);
        return 1;
    }

    private int cmdScenarioDescribeBlockEntities(CommandContext<CommandSource> ctx) {
        ctx.getSource().sendSuccess(new StringTextComponent(
                "Scenario: blockentities\n" +
                "Places deterministic block entity states in a test grid, allows conservative tick initialization, then removes blocks to verify lifecycle cleanup."
        ).withStyle(TextFormatting.YELLOW), false);
        return 1;
    }

    private int cmdPlanChunks(CommandContext<CommandSource> ctx, String flagsString) {
        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;

        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        ExperimentSpec spec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);

        ExperimentPlan plan = planner.plan(spec);
        try {
            Path path = planStorage.savePlan(plan);
            ctx.getSource().sendSuccess(new StringTextComponent(
                    "Plan Created: " + plan.id() + "\n" +
                    "- Operations: " + plan.totalOperations() + " (Unique chunks: " + plan.uniqueChunksCount() + ")\n" +
                    "- Estimated Ticks: " + plan.estimatedDurationTicks() + "\n" +
                    "- Saved to: " + path.getFileName()
            ).withStyle(TextFormatting.GREEN), false);
        } catch (IOException e) {
            ctx.getSource().sendFailure(new StringTextComponent("Failed to persist plan: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdRunChunks(CommandContext<CommandSource> ctx, String flagsString) {
        if (experimentService.isExperimentActive()) {
            ctx.getSource().sendFailure(new StringTextComponent("An experiment is already in progress. Use /hh stop first."));
            return 0;
        }

        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;

        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        ExperimentSpec spec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);

        checkpointService.clear();
        ExperimentPlan plan = planner.plan(spec);
        try {
            planStorage.savePlan(plan);
            experimentService.start(plan);
            ctx.getSource().sendSuccess(new StringTextComponent(
                    "Started Experiment: " + plan.id() + " (" + spec.iterations() + " cycles, radius " + spec.radius() + ")"
            ).withStyle(TextFormatting.GOLD), true);
        } catch (Exception e) {
            ctx.getSource().sendFailure(new StringTextComponent("Failed to start experiment: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdPlanEntities(CommandContext<CommandSource> ctx, String flagsString) {
        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;

        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        ExperimentSpec baseSpec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.ENTITIES)
                .seed(baseSpec.seed())
                .dimension(baseSpec.dimension())
                .center((int) Math.floor(pos.x), (int) Math.floor(pos.z))
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
        try {
            Path path = planStorage.savePlan(plan);
            ctx.getSource().sendSuccess(new StringTextComponent(
                    "Entity Plan Created: " + plan.id() + "\n" +
                    "- Operations: " + plan.totalOperations() + "\n" +
                    "- Estimated Ticks: " + plan.estimatedDurationTicks() + "\n" +
                    "- Saved to: " + path.getFileName()
            ).withStyle(TextFormatting.GREEN), false);
        } catch (IOException e) {
            ctx.getSource().sendFailure(new StringTextComponent("Failed to persist entity plan: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdRunEntities(CommandContext<CommandSource> ctx, String flagsString) {
        if (experimentService.isExperimentActive()) {
            ctx.getSource().sendFailure(new StringTextComponent("An experiment is already in progress. Use /hh stop first."));
            return 0;
        }

        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;

        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        ExperimentSpec baseSpec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.ENTITIES)
                .seed(baseSpec.seed())
                .dimension(baseSpec.dimension())
                .center((int) Math.floor(pos.x), (int) Math.floor(pos.z))
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

        checkpointService.clear();
        List<String> available = platform.getAvailableEntityTypes();
        ExperimentPlan plan = entityPlanner.plan(spec, available);
        try {
            planStorage.savePlan(plan);
            experimentService.start(plan);
            ctx.getSource().sendSuccess(new StringTextComponent(
                    "Started Entity Experiment: " + plan.id() + " (" + spec.iterations() + " cycles, " + spec.batchSize() + " entities/batch)"
            ).withStyle(TextFormatting.GOLD), true);
        } catch (Exception e) {
            ctx.getSource().sendFailure(new StringTextComponent("Failed to start entity experiment: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdPlanBlockEntities(CommandContext<CommandSource> ctx, String flagsString) {
        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;

        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        ExperimentSpec baseSpec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.BLOCK_ENTITIES)
                .seed(baseSpec.seed())
                .dimension(baseSpec.dimension())
                .center((int) Math.floor(pos.x), (int) Math.floor(pos.z))
                .radius(baseSpec.radius())
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
        try {
            Path path = planStorage.savePlan(plan);
            ctx.getSource().sendSuccess(new StringTextComponent(
                    "Block Entity Plan Created: " + plan.id() + "\n" +
                    "- Operations: " + plan.totalOperations() + "\n" +
                    "- Estimated Ticks: " + plan.estimatedDurationTicks() + "\n" +
                    "- Saved to: " + path.getFileName()
            ).withStyle(TextFormatting.GREEN), false);
        } catch (IOException e) {
            ctx.getSource().sendFailure(new StringTextComponent("Failed to persist block entity plan: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdRunBlockEntities(CommandContext<CommandSource> ctx, String flagsString) {
        if (experimentService.isExperimentActive()) {
            ctx.getSource().sendFailure(new StringTextComponent("An experiment is already in progress. Use /hh stop first."));
            return 0;
        }

        var pos = ctx.getSource().getPosition();
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;

        String[] rawTokens = flagsString.isBlank() ? new String[0] : flagsString.split("\\s+");
        ExperimentSpec baseSpec = FlagParser.parseSpec(rawTokens, 0, chunkX, chunkZ);
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.BLOCK_ENTITIES)
                .seed(baseSpec.seed())
                .dimension(baseSpec.dimension())
                .center((int) Math.floor(pos.x), (int) Math.floor(pos.z))
                .radius(baseSpec.radius())
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

        checkpointService.clear();
        List<String> available = platform.getAvailableBlockEntityTypes();
        ExperimentPlan plan = blockEntityPlanner.plan(spec, available);
        try {
            planStorage.savePlan(plan);
            experimentService.start(plan);
            ctx.getSource().sendSuccess(new StringTextComponent(
                    "Started Block Entity Experiment: " + plan.id() + " (" + spec.iterations() + " cycles, " + spec.batchSize() + " block entities/batch)"
            ).withStyle(TextFormatting.GOLD), true);
        } catch (Exception e) {
            ctx.getSource().sendFailure(new StringTextComponent("Failed to start block entity experiment: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdReplay(CommandContext<CommandSource> ctx, String target) {
        try {
            checkpointService.clear();
            ScenarioExecutor executor = replayService.replay(target);
            ctx.getSource().sendSuccess(new StringTextComponent("Replaying plan: " + executor.getPlan().id()).withStyle(TextFormatting.GOLD), true);
        } catch (Exception e) {
            ctx.getSource().sendFailure(new StringTextComponent("Replay failed: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdRerun(CommandContext<CommandSource> ctx, String target) {
        try {
            checkpointService.clear();
            ScenarioExecutor executor = replayService.rerun(target);
            ctx.getSource().sendSuccess(new StringTextComponent("Rerunning spec for: " + executor.getPlan().id()).withStyle(TextFormatting.GOLD), true);
        } catch (Exception e) {
            ctx.getSource().sendFailure(new StringTextComponent("Rerun failed: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdReportList(CommandContext<CommandSource> ctx) {
        try {
            List<String> reports = reportService.listReportIds();
            if (reports.isEmpty()) {
                ctx.getSource().sendSuccess(new StringTextComponent("No reports found.").withStyle(TextFormatting.GRAY), false);
            } else {
                ctx.getSource().sendSuccess(new StringTextComponent("Saved Reports (" + reports.size() + "):\n- " +
                        String.join("\n- ", reports.stream().limit(10).toList())).withStyle(TextFormatting.YELLOW), false);
            }
        } catch (IOException e) {
            ctx.getSource().sendFailure(new StringTextComponent("Error listing reports: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdReportShow(CommandContext<CommandSource> ctx, String target) {
        try {
            Optional<ExperimentReport> repOpt = "last".equalsIgnoreCase(target) ?
                    reportService.loadLatestReport() : reportService.loadReport(ExperimentId.of(target));
            if (repOpt.isEmpty()) {
                ctx.getSource().sendFailure(new StringTextComponent("Report not found: " + target));
            } else {
                String summary = reportService.formatSummary(repOpt.get());
                ctx.getSource().sendSuccess(new StringTextComponent(summary).withStyle(TextFormatting.YELLOW), false);
            }
        } catch (IOException e) {
            ctx.getSource().sendFailure(new StringTextComponent("Error loading report: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdReportExport(CommandContext<CommandSource> ctx, String target) {
        cmdReportShow(ctx, target);
        return 1;
    }

    private int cmdReportDiff(CommandContext<CommandSource> ctx) {
        String targetA = StringArgumentType.getString(ctx, "runA");
        String targetB = StringArgumentType.getString(ctx, "runB");
        try {
            Optional<ExperimentReport> repA = "last".equalsIgnoreCase(targetA) ?
                    reportService.loadLatestReport() : reportService.loadReport(ExperimentId.of(targetA));
            Optional<ExperimentReport> repB = "last".equalsIgnoreCase(targetB) ?
                    reportService.loadLatestReport() : reportService.loadReport(ExperimentId.of(targetB));

            if (repA.isEmpty()) {
                ctx.getSource().sendFailure(new StringTextComponent("Report A not found: " + targetA));
                return 0;
            }
            if (repB.isEmpty()) {
                ctx.getSource().sendFailure(new StringTextComponent("Report B not found: " + targetB));
                return 0;
            }

            ReportDiff diff = ReportDiffer.diff(repA.get(), repB.get());
            ctx.getSource().sendSuccess(new StringTextComponent(
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
                    "Classification: " + diff.classificationA() + " -> " + diff.classificationB() +
                    (diff.classificationChanged() ? " (CHANGED)" : "") +
                    (diff.warnings().isEmpty() ? "" : "\nWarnings: " + String.join("; ", diff.warnings()))
            ).withStyle(TextFormatting.AQUA), false);
        } catch (IOException e) {
            ctx.getSource().sendFailure(new StringTextComponent("Error reading reports: " + e.getMessage()));
        }
        return 1;
    }

    private int cmdCheckpointDiagnostics(CommandContext<CommandSource> ctx) {
        cmdCheckpoint(ctx);
        if (!histogramCollector.isSupported()) {
            ctx.getSource().sendSuccess(new StringTextComponent("Diagnostics: Histogram capture unsupported on this JVM.")
                    .withStyle(TextFormatting.GRAY), false);
            return 1;
        }
        Optional<ClassHistogram> hist = histogramCollector.capture(5);
        if (hist.isPresent() && !hist.get().entries().isEmpty()) {
            StringBuilder sb = new StringBuilder("Top 5 Classes:\n");
            for (ClassHistogramEntry e : hist.get().entries()) {
                sb.append(String.format(Locale.ROOT, "- #%d %s: %d (%.2f MB)\n",
                        e.rank(), e.className(), e.instances(), e.bytesMb()));
            }
            ctx.getSource().sendSuccess(new StringTextComponent(sb.toString()).withStyle(TextFormatting.GOLD), false);
        }
        return 1;
    }

    private int cmdDiagnosticsHistogram(CommandContext<CommandSource> ctx) {
        if (!histogramCollector.isSupported()) {
            ctx.getSource().sendFailure(new StringTextComponent("Class histogram capture is not supported on this JVM."));
            return 0;
        }
        ctx.getSource().sendSuccess(new StringTextComponent("Capturing JVM class histogram...").withStyle(TextFormatting.GRAY), false);
        Optional<ClassHistogram> histOpt = histogramCollector.capture(10);
        if (histOpt.isEmpty() || histOpt.get().entries().isEmpty()) {
            ctx.getSource().sendFailure(new StringTextComponent("Failed to capture class histogram."));
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
        ctx.getSource().sendSuccess(new StringTextComponent(sb.toString()).withStyle(TextFormatting.GOLD), false);
        return 1;
    }

    private int cmdDiagnosticsHeapdump(CommandContext<CommandSource> ctx) {
        if (!heapDumpService.isSupported()) {
            ctx.getSource().sendFailure(new StringTextComponent("Heap dumping is not supported on this JVM (HotSpotDiagnosticMXBean missing)."));
            return 0;
        }
        Path dumpDir = Path.of("heaphammer", "reports", "heapdumps");
        String filename = "manual-" + System.currentTimeMillis() + ".hprof";
        ctx.getSource().sendSuccess(new StringTextComponent("Triggering async heap dump to " + filename + " (Warning: temporary STW pause possible)...")
                .withStyle(TextFormatting.RED), true);
        heapDumpService.dumpHeapAsync(dumpDir, filename, true)
                .thenAccept(path -> LOGGER.info("Manual heap dump written to {}", path))
                .exceptionally(ex -> {
                    LOGGER.error("Manual heap dump failed", ex);
                    return null;
                });
        return 1;
    }

    private int cmdDiagnosticsJfrStart(CommandContext<CommandSource> ctx) {
        if (!JfrTrigger.isAvailable()) {
            ctx.getSource().sendFailure(new StringTextComponent("Java Flight Recorder is not available on this JVM."));
            return 0;
        }
        boolean started = jfrTrigger.start("HeapHammer-Manual-" + System.currentTimeMillis());
        if (started) {
            ctx.getSource().sendSuccess(new StringTextComponent("JFR recording started.").withStyle(TextFormatting.GREEN), true);
        } else {
            ctx.getSource().sendFailure(new StringTextComponent("Failed to start JFR recording (may already be active)."));
        }
        return 1;
    }

    private int cmdDiagnosticsJfrStop(CommandContext<CommandSource> ctx) {
        if (!jfrTrigger.isRecording()) {
            ctx.getSource().sendFailure(new StringTextComponent("No active JFR recording."));
            return 0;
        }
        jfrTrigger.stop();
        ctx.getSource().sendSuccess(new StringTextComponent("JFR recording stopped.").withStyle(TextFormatting.YELLOW), true);
        return 1;
    }

    private int cmdDiagnosticsJfrDump(CommandContext<CommandSource> ctx) {
        if (!jfrTrigger.isRecording()) {
            ctx.getSource().sendFailure(new StringTextComponent("No active JFR recording to dump."));
            return 0;
        }
        Path dir = Path.of("heaphammer", "reports", "jfr");
        Path dumped = jfrTrigger.dump(dir, "manual-" + System.currentTimeMillis());
        if (dumped != null) {
            ctx.getSource().sendSuccess(new StringTextComponent("Dumped JFR to: " + dumped).withStyle(TextFormatting.GOLD), true);
        } else {
            ctx.getSource().sendFailure(new StringTextComponent("Failed to dump JFR recording."));
        }
        return 1;
    }

    private int cmdInspectMods(CommandContext<CommandSource> ctx) {
        EnvironmentFingerprint env = platform.captureFingerprint();
        ctx.getSource().sendSuccess(new StringTextComponent(
                "Installed Mods (" + env.installedMods().size() + "):\n" +
                "Hash: " + env.modpackHash() + "\n" +
                String.join(", ", env.installedMods().keySet().stream().limit(15).toList()) + "..."
        ).withStyle(TextFormatting.AQUA), false);
        return 1;
    }

    private void onExperimentFinished(ScenarioExecutor executor) {
        ExperimentPlan plan = executor.getPlan();
        List<Checkpoint> cps = checkpointService.getCheckpoints();
        DetectionResult detection = trendAnalyzer.analyze(plan.spec(), cps);
        EnvironmentFingerprint env = platform.captureFingerprint();

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
