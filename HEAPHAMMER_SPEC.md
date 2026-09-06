# HeapHammer

> **Reproducible stress testing for modded Minecraft.**
>
> Core philosophy: build the dumbest tool that reliably makes server-side retention/resource problems reproducible.

---

## 1. Executive Summary

HeapHammer is a **server-side deterministic workload and retained-memory regression testing framework for modded Minecraft servers**.

Its purpose is not to magically identify the guilty mod, replace profilers, or simulate human players with perfect fidelity. Its first job is much simpler:

> Take a problem that appears after hours or days under production activity, compress the relevant activity into a repeatable staging workload, and produce a replayable test case with objective evidence.

A successful HeapHammer run should be able to answer:

- What workload was executed?
- Can the same workload be replayed exactly?
- Did the server return to approximately the same world state after cleanup?
- Did post-cleanup / post-GC retained heap continue to grow across repeated cycles?
- Which classes/packages/registries grew suspiciously, when diagnostics are enabled?
- Can another operator or maintainer rerun the same experiment from a report?

HeapHammer should complement existing tools such as **spark**, Java Flight Recorder, class histograms, heap dumps, JMX, and heap analyzers. Those tools are microscopes. HeapHammer is the machine that repeatedly creates the failure under controlled conditions.

---

## 2. Problem Statement

Large modpacks commonly contain 200–300+ mods. A server can be stable when individual mods are tested in isolation but fail when combinations interact.

Typical production-only symptoms include:

- Java heap steadily increasing until restart/crash.
- Loaded objects remaining reachable after their world state should have been destroyed.
- Chunks or related managers retaining references after unload.
- Entities or block entities accumulating unexpectedly.
- Listeners, callbacks, scheduled tasks, caches, or registries retaining stale objects.
- Player/session state surviving logout.
- Menu/container or capability/attachment state remaining referenced.
- Cross-mod interactions that only appear after repeated lifecycle churn.
- Resource growth that only appears after a particular sequence of chunk, item, entity, player, or dimension operations.

The operational problem is that staging often has too little activity to reproduce the issue. Waiting multiple days is too slow, and normal profilers can show what exists without telling us which workload reliably caused it.

HeapHammer therefore focuses on **reproducible workload generation and replay**.

---

## 3. Product Positioning

### 3.1 What HeapHammer is

HeapHammer is:

- A deterministic server workload generator.
- A repeatable test harness for lifecycle-heavy Minecraft/mod operations.
- A memory/resource regression detector.
- A replay system.
- A staging/CI tool for modpack maintainers and server operators.
- An extensible platform for mod-specific workload adapters.

### 3.2 What HeapHammer is not

HeapHammer is **not**:

- An AI-first product.
- A machine-learning memory leak detector.
- A replacement for spark.
- A replacement for JFR, heap dumps, MAT, VisualVM, JMC, `jcmd`, or JMX.
- A guaranteed root-cause detector.
- A perfect simulation of real players.
- A client-side testing mod in its core form.
- A tick-speed mod.
- A tool that should modify gameplay content or synced registries unnecessarily.

### 3.3 Value proposition

Existing profiling tools answer questions like:

> What is the server doing right now?

HeapHammer should answer:

> What workload makes this server break, and can I reproduce it?

---

## 4. Business Rules

These rules define product behavior and should be treated as implementation constraints.

### BR-001 — Determinism first

Every executable scenario must support deterministic reproduction.

At minimum each run must record:

- HeapHammer version.
- Scenario version.
- Minecraft version.
- Loader and loader version.
- Java version.
- Mod IDs and versions.
- Modpack fingerprint/hash.
- World seed.
- Dimension(s).
- HeapHammer workload seed.
- Effective scenario configuration.
- Resolved operation sequence.

The workload seed is **separate from the Minecraft world seed**.

### BR-002 — Resolved operations are stronger than RNG replay

A seed is useful, but a replay should not depend only on rerunning the current RNG/generator implementation.

HeapHammer must persist the final resolved operations whenever practical.

Example:

- Exact chunk coordinates.
- Exact entity registry IDs.
- Exact item registry IDs.
- Exact order of operations.
- Exact action types.
- Exact dimension IDs.

`replay` uses resolved operations.

`rerun` uses original configuration + seed through the current generator.

### BR-003 — Core is server-side only

HeapHammer Core must operate as a **server-side-only mod**.

Ordinary clients must not need HeapHammer installed.

Core should avoid adding gameplay content or synced registry entries unless technically unavoidable.

### BR-004 — Do not claim more than the evidence proves

HeapHammer must prefer classifications such as:

- `PASS`
- `INCONCLUSIVE`
- `SUSPICIOUS`
- `WORKLOAD_FAILED`
- `CLEANUP_FAILED`
- `ENVIRONMENT_MISMATCH`

It should not say `MEMORY LEAK CONFIRMED` solely from a rising heap line.

### BR-005 — Cleanup is part of the experiment

Every scenario must define:

1. setup,
2. exercise,
3. cleanup,
4. settle,
5. validation,
6. checkpoint.

If cleanup cannot be validated, the corresponding checkpoint must be marked partial or invalid.

### BR-006 — Workload compression, not blind tick acceleration

HeapHammer should compress the **frequency of suspected trigger activity**, not assume that globally increasing tick speed reproduces production.

Useful compression examples:

- more chunk load/unload cycles per minute,
- more entity spawn/remove cycles,
- more player/session lifecycles,
- more inventory/menu lifecycles,
- more dimension transitions.

A global tick-rate multiplier must not be a core assumption.

### BR-007 — Keep the MVP dumb

Version 0.1 should solve one problem well:

> Run deterministic chunk churn, clean up, measure, repeat, report, replay.

Do not block MVP on:

- fake players,
- GUI automation,
- AI analysis,
- mod bisection,
- cross-loader support,
- perfect root-cause attribution.

### BR-008 — Advanced JVM diagnostics are optional and event-triggered

Heavy diagnostics must not run continuously by default.

Examples:

- full heap dumps,
- detailed class histograms,
- JFR dumps,
- forced GC.

