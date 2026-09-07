<p align="center">
  <img src="assets/heaphammer_banner.png" alt="HeapHammer Banner" width="100%">
</p>

<div align="center">

<img src="assets/heaphammer_logo.png" alt="HeapHammer Logo" width="120">

# HeapHammer

**Deterministic Minecraft server stress testing and retained-memory regression detection.**

[![Release](https://img.shields.io/badge/release-v1.0.0--alpha.1-orange.svg?style=flat-square)](https://github.com/DurdeuVlad/heaphammer/releases)
[![Minecraft](https://img.shields.io/badge/minecraft-1.12.2_--_1.21.1-brightgreen.svg?style=flat-square)](docs/MULTI_VERSION_ARCHITECTURE.md)
[![Loaders](https://img.shields.io/badge/loaders-Fabric_%7C_Forge-blue.svg?style=flat-square)](docs/MULTI_VERSION_ARCHITECTURE.md)
[![License](https://img.shields.io/badge/license-LGPL--3.0-blueviolet.svg?style=flat-square)](LICENSE)
[![Side](https://img.shields.io/badge/side-server--only-informational.svg?style=flat-square)](#quickstart)

<p align="center">
  <a href="#why-heaphammer"><b>Why HeapHammer?</b></a> •
  <a href="#simulated-workloads"><b>Workloads</b></a> •
  <a href="#how-it-works"><b>How It Works</b></a> •
  <a href="#built-for-staging-safe-on-production"><b>Safety</b></a> •
  <a href="#quickstart"><b>Quickstart</b></a> •
  <a href="#modpack-triage-workflow"><b>Triage</b></a> •
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
cp heaphammer-1.0.0-alpha.1.jar /path/to/server/mods/
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

## Commands

All commands can be executed via `/hh` in-game (Level 2 OP) or `hh` from the server console:

| Command | Description | Example |
|---|---|---|
| `hh doctor` | Checks server readiness, loaded chunks, and ticket health. | `hh doctor` |
| `hh run chunks [flags]` | Executes deterministic chunk churn workload. | `hh run chunks --iterations=5 --batch=10 --radius=8 --strategy=spiral` |
| `hh run entities [flags]` | Executes entity lifecycle stress workload. | `hh run entities --iterations=5 --batch=50 --hold=20` |
| `hh run blockentities [flags]` | Executes block entity placement and destruction stress. | `hh run blockentities --iterations=5 --batch=20` |
| `hh status` | Displays active test progress, current cycle, and tickets. | `hh status` |
| `hh stop` | Immediately halts test and releases all tickets. | `hh stop` |
| `hh cleanup` | Forcibly purges all active HeapHammer tickets and entities. | `hh cleanup` |
| `hh report show <id\|last>` | Displays memory retention slope, $R^2$, and verdict. | `hh report show last` |
| `hh report diff <runA> <runB>` | Compares two runs to detect regressions between modpack updates. | `hh report diff run-01 run-02` |
| `hh replay <run-id>` | Replays the exact resolved operation sequence. | `hh replay run-01` |
| `hh diagnostics histogram` | Samples top 10 JVM class instances and memory size. | `hh diagnostics histogram` |
| `hh diagnostics heapdump` | Dumps a standard HotSpot `.hprof` snapshot for MAT/JProfiler. | `hh diagnostics heapdump` |

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

- **1.21.1** (Fabric, Java 21) — Active development trunk (`master`)
- **1.20.1** (Fabric & Forge, Java 17) — Modern LTS
- **1.18.2** (Fabric & Forge, Java 17) — World-Gen LTS
- **1.16.5** (Forge & Fabric, Java 17/8) — Legacy LTS
- **1.12.2** (MinecraftForge, Java 8) — Classic Titan

*See [docs/MULTI_VERSION_ARCHITECTURE.md](docs/MULTI_VERSION_ARCHITECTURE.md) for version-specific port implementations and adapter details.*

---

## Documentation & Building

- **Build Mod**: `./gradlew test build buildTestmods` (34 tests pass)
- **Documentation Hub**: [docs/README.md](docs/README.md)
- **Case Studies & Benchmarks**: [docs/CASE_STUDIES.md](docs/CASE_STUDIES.md)
- **Architecture & Version Ports**: [docs/MULTI_VERSION_ARCHITECTURE.md](docs/MULTI_VERSION_ARCHITECTURE.md)
- **Release Branching Lifecycle**: [docs/PUBLICATION.md](docs/PUBLICATION.md)
- **Architecture Decisions**: [docs/DECISION.md](docs/DECISION.md)
- **Contributing**: [CONTRIBUTING.md](CONTRIBUTING.md)

---

## License

Licensed under the **[LGPL-3.0 License](LICENSE)**.
