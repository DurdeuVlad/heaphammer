<p align="center">
  <img src="assets/heaphammer_banner.png" alt="HeapHammer Banner" width="100%">
</p>

<div align="center">

<img src="assets/heaphammer_logo.png" alt="HeapHammer Logo" width="120">

# HeapHammer

**Deterministic Minecraft server stress testing and retained-memory regression detection.**

[![Release](https://img.shields.io/badge/release-v1.0.0-orange.svg?style=flat-square)](https://github.com/DurdeuVlad/heaphammer/releases)
[![Minecraft](https://img.shields.io/badge/minecraft-1.12.2_--_1.21.1-brightgreen.svg?style=flat-square)](docs/MULTI_VERSION_ARCHITECTURE.md)
[![Loaders](https://img.shields.io/badge/loaders-Fabric_%7C_Forge-blue.svg?style=flat-square)](docs/MULTI_VERSION_ARCHITECTURE.md)
[![License](https://img.shields.io/badge/license-LGPL--3.0-blueviolet.svg?style=flat-square)](LICENSE)
[![Side](https://img.shields.io/badge/side-server--only-informational.svg?style=flat-square)](#quickstart)

<p align="center">
  <a href="#why-heaphammer"><b>Why HeapHammer?</b></a> •
  <a href="#simulated-workloads"><b>Workloads</b></a> •
  <a href="#does-it-work-with-any-mod"><b>Mod Support</b></a> •
  <a href="#how-it-works"><b>How It Works</b></a> •
  <a href="#built-for-staging-safe-on-production"><b>Safety</b></a> •
  <a href="#quickstart"><b>Quickstart</b></a> •
  <a href="#empirical-proof"><b>Proof</b></a> •
  <a href="#commands"><b>Commands</b></a> •
  <a href="docs/README.md"><b>Docs</b></a>
</p>

</div>

---

## Why HeapHammer?

In modpacks with 100+ mods, memory leaks rarely show up on idle servers. They take **24 to 48 hours of chaotic player traffic**—players exploring terrain on Elytras, automated mob farms running, machines cycling—before the JVM runs out of heap and crashes:

```text
[Server thread/ERROR] java.lang.OutOfMemoryError: Java heap space
```

Traditional profilers (like Spark or JFR) show what is occupying memory **right now**, but they cannot tell you **which workload caused it** or **whether the memory will ever be reclaimed**.

**HeapHammer compresses 24 hours of player activity into a 2-minute repeatable test.**  
It injects native Minecraft chunk tickets, entity spawns, and block entity cycles under a strict tick budget, forces cleanup, and uses statistical regression to prove whether memory resets or keeps climbing.

> [!NOTE]
> **Profilers (Spark, JFR)**: *"What object types are in the heap right now?"* (Instantaneous state)  
> **HeapHammer**: *"What exact workload breaks this server, which mod retains the references, and can I replay it?"* (Behavioral regression detector)

---

## Simulated Workloads

Rather than running generic stress loops, HeapHammer exercises real Minecraft mechanics that trigger real-world mod collisions:

### 1. 🗺️ Elytra Flight & World Churn (`/hh run chunks`)
- **The Reality**: A player flies at 40 blocks/second on an Elytra, loading 300+ chunks in a minute.
- **What HeapHammer Does**: Acquires native `TicketType<ChunkPos>` tickets across configurable geometric patterns (`SPIRAL`, `RING`, `RANDOM_WALK`, `GRID_SWEEP`), holds them to let world-gen and tile entities initialize, and cleanly releases them.
- **What It Catches**: Minimap mods (Dynmap, JourneyMap), land claim mods (FTB Chunks), and chunk-tracking listeners that retain references to `LevelChunk` or `ServerLevel` after chunks unload.

### 2. 👾 Mob Farm Swarms & Despawn Waves (`/hh run entities`)
- **The Reality**: Mob farms spawn dozens of mobs every minute; players sweep them with looting swords or let them despawn.
- **What HeapHammer Does**: Spawns deterministic batches of entities, exercises their navigation and AI goal selectors for a configured lifetime, and executes clean removal (`Entity.discard()`).
- **What It Catches**: Damage indicators, combat loggers, and entity tracking mods that hook `ServerEntityEvents.ENTITY_LOAD` but fail to clean up dead entity UUIDs or live `Entity` instances.

### 3. ⚙️ Industrial Automation & Block Entity Churn (`/hh run blockentities`)
- **The Reality**: Tech mods place conduits, storage drawers, quarries, and processing machines that are constantly placed, rotated, and broken.
- **What HeapHammer Does**: Places test grids of block entities, triggers tick updates and inventory access, then removes the blocks.
- **What It Catches**: Machines whose tile entities fail to unregister from the world tick queue on `BlockEntity.setRemoved()`, leaving container inventories and redstone listeners pinned in memory.

### 4. 🔌 Cross-Mod Collision Isolation (`/hh adapters`)
- **The Reality**: Mod A works fine alone. Mod B works fine alone. Installed together, Mod A registers a listener onto Mod B's custom event bus on every dimension change and never unsubscribes.
- **What HeapHammer Does**: Executes dedicated workload adapters registered via entrypoints and compares differential retention slopes (`/hh report diff`).

---

## Does It Work With Any Mod?

**Yes. HeapHammer works automatically out of the box with any mod**—no mod-specific plugins, custom configs, or patches required.

Because HeapHammer stresses the **native Minecraft server engine** and queries the **JVM runtime directly**, any mod running on your server is automatically included in tests:

| Mod Category | Examples | Automatic Behavior | What HeapHammer Catches |
|---|---|---|---|
| 🗺️ **World-Gen & Biomes** | Terralith, BYG, Biomes O' Plenty | **100% Automatic** | Chunk loading triggers native feature generation, population, and lighting passes. Catches listeners that leak chunk data. |
| 📍 **Maps & Claims** | Dynmap, JourneyMap, FTB Chunks | **100% Automatic** | Exercises whether map rendering and claiming listeners cleanly evict terrain cache data when chunks unload. |
| 👾 **Custom Mobs & Bosses** | Alex's Mobs, Lycanites, Cataclysm | **100% Automatic** | Spawns, ticks, and discards registered entity types, verifying that entity tracking and combat listeners don't pin dead mobs in static lists. |
| ⚙️ **Machines & Tech** | Create, Mekanism, Applied Energistics 2 | **100% Automatic** | Placing and breaking blocks tests tile entity tick queue deregistration (`BlockEntity.setRemoved()`) and inventory buffer cleanup. |
| 📦 **Full Modpacks** | ATM, Better MC, Custom Packs (200+ mods) | **100% Automatic** | `/hh report diff` isolates which mod update introduced a regression by comparing memory slopes before and after adding a mod. |

> [!TIP]
> **What about the `/hh adapters` command?**  
> For 99% of mods, zero adapters are needed. The **Workload Adapter SPI** is an *optional* extension point for mod authors who want to write specialized stress scenarios for proprietary, non-standard systems (such as off-thread simulations or custom dimension networks).

---

## How It Works

```text
1. PLAN (Seed)          2. EXECUTE (Tick Budget)    3. SETTLE (Eviction)      4. VERDICT (Slope)
Deterministic seed  ──> Max 10 ops/tick         ──> Drop tickets, wait    ──> Measure post-settle
guarantees replay       TPS stays smooth            for native chunk unload   Ordinary Least Squares
```

1. **Deterministic Planning**: Every workload is generated from a fixed seed. When a leak is discovered, the exact coordinate sequence can be replayed across server restarts.
2. **Strict Tick Budgeting**: Operations run incrementally during server tick ends (`maxOperationsPerTick=10`, `maxMsPerTick=15`). The server thread is never starved, and TPS remains smooth.
3. **Native Eviction & Settle**: After each batch, HeapHammer drops all tickets and allows vanilla `ServerChunkCache` to evict chunks naturally over configurable settle ticks.
4. **Statistical OLS Regression vs. GC Noise**: Rather than guessing from volatile instantaneous heap spikes ($\Delta\text{Heap}$), HeapHammer measures the slope ($y = mx + b$) and goodness of fit ($R^2$) across post-settle checkpoints.

---

## Built for Staging. Safe on Production.

HeapHammer is **designed primarily for staging and development servers** to validate modpacks before publishing updates. However, it is built with strict **zero-destruction safety invariants** so server admins can run diagnostics on live worlds:

- 🛡️ **Zero Chunk Corruption**: Only uses dedicated test tickets (`TicketType heaphammer`). Player chunks, world spawn, and player builds are never touched or modified.
- ⏱️ **Watchdog Protection**: Operations are tick-budgeted (max 15 ms/tick). It will never trigger a server watchdog crash or TPS freeze.
- 🧹 **Instant Clean Abort**: Running `/hh stop` or `/hh cleanup` immediately frees 100% of test tickets and entities. No leftover tickets, no server restart needed.

---

## Quickstart

HeapHammer is **100% server-side only**. Connecting players do **not** need it installed.

### 1. Install
Download the compiled JAR from [Releases](https://github.com/DurdeuVlad/heaphammer/releases) and place it into your server's `mods/` directory:
```bash
cp heaphammer-1.0.0.jar /path/to/server/mods/
```

### 2. Run Stress Test
Run from server console (or in-game with OP Level 2):
```text
# 1. Verify server health and chunk status
/hh doctor

# 2. Run a 5-cycle spiral chunk test (10 chunks/batch, radius 8)
/hh run chunks --iterations=5 --batch=10 --radius=8 --strategy=spiral --explicit-gc=true

# 3. View the verdict and slope
/hh report show last
```

### 3. Read the Output
HeapHammer prints a clear, statistical diagnostic report:

```text
=== HeapHammer Report: run-2026-09-07-120401 ===
Status: COMPLETED | Verdict: SUSPICIOUS
Duration: 45s | Checkpoints: 10
Slope: +10.56 MB/cycle (R² = 0.99)
Net Delta: +52.80 MB
Rationale: Retained memory slope indicates linear accumulation across cycles
Canonical Replay: /hh run chunks --seed=42 --center=0,0 --radius=8 --iterations=5 --batch=10 --strategy=spiral --explicit-gc=true

--- JVM Class Histogram (Top Retained Roots) ---
#1 net.minecraft.world.level.chunk.LevelChunk: 84 instances (+18.48 MB)
#2 net.minecraft.world.level.block.entity.BlockEntity: 340 instances (+4.12 MB)
#3 com.example.leakingmod.StaticChunkCache: 84 entries (+2.10 MB)
```

### 4. Verdicts Explained
- **`PASS`**: Retained memory returned cleanly to baseline ($\text{slope} \le 1.0\text{ MB/cycle}$).
- **`PASS (PLATEAU)`**: Initial cache warming that leveled off safely across subsequent cycles.
- **`SUSPICIOUS`**: Memory steadily accumulated every cycle ($\text{slope} > 2.0\text{ MB/cycle}$, $R^2 > 0.90$). An active leak exists!
- **`FAIL`**: Leftover chunk tickets or test entities were detected after cleanup.

---

## Modpack Triage Workflow

When your modpack has a memory leak, use HeapHammer to pinpoint the culprit mod:

```text
[Step 1: Baseline Run]          [Step 2: Add Suspect Mod]       [Step 3: Compare Diff]
/hh run chunks --iterations=5 ─> Add/update suspect mod     ─> /hh report diff <runA> <runB>
(Saved: run-01, Verdict: PASS)   /hh rerun run-01              Shows exact slope delta
                                 (Saved: run-02)               and culprit class!
```

```text
=== HeapHammer Report Diff ===
Run A: run-2026-09-07-100000 (Vanilla + Core Mods)
Run B: run-2026-09-07-103000 (+ Suspect Mod Added)
Net Delta:      +1.20 MB vs +54.00 MB (Diff: +52.80 MB)
Retained Slope: +0.24 MB/cyc vs +10.56 MB/cyc (Diff: +10.32 MB/cyc)
Classification: PASS -> SUSPICIOUS (CHANGED)
```

---

## Empirical Proof

HeapHammer is not theoretical. Every algorithm, regression slope, and ticket lifecycle has been empirically benchmarked and proven on **real Minecraft 1.21.1 Fabric dedicated servers** using standalone companion test mods:

### 1. Dedicated Server Benchmark Matrix (5 Cycles, Explicit GC)
| Test Condition | Retained Slope | Net Delta | Verdict | Real-World Outcome |
|---|---|---|---|---|
| **Clean Vanilla Control** | **+0.75 MB/cyc** | +3.00 MB | **`PASS`** | Zero false positives on healthy servers. |
| **Static Chunk Cache Leak** | **+10.56 MB/cyc** | +42.24 MB | **`SUSPICIOUS`** | Caught 100% of pinned `LevelChunk` instances ($R^2 = 0.9999$). |
| **Entity Tracker Leak** | **+6.41 MB/cyc** | +41.02 MB | **`SUSPICIOUS`** | Caught unevicted despawned entity references. |
| **Cross-Mod Collision (A+B)** | **+10.52 MB/cyc** | **+42.08 MB** | **`SUSPICIOUS`** | **Caught circular subscriber leak** that only manifests when both mods co-exist! |

### 2. Active Acceleration vs. Passive Waiting
- **Passive Idle Server (15 seconds)**: 0 chunks loaded $\rightarrow$ `0.00 MB/cycle` (Leak remains **dormant & invisible**).
- **Active HeapHammer (18 seconds)**: 35 chunks churned $\rightarrow$ **`+10.60 MB/cycle` (`+37.89 MB`)** $\rightarrow$ **`SUSPICIOUS` (Caught immediately!)**.

### 3. Automated Test Suite
- **86 unit and integration tests** pass continuously in CI (`./gradlew test`).
- Covers domain isolation (zero-Minecraft imports), OLS linear regression math, tick budget throttling, configurable safety ceilings, runtime circuit breaker, crash recovery journaling, and path traversal defense-in-depth.

*See [docs/CASE_STUDIES.md](docs/CASE_STUDIES.md) for full server logs, class histograms, and raw JSON benchmark reports.*

---

<a id="commands"></a>
## Commands & Permissions

All commands are **operator gated (OP Level 2+)** and **permission gated**. Non-OP players without permissions cannot execute or tab-complete `/hh` commands.

### Permission Nodes (Fabric Permissions API / LuckPerms)
HeapHammer automatically integrates with Fabric Permissions API and LuckPerms if present, seamlessly falling back to vanilla OP Level 2:

| Permission Node | Description | Default Access |
|---|---|---|
| `heaphammer.admin` | Wildcard granting full access to all HeapHammer commands. | OP Level 2 |
| `heaphammer.use` | Root command access (`/hh`, `/hh version`, `/hh help`). | OP Level 2 |
| `heaphammer.run` | Execute workloads (`/hh run ...`, `/hh stop`, `/hh cleanup`). | OP Level 2 |
| `heaphammer.config` | View and reload runtime safety configuration (`/hh config ...`). | OP Level 2 |
| `heaphammer.diagnostics` | Capture class histograms and `.hprof` heap dumps. | OP Level 2 |
| `heaphammer.report` | View, export, and diff test reports (`/hh report ...`). | OP Level 2 |
| `heaphammer.doctor` | View server health and JVM metrics (`/hh doctor`, `/hh metrics`). | OP Level 2 |
| `heaphammer.plan` | Compute deterministic workload plans (`/hh plan ...`). | OP Level 2 |

### Command Reference
Execute via `/hh` in-game or `hh` directly from the dedicated server console:

| Command | Description | Example |
|---|---|---|
| `hh doctor` | Checks server readiness, loaded chunks, and ticket health. | `hh doctor` |
| `hh run chunks [flags]` | Executes deterministic chunk churn workload. | `hh run chunks --iterations=5 --batch=10 --radius=8 --strategy=spiral` |
| `hh run entities [flags]` | Executes entity lifecycle stress workload. | `hh run entities --iterations=5 --batch=50 --hold=20` |
| `hh run blockentities [flags]` | Executes block entity placement and destruction stress. | `hh run blockentities --iterations=5 --batch=20` |
| `hh status` | Displays active test progress, current cycle, and tickets. | `hh status` |
| `hh stop` | Immediately halts test and releases all tickets. | `hh stop` |
| `hh cleanup` | Forcibly purges all active HeapHammer tickets and entities across all dimensions. | `hh cleanup` |
| `hh config show` | Displays active safety limits, circuit breaker, and crash recovery settings. | `hh config show` |
| `hh config reload` | Hot-reloads safety configuration from `config/heaphammer.json`. | `hh config reload` |
| `hh report show <id\|last>` | Displays memory retention slope, $R^2$, and verdict. | `hh report show last` |
| `hh report diff <runA> <runB>` | Compares two runs to detect regressions between modpack updates. | `hh report diff run-01 run-02` |
| `hh replay <run-id>` | Replays the exact resolved operation sequence. | `hh replay run-01` |
| `hh diagnostics histogram` | Samples top 10 JVM class instances and memory size. | `hh diagnostics histogram` |
| `hh diagnostics heapdump` | Dumps a standard HotSpot `.hprof` snapshot for MAT/JProfiler. | `hh diagnostics heapdump` |

---

## Production Safety & Crash Resilience

HeapHammer is specifically engineered for safe execution on live staging and production servers:

1. **Configurable Safety Ceilings (`config/heaphammer.json`)**:
   - Out-of-bounds parameters passed by overzealous operators (e.g., `--radius=1000 --batch=50000`) are actively validated and rejected with clear instructions on how to adjust limits safely in `config/heaphammer.json` (`maxRadius: 32`, `maxBatchSize: 128`, `maxIterations: 50`, `maxOperationsPerTick: 50`, `maxMillisPerTick: 35`).
   - Admins running stress-testing staging hardware can freely elevate these limits in `config/heaphammer.json`.
2. **Runtime Memory Circuit Breaker**:
   - Actively evaluates JVM available heap memory on every server tick.
   - If available heap drops below `minFreeMemoryMb` (default `64 MB`), the circuit breaker trips, immediately aborting the test and releasing all tickets and entities before an `OutOfMemoryError` or server watchdog crash can occur.
3. **Automated Server Crash Recovery**:
   - If the server halts unexpectedly during testing (e.g. power loss or external mod crash), all spawned test entities are persistent-tagged with `heaphammer:test`, and placed blocks/entities are tracked in `heaphammer/active_run_journal.json`.
   - On the next server startup (`SERVER_STARTED`), HeapHammer automatically detects the interrupted run, purges all leftover test entities across all worlds, reverts placed test blocks to air, releases chunk tickets, and cleans the journal.
4. **Path Traversal Defense-in-Depth**:
   - Strict alphanumeric whitelist validation (`^[a-zA-Z0-9_-]{1,64}$`) on `ExperimentId` completely neutralizes directory traversal (`../`) vulnerabilities in report loading and replay pipelines.

### Key Command Flags
- `--iterations=<int>`: Number of test cycles (default: `5`).
- `--batch=<int>`: Chunks or entities exercised per batch (default: `10`).
- `--radius=<int>`: Chunk radius around center coordinate (default: `8`).
- `--strategy=<name>`: `SPIRAL`, `RING`, `RANDOM_WALK`, `HOTSPOT_CHURN`, `GRID_SWEEP`.
- `--hold=<ticks>`: Ticks to keep chunks or entities loaded (default: `5`).
- `--settle=<ticks>`: Ticks to wait for chunk eviction after releasing tickets (default: `10`).
- `--explicit-gc=<bool>`: Run JVM garbage collection at cycle checkpoints (default: `true`).
- `--seed=<long>`: Custom seed for repeatable sequence generation.

---

## Multi-Version Architecture

HeapHammer uses **Hexagonal Architecture (Ports & Adapters)**. The core domain, math engine, and scenario planning are pure Java with **zero Minecraft dependencies**, guaranteeing binary compatibility across all supported versions:

- **1.21.1** (Fabric, Java 21) — Active production trunk (`master`)
- **1.20.1** (Fabric & Forge, Java 17) — Modern LTS Gold Standard (`ver/1.20.1`)
- **1.18.2** (Fabric & Forge, Java 17) — World-Gen Overhaul LTS (`ver/1.18.2`)
- **1.16.5** (Forge & Fabric, Java 17/8) — Nether Legacy LTS (`ver/1.16.5`)
- **1.12.2** (MinecraftForge, Java 8) — Classic Titan LTS (`ver/1.12.2-forge`)

*See [docs/MULTI_VERSION_ARCHITECTURE.md](docs/MULTI_VERSION_ARCHITECTURE.md) for version-specific port implementations and adapter details.*

---

## Documentation & Building

- **Build Mod**: `./gradlew test build buildTestmods` (86 tests pass)
- **Documentation Hub**: [docs/README.md](docs/README.md)
- **Case Studies & Benchmarks**: [docs/CASE_STUDIES.md](docs/CASE_STUDIES.md)
- **Architecture & Version Ports**: [docs/MULTI_VERSION_ARCHITECTURE.md](docs/MULTI_VERSION_ARCHITECTURE.md)
- **Release Branching Lifecycle**: [docs/PUBLICATION.md](docs/PUBLICATION.md)
- **Architecture Decisions**: [docs/DECISION.md](docs/DECISION.md)
- **Contributing**: [CONTRIBUTING.md](CONTRIBUTING.md)

---

## License

Licensed under the **[LGPL-3.0 License](LICENSE)**.