They should be manually triggered or conditionally captured when the experiment becomes suspicious.

### BR-009 — Explicit GC is staging-only measurement support

HeapHammer may optionally request explicit GC at controlled checkpoints if explicitly enabled.

It must never present forced GC as a production fix.

Default behavior should be no explicit GC unless configured otherwise.

### BR-010 — Preserve server safety

HeapHammer must implement budgets and abort conditions.

Examples:

- max operations per tick,
- max HeapHammer time budget per tick,
- max loaded test chunks,
- max spawned test entities,
- max experiment duration,
- cleanup timeout,
- emergency stop,
- test-world warnings.

### BR-011 — Reports are first-class outputs

Every completed or interrupted run must produce a structured report when possible.

Reports must contain enough information to:

- understand the test,
- validate environment equivalence,
- inspect metrics,
- rerun/replay,
- compare with another run.

### BR-012 — Generic automation must be conservative

The presence of a registry entry does not prove that HeapHammer can safely exercise it.

For items, menus, block entities, machines, or mod-specific systems, HeapHammer should classify targets as:

- `SUPPORTED`
- `PARTIALLY_SUPPORTED`
- `UNSUPPORTED`
- `FAILED_TO_INITIALIZE`

Do not blindly invoke arbitrary mod logic.

### BR-013 — Extension adapters are the escape hatch

When generic testing cannot faithfully construct or exercise a mod system, use a workload adapter.

Example adapter IDs:

- `create:contraption_cycle`
- `ae2:grid_create_destroy`
- `mekanism:machine_cycle`

HeapHammer Core remains generic; adapters understand mod-specific valid lifecycle setup and teardown.

### BR-014 — Client fidelity must be explicit

Record actor type in reports:

- `direct`
- `fake-player`
- `network-client`
- `real-client`

Do not treat them as equivalent.

### BR-015 — One loader first

The first production-quality implementation should target one loader/version family only.

Cross-loader abstraction should exist only where it does not compromise the implementation.

Do not build a giant abstraction framework before the first scenario works.

---

## 5. Core Experiment Model

Every experiment follows this high-level lifecycle:

```text
Generate / Resolve
        ↓
Setup
        ↓
Exercise
        ↓
Cleanup
        ↓
Settle
        ↓
Validate world-state recovery
        ↓
Measure / Checkpoint
        ↓
Repeat
```

A leak-like signal becomes more credible when:

1. workload cycles are repeatable,
2. expected world state returns close to baseline,
3. GC has had a fair chance to reclaim memory,
4. retained/post-GC heap keeps ratcheting upward,
5. the trend persists beyond warmup/cache initialization,
6. the same experiment reproduces the trend again.

---

## 6. Detection Philosophy

### 6.1 Primary heuristic

For MVP, HeapHammer should use a simple rule-based detector based on:

- post-cleanup heap,
- post-GC heap when available/appropriate,
- retained growth slope across checkpoints,
- world-state recovery,
- iteration count,
- repeatability.

### 6.2 Metrics to collect

At minimum:

#### JVM

- heap used,
- heap committed,
- heap max,
- GC collection count,
- GC collection time,
- optionally memory pool metrics,
- optionally metaspace,
- optionally process RSS/native metrics when accessible.

#### Minecraft/server

- loaded chunks,
- entities,
- block entities,
- players,
- dimensions currently loaded if relevant,
- average tick time,
- p95 tick time,
- max tick time,
- scenario iteration,
- operations completed.

#### Experiment validation

- expected temporary chunks released,
- expected test entities removed,
- expected temporary tickets removed,
- cleanup duration,
- settle duration,
- checkpoint validity.

### 6.3 Warmup

Tests must support ignored warmup iterations.

Reason:

- lazy class loading,
- cache warming,
- first-time registry work,
- first-time terrain generation,
- JIT compilation,
- mod initialization.

A rising heap during warmup is not sufficient evidence.

### 6.4 False positives

HeapHammer must account for:

- intentionally bounded caches,
- lazy initialization,
- JIT/code cache effects,
- delayed GC,
- old-gen promotion,
- allocation bursts,
- chunk saves still queued,
- async cleanup,
- world generation caches,
- memory fragmentation/allocator behavior,
- diagnostic tooling overhead.

### 6.5 False negatives

HeapHammer may miss:

- leaks requiring real client behavior,
- extremely slow accumulation,
- native/off-heap leaks,
- network buffer leaks,
- metaspace/classloader leaks,
- rare race conditions,
- workload sequences not represented by current scenarios,
- leaks hidden by GC timing during short tests.

### 6.6 Stronger diagnostics later

When available, correlate suspicious checkpoints with:

- JVM class histograms,
- JFR events,
- heap dumps,
- dominator trees in external analyzers,
- package/class growth,
- allocation rate changes,
- native memory tracking if enabled.

HeapHammer does not need to implement its own heap analyzer.

---

## 7. Command Design

Root command:

```text
/heaphammer
```

Alias:

```text
/hh
```

HeapHammer supports **both Minecraft/Brigadier-style positional arguments and named `--flags`**.

### 7.1 Command syntax rule

- Required/common arguments may be positional.
- Named flags are the canonical explicit representation.
- Both styles may be mixed.
- Providing the same logical argument twice is an error.

Equivalent examples:

```text
/hh run chunks 1000 100 481293
```

```text
/hh run chunks 1000 100 --seed 481293
```

```text
/hh run chunks --iterations 1000 --batch 100 --seed 481293
```

### 7.2 Duplicate argument behavior

This should fail:

```text
/hh run chunks 1000 --iterations 500
```

Example response:

```text
HeapHammer: iterations was provided twice.
Positional: 1000
Named: --iterations 500
Use only one.
```

### 7.3 Canonical command

After parsing, normalize input to a single internal `ExperimentSpec`.

Reports should print a fully explicit canonical command regardless of how the user entered it.

---

## 8. Command Tree

