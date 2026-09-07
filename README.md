<p align="center">
  <img src="assets/heaphammer_banner.png" alt="HeapHammer Banner" width="100%">
</p>

<div align="center">

<img src="assets/heaphammer_logo.png" alt="HeapHammer Logo" width="120">

# HeapHammer

**Deterministic server stress testing & retained-memory regression framework for modded Minecraft**

[![Release](https://img.shields.io/badge/release-v1.0.0--alpha.1-orange.svg?style=flat-square)](https://github.com/DurdeuVlad/heaphammer/releases)
[![Minecraft](https://img.shields.io/badge/minecraft-1.12.2_--_1.21.1-brightgreen.svg?style=flat-square)](docs/MULTI_VERSION_ARCHITECTURE.md)
[![Loaders](https://img.shields.io/badge/loaders-Fabric_%7C_Forge-blue.svg?style=flat-square)](docs/MULTI_VERSION_ARCHITECTURE.md)
[![Java](https://img.shields.io/badge/java-8_%7C_17_%7C_21-red.svg?style=flat-square)](docs/JENKINS_PIPELINE.md)
[![License](https://img.shields.io/badge/license-LGPL--3.0-blueviolet.svg?style=flat-square)](LICENSE)
[![Tests](https://img.shields.io/badge/tests-34%20passed-success.svg?style=flat-square)](#building--contributing)
[![Side](https://img.shields.io/badge/side-server--only-informational.svg?style=flat-square)](#quickstart-for-server-admins)

<p align="center">
  <a href="#quickstart-for-server-admins"><b>Quickstart</b></a> •
  <a href="#command-reference"><b>Commands</b></a> •
  <a href="#how-it-works"><b>How It Works</b></a> •
  <a href="#proven-on-live-servers"><b>Empirical Proof</b></a> •
  <a href="docs/README.md"><b>Documentation Hub</b></a> •
  <a href="docs/PUBLICATION.md"><b>Publication Guide</b></a>
</p>

</div>

---

## Stop Guessing. Compress Staging Workloads.

In modpacks with 200–300+ mods, memory leaks and retained-chunk bugs often take **days of chaotic player traffic** to manifest, making staging reproduction painfully slow.

**HeapHammer automates deterministic workload compression.**  
It exercises chunk, entity, and block lifecycles under a strict server tick budget, releases all resources, and mathematically evaluates what stays behind—producing an objective, replayable verdict in minutes instead of days.

> [!NOTE]
> **Traditional Profilers (Spark, JFR)**: *"What is the server doing right now?"*  
> **HeapHammer**: *"What exact workload breaks this server, and can I replay it?"*

---

## Quickstart for Server Admins

HeapHammer is **100% server-side only** and requires **zero configuration files**.

> [!TIP]
> **Do connecting players need this mod?**  
> **No.** Clients do **not** need HeapHammer installed. It registers zero client blocks, items, or packets. Standard vanilla and modded clients connect seamlessly.

### 1. Install
Drop the compiled JAR into your test or staging server's `mods/` folder:
```bash
cp heaphammer-1.0.0-alpha.1.jar /opt/minecraft/staging/mods/
```

### 2. Run
Start your server. In the server console (or in-game as OP), execute:
```text
# 1. Check server health and chunk safety
hh doctor

# 2. Run a 5-cycle deterministic chunk churn test (10 chunks/batch, seed 1234)
hh run chunks 5 10 1234

# 3. Check progress anytime
hh status

# 4. View the statistical verdict
hh report show last
```

### 3. Read the Verdict
- **`PASS`**: Retained memory returned cleanly to baseline across cycles.
- **`PASS (PLATEAU)`**: Memory growth flattened into a stable plateau (normal bounded cache warming).
- **`SUSPICIOUS`**: Monotonic memory accumulation detected ($R^2 \ge 0.70$). Indicates an active leak.
- **`CLEANUP_FAILED`**: Server failed to release test chunk tickets or entities after completion.

---

## How It Works

```text
  ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
  │ 1. STRESS CYCLE │ ────> │ 2. CLEAN & SETTLE│ ────> │ 3. OLS REGRESSION│ ────> │ 4. REPLAY CASE  │
  │ Deterministic   │       │ Release tickets │       │ Fit y = mx + b  │       │ Export to JSON  │
  │ chunk & entity  │       │ & force garbage │       │ across cycle    │       │ for bit-for-bit │
  │ tick workload   │       │ collection      │       │ checkpoints     │       │ bug reproduction│
  └─────────────────┘       └─────────────────┘       └─────────────────┘       └─────────────────┘
```

1. **Deterministic Pacing**: Generates chunk and entity lifecycles using fixed seeds under strict tick limits (`10 ops/tick`, `15 ms/tick max`), never starving server TPS.
2. **Tick Isolation**: Allocates chunks strictly under HeapHammer's dedicated `TicketType`. Player and spawn chunks are never touched.
3. **Statistical Regression vs. GC Noise**: Rather than trusting volatile instantaneous memory diffs ($\Delta\text{Heap}$), HeapHammer calculates Ordinary Least Squares slope ($m$) and goodness-of-fit ($R^2$) across post-settle checkpoints.
4. **Bit-for-Bit Replay**: Persists exact operations to JSON. Any failing run can be replayed identically across server restarts with `/hh replay <run-id>`.

---

## Command Reference

All commands run via `/hh` in-game (Level 2 OP) or `hh` from the server console:

| Command | Description |
|---|---|
| `hh doctor` | Inspects environment health, loaded chunks, and ticket safety. |
| `hh run chunks [flags]` | Executes deterministic chunk churn with tick-budget pacing. |
| `hh run entities [flags]` | Exercises entity spawning, tracking, and despawn lifecycles. |
| `hh run blockentities [flags]` | Stresses block entity placement, ticking, and destruction. |
| `hh status` | Displays real-time scenario progress, active cycle, and ticket counts. |
| `hh stop` / `hh cleanup` | Immediately aborts active run and forcibly purges all test tickets. |
| `hh report show last` | Displays formatted report, linear regression slope ($m$), and verdict. |
| `hh report diff <runA> <runB>` | Compares two runs to pinpoint where a regression or mod leak diverged. |
| `hh replay <run-id>` | Replays exact recorded operations from a saved experiment plan. |
| `hh diagnostics histogram` | Captures top 10 growing JVM classes via DiagnosticCommand MBean. |
| `hh diagnostics heapdump` | Triggers an asynchronous `.hprof` heap dump to disk. |

### Common Workload Flags
- `--iterations=<int>` (default `5`): Number of stress cycles.
- `--batch=<int>` (default `9`): Chunks or entities processed per cycle.
- `--radius=<int>` (default `6`): Chunk radius around player or center point.
- `--seed=<long>` (default `42`): Random seed for reproducible generation.
- `--strategy=<STRATEGY>`: Generation pattern (`SPIRAL`, `RING`, `RANDOM_WALK`, `GRID`).
- `--explicit-gc=<true|false>`: Force `System.gc()` at settle checkpoints to isolate true retained roots.

---

## Proven on Live Servers

HeapHammer is validated against real Minecraft 1.21.1 Fabric dedicated servers and standalone test mods.

### Multi-Mod Test Matrix Summary
All tests run with 5 iterations, 10 chunks/batch, radius 6, and `--explicit-gc=true`:

| Scenario | Test Environment | Slope | Net Delta | Verdict | Real-World Finding |
|---|---|---|---|---|---|
| **Clean Baseline** | Vanilla + HeapHammer | **+0.75 MB/cyc** | +3.00 MB | **`PASS`** | Clean server recovers baseline memory. |
| **Chunk Cache Leak** | `testmod-leak-chunkcache` | **+10.56 MB/cyc** | +42.24 MB | **`SUSPICIOUS`** | Caught unevicted `LevelChunk` static map. |
| **Multi-Subsystem** | `testmod-leak-omnitrack` | **+10.70 MB/cyc** | +42.80 MB | **`SUSPICIOUS`** | Caught chunk + entity + tick buffer retention. |
| **Cross-Mod Collision** | **Mod A + Mod B Together** | **+10.52 MB/cyc** | **+42.08 MB** | **`SUSPICIOUS`** | **Both pass alone; collision caught together!** |

> 📊 **Full Logs & Mathematical Models**: For the complete empirical test matrix, raw server logs, class histograms, and our 4-step modpack leak triage playbook, see **[docs/CASE_STUDIES.md](docs/CASE_STUDIES.md)**.

---

## Multi-Version Architecture

HeapHammer uses **Hexagonal Architecture (Ports & Adapters)**. The core domain is **100% pure Java** with zero `net.minecraft.*` dependencies, ensuring binary portability across 5 major Minecraft version lines:

```text
master (1.21.1) ──> ver/1.20.1 ──> ver/1.18.2 ──> ver/1.16.5 ──> ver/1.12.2-forge
Java 21             Java 17         Java 17         Java 17/8       Java 8
Fabric/NeoForge     Fabric/Forge    Fabric/Forge    Fabric/Forge    MinecraftForge
```

- **[docs/MULTI_VERSION_ARCHITECTURE.md](docs/MULTI_VERSION_ARCHITECTURE.md)**: Compatibility matrix and automated branch synchronization.
- **[docs/FORGE_1_12_2_BRIDGE.md](docs/FORGE_1_12_2_BRIDGE.md)**: Reflection-decoupled bridge for classic 1.12.2 Forge.

---

## Building & Contributing

```powershell
# Run automated test suite (34 unit & integration tests)
./gradlew test

# Compile mod JAR and synthetic testmod fixtures
./gradlew build buildTestmods

# Run automated multi-mod matrix verification
powershell -ExecutionPolicy Bypass -File tools/run-mod-matrix-test.ps1
```

- **[docs/README.md](docs/README.md)**: Master documentation hub and index.
- **[docs/PUBLICATION.md](docs/PUBLICATION.md)**: Release staging branches, packaging, and distribution guide.
- **[CONTRIBUTING.md](CONTRIBUTING.md)**: Contribution standards, hexagonal boundary rules, and PR workflow.
- **[AGENTS.md](AGENTS.md)**: AI disclosure policy and machine-readable issue reporting standards.
- **[docs/DECISION.md](docs/DECISION.md)**: Architectural Decision Records (ADRs).

---

## License

Licensed under the **[GNU Lesser General Public License v3.0 (LGPL-3.0)](LICENSE)**.