```text
/heaphammer
├── help [command]
├── version
├── capabilities
├── doctor
├── status
├── stop [--now]
├── pause
├── resume
├── cleanup [run-id]
├── metrics
├── checkpoint [label]
├── gc
│
├── scenario
│   ├── list
│   └── describe <scenario>
│
├── plan <scenario> ...
├── run <scenario> ...
├── replay <run-id|last> [--allow-mismatch]
├── rerun [run-id|last] [--new-seed]
│
├── baseline
│   ├── capture
│   ├── show
│   └── clear
│
├── report
│   ├── list
│   ├── show <run-id|last>
│   ├── compare <run-a> <run-b>
│   └── export <run-id|last>
│
├── inspect
│   ├── mods
│   ├── registry <type> [--mod <id>]
│   ├── items [mod]
│   ├── entities [mod]
│   ├── blockentities [mod]
│   └── dimensions
│
├── adapters
│   ├── list
│   └── describe <id>
│
├── diagnostics
│   ├── histogram
│   ├── heapdump
│   └── jfr
│       ├── start
│       ├── mark <label>
│       └── stop
│
├── config
│   ├── show
│   ├── set <key> <value>
│   └── reload
│
└── suite
    ├── list
    └── describe <suite>
```

---

## 9. Commands and Behavior

### `/hh help [command]`

Shows syntax, examples, flags, safety notes, and scenario-specific behavior.

### `/hh version`

Shows:

- HeapHammer version,
- Minecraft version,
- loader/version,
- Java version,
- report/schema version.

### `/hh capabilities`

Shows which features are available in the current environment.

Examples:

- chunk scenario supported,
- explicit GC allowed/disabled,
- JFR available,
- heap dump available,
- histogram available,
- spark detected,
- fake-player support available/unavailable.

### `/hh doctor`

Preflight diagnostics before running tests.

Should inspect:

- supported Minecraft/loader version,
- Java diagnostics availability,
- report directory permissions,
- current player count,
- heap headroom,
- test-world warnings,
- explicit GC configuration,
- optional spark presence,
- scenario prerequisites.

### `/hh status`

Shows active run:

- run ID,
- scenario,
- phase,
- iteration,
- operations completed,
- elapsed time,
- current heap,
- baseline heap,
- loaded chunks/entities,
- current classification.

### `/hh stop`

Gracefully stop after the current atomic operation and run normal cleanup.

### `/hh stop --now`

Emergency abort followed by best-effort cleanup.

### `/hh pause`

Pause workload generation without destroying state.

### `/hh resume`

Resume a paused run.

### `/hh cleanup [run-id]`

Remove HeapHammer-owned temporary resources where possible.

Examples:

- chunk tickets,
- temporary entities,
- experiment bookkeeping.

### `/hh metrics`

Display current JVM and Minecraft metrics.

### `/hh checkpoint [label]`

Create an immediate measurement checkpoint.

### `/hh gc`

Request explicit GC only if enabled by configuration.

Must clearly state that this is a controlled staging measurement action, not a production fix.

### `/hh scenario list`

List available scenarios.

### `/hh scenario describe <scenario>`

Explain:

- what it mutates,
- lifecycle phases,
- cleanup semantics,
- supported arguments,
- known limitations,
- whether a disposable world is recommended.

### `/hh plan <scenario> ...`

Resolve the exact operations without executing them.

Produces a `.hhplan` or equivalent plan artifact.

### `/hh run <scenario> ...`

Resolve and execute a scenario.

### `/hh replay <run-id>`

Replay saved resolved operations.

Strict environment validation by default.

### `/hh rerun <run-id>`

Recreate the experiment from the original spec + seed using the current scenario generator.

### `/hh rerun --new-seed`

Reuse configuration with a new workload seed.

### `/hh baseline capture`

Capture manual comparison baseline.

### `/hh baseline show`

Show baseline metrics.

### `/hh baseline clear`

Clear manual baseline.

### `/hh report list`

List recent reports.

### `/hh report show <run-id>`

Show summary, trend, validation, and replay information.

### `/hh report compare <run-a> <run-b>`

Compare two runs.

### `/hh report export <run-id>`

Export the full structured report.

### `/hh inspect mods`

List mod IDs and versions included in environment fingerprinting.

### `/hh inspect registry <type>`

Inspect available registry entries.

### `/hh adapters list`

List installed workload adapters.

### `/hh diagnostics histogram`

Capture a JVM class histogram if supported.

### `/hh diagnostics heapdump`

Capture a heap dump if supported.

Must show a strong warning because this can pause the JVM and generate a very large file.

### `/hh diagnostics jfr ...`

Control an optional Java Flight Recorder capture.

### `/hh config show`

Show effective configuration.

### `/hh config set <key> <value>`

Modify runtime-safe settings only.

### `/hh config reload`

Reload safe settings.

---

## 10. Common Scenario Arguments

```text
--seed <long>
```

Deterministic workload seed.

```text
--iterations <n>
```

Number of complete cycles.

```text
--batch <n>
```

Operations/targets per iteration.

```text
--coverage <0-100>
```

Percentage of matching registry targets to sample.

```text
--include-mod <ids>
--exclude-mod <ids>
--mods <ids>
```

Registry filtering.

```text
--warmup <n>
```

Iterations excluded from trend analysis.

```text
--checkpoint-every <n>
```

Checkpoint frequency.

```text
--settle-ticks <n>
```

Wait after cleanup before validating/measuring.

```text
--timeout <duration>
```

Abort a phase if it does not complete.

```text
--ops-per-tick <n>
```

Activity compression control.

```text
--budget-ms <n>
```

Maximum HeapHammer execution time per server tick.

```text
--gc none|checkpoint
```

Explicit GC behavior.

```text
--dry-run
```

Resolve only.

```text
--label <text>
```

Human-readable run label.

---

## 11. Scenario Catalog

## 11.1 Chunk Churn — MVP

Command:

```text
/hh run chunks <iterations> <batch> [seed]
```

Canonical example:

```text
/hh run chunks --iterations 1000 --batch 100 --seed 481293 --dimension minecraft:overworld --mode load-unload
```

Lifecycle:

```text
select deterministic chunks
→ acquire/load/generate as configured
→ allow normal initialization/events
→ hold if configured
→ release HeapHammer ownership/tickets
→ wait for unload/cleanup
→ validate
→ checkpoint
→ repeat
```

Important options:

- `--dimension <id>`
- `--mode load-unload`
- `--mode generate-load-unload`
- `--mode ticket-churn`
- `--radius <blocks>`
- `--center <x> <z>`
- `--hold-ticks <n>`
- `--settle-ticks <n>`
- `--require-unloaded`
- `--selection random|grid|ring|walk`

Important rule:

Terrain generation is not fully reversible. Generated data remains on disk even after memory cleanup. Generation scenarios should strongly recommend a disposable world or world copy.

## 11.2 Entity Churn — Later

```text
/hh run entities <iterations> <batch> [seed]
```

Lifecycle:

```text
select entity types
→ spawn
→ initialize/tick
→ optional interaction
→ remove/kill/discard
→ unload/release relevant world state
→ validate entity counts
→ checkpoint
```

Options:

- `--include-mod`
- `--exclude-mod`
- `--coverage`
- `--lifetime-ticks`
- `--remove kill|discard`

## 11.3 Block Entity Churn — Later

```text
/hh run blockentities <iterations> <batch> [seed]
```

Lifecycle:

```text
construct valid block state
→ place
→ initialize block entity
→ optionally tick/exercise
→ remove
→ unload chunk if relevant
→ checkpoint
```

Generic support must be conservative because many modded block entities require valid neighbors, multiblocks, capabilities, networks, or custom state.

## 11.4 Item Workloads — Later

```text
/hh run items <iterations> <coverage> [seed]
```

Potential actions:

- inventory create/remove,
- item use,
- use-on-block,
- drop/pickup,
- stack create/destroy.

Not every registered item is safely testable.

Record target status:

- `TESTED`
- `PARTIALLY_TESTED`
- `UNSUPPORTED`
- `FAILED_TO_INITIALIZE`

## 11.5 Menu / GUI Lifecycle — Later

```text
/hh run menus ...
```

Potential lifecycle:

```text
construct valid backing state
→ create/open menu
→ initialize server-side state
→ supported interactions
→ close
→ destroy backing state
→ checkpoint
```

This area is likely adapter-heavy and may not reproduce client-network behavior without a real protocol client.

## 11.6 Dimension Churn — Later

```text
/hh run dimensions <iterations> [seed]
```

Potential lifecycle:

```text
enter/load dimension
→ exercise region
→ leave
→ release chunks/state
→ repeat across dimensions
```

## 11.7 Player / Session Churn — Later

```text
/hh run sessions <iterations> [seed]
```

Potential actor modes:

- direct server invocation,
- fake player,
- external protocol bot,
- real client.

Reports must state actor mode.

---

## 12. Fake Players and Client-Side Boundary

HeapHammer Core should not require a client-side mod.

A server-side fake player can exercise a large amount of server logic, including some inventory/item/block/player pathways, but it is **not equivalent to a real networked client**.

A real client or protocol bot may be required for:

- login/handshake bugs,
- real packet ordering,
- latency-sensitive behavior,
- client-driven button/key behavior,
- rendering/client-only logic,
- some menu/network synchronization paths,
- disconnect timing bugs.

Future optional components may include:

```text
HeapHammer Core
    server-only mod

HeapHammer Bot
    external Minecraft protocol client

HeapHammer Client
    optional companion only if a scenario genuinely requires client mod code
```

Do not make these optional components a dependency of the MVP.

---

## 13. Determinism and Replay

Perfect determinism is difficult in a modded concurrent server. HeapHammer should therefore distinguish between:

### Configuration determinism

Same:

- scenario,
- seed,
- config,
- modpack,
- world.

### Operation determinism

Same resolved operation sequence.

### Runtime determinism

Same exact timing/interleaving/async execution.

Runtime determinism is not guaranteed.

To maximize reproducibility, persist:

- world seed,
- workload seed,
- exact resolved registry IDs,
- exact coordinates,
- operation ordering,
- iteration boundaries,
- scenario implementation version,
- environment fingerprint,
- relevant config hashes,
- server/JVM details.

Replay should be strict by default.

Example validation:

```text
Minecraft version           MATCH
Loader/version              MATCH
Java major version          MATCH
HeapHammer plan version     MATCH
Mod IDs                     MATCH
Mod versions                MATCH
Relevant configs            MATCH
World seed                  MATCH
Registry fingerprint        MATCH
```

If mismatched:

- refuse by default,
- allow `--allow-mismatch`,
- clearly mark the run as non-equivalent.

---

## 14. Reports

Each experiment should generate a machine-readable report, preferably JSON, plus an optional human-readable summary.

### 14.1 Required fields

```text
runId
label
startTime
endTime
status
scenario
scenarioVersion
heapHammerVersion

inputSpec
canonicalCommand
resolvedOperationsRef

environment
  minecraftVersion
  loader
  loaderVersion
  javaVersion
  worldSeed
  modpackFingerprint
  modList
  configFingerprint

metrics
  baseline
  checkpoints
  final

cleanup
  expected
  observed
  validationStatus

detection
  warmupIterations
  analyzedCheckpoints
  retainedHeapSlope
  totalDelta
  classification
  confidence
  reasons

diagnostics
  histogramRefs
  heapDumpRefs
  jfrRefs

replay
  exactReplayCommand
  rerunCommand
```

### 14.2 Human-readable summary example

```text
HeapHammer Experiment
HH-20260906-17F8

Scenario
  chunk-churn

Iterations
  1000

Operations
  100,000 chunk cycles

Initial post-GC heap
  3.81 GiB

Final post-GC heap
  5.17 GiB

Delta
  +1.36 GiB

Estimated retained growth
  +1.41 MiB / iteration

World-state recovery
  PASS

Loaded chunk baseline
  327 → 330

Entity baseline
  184 → 186

Trend classification
  SUSPICIOUS

Confidence
  HIGH

Replay
  /hh replay HH-20260906-17F8
```

---

## 15. Layered Architecture

The architecture must separate Minecraft integration from experiment logic so that the domain can be tested without starting a full server wherever possible.

```text
┌────────────────────────────────────────────┐
│ Presentation / Command Layer               │
│ Brigadier commands, suggestions, messages  │
├────────────────────────────────────────────┤
│ Application Layer                          │
│ run/plan/replay/stop/report orchestration  │
├────────────────────────────────────────────┤
│ Experiment Domain Layer                    │
│ specs, plans, lifecycle, results, rules    │
├────────────────────────────────────────────┤
│ Scenario Layer                             │
│ chunk/entity/item/etc workloads            │
├────────────────────────────────────────────┤
│ Measurement & Detection Layer              │
│ metrics, baselines, slopes, classifications│
├────────────────────────────────────────────┤
│ Diagnostics Layer                          │
│ JFR, histograms, heap dumps, optional spark│
├────────────────────────────────────────────┤
│ Platform Adapter Layer                     │
│ NeoForge/Fabric/Minecraft integrations     │
├────────────────────────────────────────────┤
│ Infrastructure Layer                       │
│ JSON, files, hashes, clock, logging        │
└────────────────────────────────────────────┘
```

### 15.1 Presentation / Command Layer

Responsibilities:

- Brigadier registration.
- Positional + named flag parsing.
- Tab completion.
- Permission checks.
- User-facing output.
- Conversion to application commands.

Must not contain scenario business logic.

### 15.2 Application Layer

Responsibilities:

- Start experiment.
- Stop/pause/resume experiment.
- Plan scenario.
- Replay plan.
- Rerun spec.
- Trigger checkpoints.
- Produce/export reports.
- Enforce single-run concurrency initially.

Suggested services:

- `ExperimentService`
- `PlanService`
- `ReplayService`
- `ReportService`
- `DiagnosticService`
- `CapabilityService`

### 15.3 Experiment Domain Layer

Pure Java domain where possible.

Core types:

- `ExperimentId`
- `ExperimentSpec`
- `ExperimentPlan`
- `ResolvedOperation`
- `ExperimentState`
- `ExperimentPhase`
- `Checkpoint`
- `EnvironmentFingerprint`
- `ScenarioId`
- `ScenarioVersion`
- `DetectionResult`
- `CleanupResult`
- `ExperimentReport`

Recommended state machine:

```text
CREATED
→ PLANNED
→ WARMING_UP
→ RUNNING
→ CLEANING_UP
→ SETTLING
→ MEASURING
→ RUNNING ...
→ COMPLETED
```

Exceptional states:

```text
PAUSED
STOPPING
ABORTED
FAILED
CLEANUP_FAILED
```

### 15.4 Scenario Layer

Define a stable scenario contract.

Conceptual interface:

```java
interface Scenario {
    ScenarioId id();
    ScenarioVersion version();
    CapabilityCheck checkCapabilities(Environment env);
    ExperimentPlan plan(ExperimentSpec spec, PlanningContext ctx);
    ScenarioExecutor createExecutor(ExperimentPlan plan, ExecutionContext ctx);
}
```

Execution must be incremental/tick-budgeted, not one giant blocking call.

Conceptual executor:

```java
interface ScenarioExecutor {
    StepResult tick(ExecutionBudget budget);
    CleanupResult cleanup(ExecutionBudget budget);
    ValidationResult validate();
}
```

### 15.5 Measurement & Detection Layer

Responsibilities:

- sample metrics,
- create checkpoints,
- store baseline,
- detect cleanup recovery,
- compute trends,
- classify result.

Suggested components:

- `JvmMetricsCollector`
- `MinecraftMetricsCollector`
- `CheckpointService`
- `TrendAnalyzer`
- `CleanupValidator`
- `LeakHeuristic`

The detector should operate on immutable checkpoint data and be unit-testable.

### 15.6 Diagnostics Layer

Provide optional interfaces for:

- class histogram,
- heap dump,
- JFR,
- optional external profiler integration.

Diagnostics must be feature-detected.

Do not assume every JVM exposes every facility in the same way.

### 15.7 Platform Adapter Layer

This is the only layer that should know loader/Minecraft implementation details.

Responsibilities may include:

- server lifecycle hooks,
- command registration,
- chunk tickets/load/unload requests,
- registry access,
- entity operations,
- dimension lookup,
- tick scheduling,
- fake-player integration where supported.

Keep this layer thin.

### 15.8 Infrastructure Layer

Responsibilities:

- JSON serialization,
- plan/report persistence,
- hashing,
- file paths,
- atomic writes,
- log formatting,
- monotonic clock/time abstraction.

---

## 16. Suggested Module Structure

Do not over-modularize on day one, but keep package boundaries clean.

Potential Gradle modules later:

```text
heaphammer-core
heaphammer-neoforge
heaphammer-cli         # future
heaphammer-adapter-api # future
```

MVP may remain one mod project with packages:

```text
io.heaphammer
├── command
├── application
├── domain
├── scenario
│   └── chunks
├── metrics
├── detection
├── diagnostics
├── platform
├── report
└── infrastructure
```

---

## 17. Data Flow

Example `/hh run chunks ...`:

```text
Brigadier input
    ↓
Command parser
    ↓
ExperimentSpec
    ↓
Validation
    ↓
Scenario.plan(...)
    ↓
ExperimentPlan + resolved operations
    ↓
Persist plan
    ↓
ExperimentService.start(...)
    ↓
Tick-driven ScenarioExecutor
    ↓
CleanupValidator
    ↓
CheckpointService
    ↓
TrendAnalyzer
    ↓
ExperimentReport
```

---

## 18. Chunk Scenario Implementation Requirements

This is the MVP scenario.

### 18.1 Planning

Given:

- seed,
- center,
- radius,
- dimension,
- iterations,
- batch size,
- selection strategy,

resolve exact chunk coordinates before execution when feasible.

Persist them.

### 18.2 Execution

For each iteration:

1. acquire/request selected chunks,
2. optionally generate if mode permits,
3. allow initialization,
4. hold for configured ticks,
5. release HeapHammer-owned tickets/references,
6. wait for normal unload/cleanup,
7. validate expected chunks are no longer kept loaded by HeapHammer,
8. settle,
9. checkpoint,
10. continue.

### 18.3 Ownership tracking

HeapHammer must know exactly which resources it owns.

Never remove tickets/resources that were not created by HeapHammer.

### 18.4 Failure behavior

If chunks refuse to unload:

- do not automatically call this a memory leak,
- report which chunks remain loaded,
- report possible external tickets/owners if inspectable,
- mark cleanup incomplete,
- continue or abort according to configuration.

---

## 19. Environment Fingerprinting

Create a stable environment fingerprint from:

- Minecraft version,
- loader + version,
- Java major/full version,
- HeapHammer version,
- mod ID + version list,
- selected relevant configs or config hashes,
- world seed,
- registry fingerprint,
- scenario version.

The fingerprint should be human-inspectable and machine-comparable.

Do not depend solely on JAR filename because launchers often rename files.

---

## 20. Mod Targeting and Coverage

Scenario selection should support:

- one mod,
- multiple mods,
- include filters,
- exclude filters,
- percentage coverage,
- exact registry IDs where applicable.

Example:

```text
/hh run items --mods create,mekanism --coverage 25 --seed 847213
```

Rule:

The same environment + same seed + same coverage must resolve to the same sampled registry IDs.

Persist the actual selected IDs in the plan.

---

## 21. Future Automated Mod Isolation

A future external controller may perform dependency-aware delta debugging.

Concept:

```text
250 mods
→ choose valid dependency-closed subset
→ start server
→ replay known failing workload
→ classify PASS/FAIL/INCONCLUSIVE
→ choose next subset
→ repeat
```

Important complications:

- required library mods,
- hard dependencies,
- optional dependencies,
- config/world compatibility,
- mods that alter registry/world data,
- pairwise interactions,
- higher-order interactions,
- invalid subsets,
- startup failures unrelated to the target bug.

Do not implement naive `remove half the jars` logic inside the Minecraft mod.

This belongs in a future external orchestration tool.

Potential future CLI:

```text
heaphammer bisect start --pack ./server --plan failing.hhplan
heaphammer bisect status
heaphammer bisect resume
heaphammer bisect abort
heaphammer bisect report
```

---

## 22. External CLI — Future

The external CLI is optional and post-MVP.

Potential commands:

```text
heaphammer run <plan>
heaphammer replay <run>
heaphammer compare <run-a> <run-b>
heaphammer ci <plan>
heaphammer bisect ...
```

Potential CI exit codes:

```text
0 = pass
1 = suspicious retained-memory growth
2 = workload failure
3 = server crash
4 = environment mismatch
```

---

## 23. Testing Strategy

Testing is not an afterthought. HeapHammer itself must be trustworthy because its job is to diagnose unreliable environments.

Use four layers of tests.

---

## 23.1 Unit Tests — No Minecraft Server Required

Test pure domain logic heavily.

### Required unit tests

#### Command normalization

Verify that these produce identical `ExperimentSpec` values:

```text
/hh run chunks 1000 100 481293
/hh run chunks 1000 100 --seed 481293
/hh run chunks --iterations 1000 --batch 100 --seed 481293
```

Verify duplicate arguments are rejected.

#### Deterministic planning

Given identical:

- environment fingerprint,
- scenario version,
- seed,
- scenario options,

assert exact equality of resolved operations.

#### Different seeds

Different seeds should normally resolve to different operations.

#### Coverage sampling

For a fixed registry list + fixed seed + 25% coverage:

- same IDs every run,
- exact expected sample count rules,
- stable ordering.

#### Environment comparison

Test:

- exact match,
- mod version mismatch,
- missing mod,
- Java mismatch,
- world seed mismatch,
- allowed mismatch behavior.

#### State machine

Test all valid and invalid experiment transitions.

#### Trend detector

Create synthetic checkpoint sequences for:

1. flat retained heap → PASS,
2. warmup growth then flat → PASS/INCONCLUSIVE according to thresholds,
3. steady positive slope + recovered world state → SUSPICIOUS,
4. heap growth + entity count growth → cleanup problem, not clean leak classification,
5. noisy heap with no statistically meaningful slope → INCONCLUSIVE,
6. too few checkpoints → INCONCLUSIVE.

#### Serialization round-trip

`ExperimentSpec`, `ExperimentPlan`, and `ExperimentReport` must serialize and deserialize without semantic changes.

#### Replay versioning

Old plan schema fixtures should either:

- migrate successfully,
- or fail with a clear unsupported-schema error.

---

## 23.2 Integration Tests — Minecraft Test Environment

Run against a minimal server/mod test environment.

### Required integration tests

#### Command registration

Verify all MVP commands register and tab-complete correctly.

#### Server-only compatibility

A vanilla-compatible client that does not have HeapHammer installed must be able to connect if the rest of the modpack permits it.

#### Chunk load/unload lifecycle

On a disposable test world:

1. record baseline loaded chunks,
2. run a small deterministic chunk test,
3. verify selected chunks load,
4. release HeapHammer tickets,
5. wait for cleanup,
6. verify HeapHammer retains no references/tickets to them,
7. verify report contains the exact coordinates.

#### Stop and cleanup

Start a run, interrupt it mid-iteration, and assert:

- state enters stopping/aborted correctly,
- HeapHammer-owned tickets are removed,
- report is still written,
- run is marked interrupted.

#### Replay

Run a short scenario, then replay it.

Assert the resolved operations are identical.

#### Report persistence

Restart the server and verify previous reports/plans remain listable/readable.

---

## 23.3 Controlled Leak Fixture Tests

HeapHammer needs intentionally broken test fixtures.

Create small internal/dev-only mods or fixtures that produce known behavior.

### Fixture A — Static chunk retention

On chunk load:

```java
static final Map<ChunkPos, Object> LEAK = ...;
```

Retain an object related to the loaded chunk and never remove it.

Expected result:

- chunk visible state returns near baseline,
- retained heap grows approximately with iterations,
- HeapHammer eventually classifies `SUSPICIOUS`.

### Fixture B — Correctly cleaned cache

Add state on chunk load and remove it on unload.

Expected:

- no sustained post-warmup slope,
- `PASS`.

### Fixture C — Bounded warming cache

Grow to a fixed size, then stop.

Expected:

- early growth,
- later plateau,
- not `SUSPICIOUS` after sufficient warmup/analysis window.

### Fixture D — Entity cleanup failure

Create entities that are intentionally left alive.

Expected:

- heap grows,
- entity count also grows,
- classify cleanup/world-state failure rather than a clean hidden-retention signal.

### Fixture E — Async delayed cleanup

Delay release for several seconds/ticks.

Expected:

- short settle period may be inconclusive,
- longer settle period should recover.

This validates that HeapHammer is not simply measuring too early.

---

## 23.4 Real Modpack Validation

After synthetic fixtures pass, test against real packs.

### Validation protocol

For each candidate modpack:

1. clone/copy the server to a disposable staging environment,
2. use the same Java version and JVM flags as production where practical,
3. use the same mod/config set,
4. copy or intentionally regenerate the world depending on the test,
5. run `/hh doctor`,
6. capture baseline,
7. run a short warmup,
8. run deterministic chunk churn,
9. repeat the same test at least 3 times,
10. compare reports,
11. run with a different seed,
12. if suspicious, capture histogram/JFR/heap dump at selected checkpoints,
13. replay the exact resolved workload.

### Success criteria

The test is valuable if it can produce one or more of:

- repeatable growth correlated with a workload,
- repeatable no-growth baseline,
- a specific seed/operation sequence that fails more often,
- a reliable cleanup failure,
- a reduced time-to-reproduction compared with passive staging.

---

## 24. Performance Testing HeapHammer Itself

HeapHammer must not become the bottleneck it is trying to measure.

Test:

- command parsing cost,
- planner memory usage for large plans,
- report file size,
- tick execution budget compliance,
- metrics collection overhead,
- effect of checkpoint frequency,
- effect of large batch sizes.

### Required safeguards

At runtime track HeapHammer's own execution time per tick where practical.

If its configured budget is exceeded repeatedly:

- throttle workload,
- warn,
- optionally pause/abort.

---

## 25. Manual Test Checklist for Every Release

Before release:

1. Start a clean supported server.
2. Confirm HeapHammer loads server-side.
3. Connect a client without HeapHammer installed.
4. Run `/hh version`.
5. Run `/hh capabilities`.
6. Run `/hh doctor`.
7. Run `/hh plan chunks 5 2 1234`.
8. Inspect the resolved plan.
9. Run `/hh run chunks 5 2 1234`.
10. Verify progress/status.
11. Verify cleanup.
12. Verify report creation.
13. Run `/hh replay last`.
14. Confirm exact operations match.
15. Run `/hh rerun last`.
16. Confirm current generator resolves equivalently when expected.
17. Trigger `/hh stop` during a longer run.
18. Confirm graceful cleanup.
19. Trigger `/hh stop --now` during a longer run.
20. Confirm best-effort cleanup and interrupted report.
21. Verify a controlled leak fixture becomes suspicious.
22. Verify a bounded cache fixture does not.
23. Verify an environment mismatch blocks replay.
24. Verify `--allow-mismatch` records non-equivalent status.

---

## 26. MVP Scope

HeapHammer 0.1 should include only:

### Commands

```text
/hh help
/hh version
/hh capabilities
/hh doctor
/hh status
/hh stop
/hh cleanup
/hh metrics
/hh checkpoint
/hh scenario list
/hh scenario describe chunks
/hh plan chunks ...
/hh run chunks ...
/hh replay <run-id|last>
/hh rerun <run-id|last>
/hh report list
/hh report show <run-id|last>
/hh report export <run-id|last>
/hh inspect mods
```

### Features

- one loader,
- one supported Minecraft version family,
- server-side only,
- deterministic chunk churn,
- persisted plans,
- replay,
- cleanup validation,
- lightweight metrics,
- warmup support,
- simple retained-heap trend heuristic,
- JSON reports,
- canonical commands,
- test-world safety checks.

### Explicit non-goals for 0.1

- no item fuzzing,
- no menu fuzzing,
- no player bot,
- no client mod,
- no mod bisection,
- no AI,
- no automatic blame attribution,
- no built-in heap analyzer,
- no multi-loader support.

---

## 27. Roadmap

### Phase 0 — Feasibility spike

Goal: prove the core lifecycle is possible and measurable.

Deliver:

- register `/hh`,
- load/release deterministic chunks,
- collect heap + chunk metrics,
- write one JSON report.

### Phase 1 — MVP

Deliver:

- complete chunk scenario,
- planning,
- replay,
- cleanup validation,
- baseline/checkpoints,
- detector,
- reports,
- doctor/status/stop,
- synthetic leak fixture.

### Phase 2 — Better diagnostics

Add:

- class histogram support,
- JFR integration,
- heap dump trigger,
- report comparison,
- richer slope/confidence analysis.

### Phase 3 — Entity and block-entity scenarios

Add generic conservative lifecycle testing.

### Phase 4 — Registry sampling / mod targeting

Add:

- coverage percentages,
- include/exclude mods,
- exact registry filters,
- persisted sampled target sets.

### Phase 5 — Adapter API

Allow mod-specific workload plugins.

### Phase 6 — Player/session/client fidelity

Explore:

- fake players,
- external protocol bot,
- optional client companion only if justified.

### Phase 7 — External orchestration

Add:

- CLI,
- CI mode,
- server startup/restart control,
- batch experiment orchestration.

### Phase 8 — Dependency-aware mod isolation

Add delta debugging / bisection outside the server mod.

---

## 28. Engineering Risks

### Risk: Workload compression may not reproduce the production leak

Some failures depend on:

- wall-clock time,
- asynchronous races,
- real client/network timing,
- rare state combinations,
- external integrations,
- player behavior sequence,
- specific world history.

Mitigation:

- support multiple scenario families,
- persist exact operations,
- support configurable pacing,
- eventually add real protocol clients/adapters,
- treat non-reproduction as a result, not proof of absence.

### Risk: Heap slope is noisy

Mitigation:

- warmup,
- multiple checkpoints,
- cleanup validation,
- optional explicit GC in staging,
- repeated runs,
- class/JFR/heap diagnostics.

### Risk: HeapHammer itself retains objects

This would invalidate the product.

Mitigation:

- strict ownership model,
- weak/no long-term references to world objects,
- IDs/coordinates instead of object references in persisted state,
- integration tests specifically checking HeapHammer cleanup,
- heap analysis of HeapHammer itself.

### Risk: Generic item/menu testing is unsafe or meaningless

Mitigation:

- conservative support classification,
- adapters,
- no blind invocation.

### Risk: Loader APIs change

Mitigation:

- thin platform layer,
- one loader/version first,
- pin exact supported versions,
- verify implementation details against current official documentation during development.

---

## 29. AI Coding Agent Instructions

The coding agent implementing HeapHammer must follow these rules.

### 29.1 Before coding

1. Read this entire specification.
2. Confirm the exact target Minecraft version and loader version.
3. Consult the **current official loader/Minecraft documentation and source** for chunk lifecycle, commands, server ticks, registry access, and diagnostics integration.
4. Do not invent APIs from memory.
5. Prefer official APIs over mixins/invasive hooks when possible.
6. If a required operation is not safely available through public APIs, document the limitation before introducing invasive implementation techniques.

### 29.2 Implementation priorities

Implement in this order:

1. project boots,
2. server-only compatibility,
3. command registration,
4. domain model,
5. JSON persistence,
6. deterministic planner,
7. chunk execution lifecycle,
8. ownership cleanup,
9. metrics,
10. checkpoints,
11. replay,
12. detection heuristic,
13. reports,
14. controlled leak fixtures,
15. polish.

### 29.3 Code quality rules

- Prefer small explicit classes over magical frameworks.
- Keep Minecraft objects out of long-lived domain state whenever possible.
- Persist IDs/coordinates, not live object references.
- Avoid static mutable collections containing world objects.
- All experiment resources need explicit ownership and cleanup.
- Long-running work must be tick-budgeted.
- Do not block the server thread with file writes or expensive diagnostics when avoidable.
- Use atomic report/plan writes.
- Log run ID with every experiment-related message.
- Make failures actionable and explicit.
- Never silently fall back to a different workload behavior.

### 29.4 Testing rules

No feature is complete without tests appropriate to its layer.

For each scenario feature:

1. deterministic planning test,
2. serialization test,
3. happy-path integration test,
4. cleanup test,
5. interruption test,
6. replay test,
7. at least one intentionally broken fixture if relevant.

### 29.5 Do not overbuild

The agent must not implement future roadmap items unless explicitly requested.

In particular, do not add:

- AI analysis,
- fake players,
- client mod,
- external CLI,
- mod bisection,
- multi-loader architecture,

before the chunk MVP is proven.

---

## 30. Definition of Done for HeapHammer 0.1

HeapHammer 0.1 is done when all of the following are true:

1. The mod runs server-side without requiring a client installation.
2. `/hh doctor` verifies the environment and gives useful warnings.
3. `/hh plan chunks` generates deterministic resolved chunk operations.
4. Same seed/spec/environment produces the same resolved plan.
5. `/hh run chunks` executes that plan incrementally without violating configured tick budgets.
6. HeapHammer tracks and releases only its own chunk ownership/tickets.
7. Cleanup state is validated.
8. Checkpoints record heap + relevant world metrics.
9. Warmup iterations can be excluded.
10. A simple trend detector classifies synthetic flat, bounded, and leaking fixtures correctly.
11. Every run writes a report even when interrupted whenever technically possible.
12. `/hh replay` executes the exact persisted operation sequence.
13. `/hh rerun` rebuilds from the original configuration + seed.
14. Environment mismatches are detected.
15. A controlled static-retention fixture becomes `SUSPICIOUS`.
16. A correctly cleaned fixture does not become `SUSPICIOUS`.
17. A bounded warm cache does not become a false leak after sufficient warmup.
18. Replaying the controlled leak reproduces the signal repeatedly.
19. The canonical command is printed in the report.
20. No HeapHammer-owned world references remain after completed cleanup.

---

## 31. Product Hypothesis Validation

Before investing heavily in later features, validate the product hypothesis with real experiments.

### Hypothesis

> Deterministic lifecycle workload compression can reduce the time required to reproduce at least a meaningful subset of real modded-server memory/resource-retention bugs.

### Experiments

#### Experiment 1 — Synthetic known leak

Can HeapHammer detect a deliberately retained chunk-related object?

Expected: yes.

#### Experiment 2 — Bounded cache

Can HeapHammer avoid falsely calling normal cache warming a leak?

Expected: yes.

#### Experiment 3 — Known real modpack problem

Take one real pack with a known long-running memory issue.

Try multiple chunk workload seeds and pacing profiles.

Success:

- reproduce the trend significantly faster than passive staging,
- and reproduce it again from the same plan.

#### Experiment 4 — Negative real pack

Run against a known-stable pack.

Expected:

- no persistent suspicious retained growth after warmup.

#### Experiment 5 — Replay across restart

Generate a suspicious run, fully restart the server, replay the exact plan.

Success:

- behavior reproduces consistently enough to be useful.

### Go / no-go checkpoint

Continue building broader scenarios if chunk MVP can demonstrate at least one real-world case where HeapHammer:

1. reduces time-to-reproduction,
2. creates a repeatable test case,
3. provides more actionable evidence than passive profiling alone.

If it cannot do that, reassess the product before adding complexity.

---

## 32. Final Product Principle

HeapHammer should remain easy to explain:

> Give it a deterministic workload.
> Hammer the same lifecycle repeatedly.
> Clean up.
> Measure what stays behind.
> Save exactly what happened.
> Replay it.

If HeapHammer can turn:

```text
"the server usually dies after two days"
```

into:

```text
"this exact 17-minute workload reproduces retained growth 5/5 times"
```

then the tool has solved its core problem.

---

## 33. Recommended Initial Tagline

**HeapHammer — reproducible stress testing for modded Minecraft.**

Alternative:

**HeapHammer — make memory leaks reproducible.**
