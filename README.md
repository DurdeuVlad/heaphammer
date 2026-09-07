<p align="center">
  <img src="assets/heaphammer_banner.png" alt="HeapHammer Banner" width="100%">
</p>

<div align="center">

<img src="assets/heaphammer_logo.png" alt="HeapHammer Logo" width="120">

# HeapHammer

**Catch Minecraft server memory leaks and mod crashes in minutes, not days.**

[![Release](https://img.shields.io/badge/release-v1.0.0--alpha.1-orange.svg?style=flat-square)](https://github.com/DurdeuVlad/heaphammer/releases)
[![Minecraft](https://img.shields.io/badge/minecraft-1.12.2_--_1.21.1-brightgreen.svg?style=flat-square)](docs/MULTI_VERSION_ARCHITECTURE.md)
[![Loaders](https://img.shields.io/badge/loaders-Fabric_%7C_Forge-blue.svg?style=flat-square)](docs/MULTI_VERSION_ARCHITECTURE.md)
[![License](https://img.shields.io/badge/license-LGPL--3.0-blueviolet.svg?style=flat-square)](LICENSE)
[![Side](https://img.shields.io/badge/side-server--only-informational.svg?style=flat-square)](#quickstart)

<p align="center">
  <a href="#what-can-it-simulate"><b>Workloads</b></a> •
  <a href="#how-it-works"><b>How It Works</b></a> •
  <a href="#built-for-staging-safe-on-live-production"><b>Safety</b></a> •
  <a href="#quickstart"><b>Quickstart</b></a> •
  <a href="#commands"><b>Commands</b></a> •
  <a href="docs/README.md"><b>Docs</b></a>
</p>

</div>

---

## Why HeapHammer?

In modpacks with 200+ mods, memory leaks usually take **days of chaotic player traffic** to crash a server. Staging servers stay idle, so bugs remain hidden until production.

**HeapHammer simulates hours of player activity in 2 minutes.**  
It safely exercises chunk, entity, and block lifecycles under a strict tick budget, cleans everything up, and checks whether memory actually resets or stays trapped.

> [!NOTE]
> **Profilers (Spark, JFR)**: *"What is using memory right now?"*  
> **HeapHammer**: *"What exact workload breaks this server, and can I replay it?"*

---

## What Can It Simulate?

HeapHammer runs repeatable, tick-budgeted stress tests across 4 core server areas:

| Workload | What It Does | Why It Matters |
|---|---|---|
| 🗺️ **World & Chunks** | Churns chunk loading and unloading in spiral, ring, or random patterns. | Catches map, claim, and world mods that forget to free chunks on unload. |
| 👾 **Entities & Mobs** | Spawns, exercises pathfinding, and despawns waves of mobs. | Catches entity tracking mods that keep dead mobs trapped in static lists. |
| ⚙️ **Machines & Blocks** | Places, ticks, and breaks machine blocks, containers, and pipes. | Catches tech mods with tile entity tick leaks or unpurged container data. |
| 🔌 **Mod Adapters** | Fires custom workload hooks directly into third-party mod APIs. | Catches cross-mod collisions where two mods accidentally leak memory together. |

---

## How It Works

```text
1. PLAN (Seed)        2. PACE (Safe Ticks)     3. CLEAN (Purge)       4. VERDICT (Slope)
Generate repeatable ──> Run max 10 ops/tick ──> Release all tickets ──> Check if memory resets
stress sequence         (TPS stays smooth)      & force cleanup         or keeps climbing
```

1. **Repeatable**: Every test uses a fixed random seed. When you find a bug, you can replay the exact same test sequence across server restarts.
2. **Safe for TPS**: Operations run incrementally within strict limits (`10 ops/tick`, `15 ms max`). It never starves or freezes the server thread.
3. **Clean Isolation**: Operates only on its own test tickets. Player chunks, world spawn, and existing builds are never touched.
4. **Smart Math vs. GC Noise**: Rather than guessing from volatile instantaneous memory spikes, it measures if memory steadily creeps up across repeated cycles (`PASS` vs `SUSPICIOUS`).

---

## Built for Staging. Safe on Live Production.

While HeapHammer is **designed primarily for staging and test servers** to catch regressions before rolling out modpack updates, it is engineered with strict **zero-destruction safety invariants** so it can safely run on live production worlds:

- 🛡️ **Zero World Corruption**: Allocates only dedicated test tickets (`TicketType heaphammer`). Player chunks, world spawn, and player builds are never touched or modified.
- ⏱️ **Strict TPS Protection**: Operations execute within a strict tick budget (`max 10 ops/tick`, `15 ms max`), preventing watchdog stalls and TPS lag for active players.
- 🧹 **Instant Clean Abort**: Running `/hh stop` or `/hh cleanup` immediately and cleanly releases 100% of test tickets and entities without requiring a server restart.

---

## Quickstart

HeapHammer is **100% server-side only**. Players do **not** need it installed to connect.

### 1. Install
Drop the compiled JAR into your test or staging server's `mods/` directory:
```bash
cp heaphammer-1.0.0-alpha.1.jar /path/to/server/mods/
```

### 2. Run Test (In Console or In-Game OP)
```text
# 1. Check server health
hh doctor

# 2. Run a 5-cycle chunk test (10 chunks per batch, seed 1234)
hh run chunks 5 10 1234

# 3. View the verdict
hh report show last
```

### 3. Read the Verdict
- **`PASS`**: Retained memory returned cleanly to baseline after cleanup.
- **`PASS (PLATEAU)`**: Normal cache warming that leveled off safely.
- **`SUSPICIOUS`**: Memory steadily accumulated every cycle. Indicates an active leak!

---

## Commands

All commands run via `/hh` in-game (Level 2 OP) or `hh` from the server console:

| Command | Description |
|---|---|
| `hh doctor` | Checks server readiness, loaded chunks, and ticket safety. |
| `hh run chunks [flags]` | Stresses chunk loading, holding, and unloading. |
| `hh run entities [flags]` | Stresses entity spawning, ticking, and despawning. |
| `hh run blockentities [flags]` | Stresses machine block placement and destruction. |
| `hh status` | Displays active test progress and ticket counts. |
| `hh stop` / `hh cleanup` | Aborts test and forcibly purges all test tickets and entities. |
| `hh report show last` | Displays the memory retention slope and verdict for the last run. |
| `hh report diff <runA> <runB>` | Compares two runs to see which mod caused memory to diverge. |
| `hh replay <run-id>` | Replays the exact recorded test sequence from a previous run. |
| `hh diagnostics histogram` | Shows the top 10 memory-consuming Java classes on the server. |

**Common Flags**: `--iterations=5` (cycles), `--batch=10` (units/cycle), `--seed=42` (seed), `--explicit-gc=true` (force GC at checkpoints).

---

## Real-World Proof

Tested and verified on live Minecraft 1.21.1 Fabric dedicated servers:

- **Vanilla Server**: Slope `+0.75 MB/cycle` $\rightarrow$ **`PASS`** (Clean baseline)
- **Leaking Chunk Mod**: Slope `+10.56 MB/cycle` $\rightarrow$ **`SUSPICIOUS`** (Caught static chunk cache leak)
- **Cross-Mod Collision**: Mod A alone = `PASS`, Mod B alone = `PASS`, **Both together = `SUSPICIOUS`** (Caught circular subscriber bug!)

*See [docs/CASE_STUDIES.md](docs/CASE_STUDIES.md) for full benchmarks, raw server logs, and our modpack triage guide.*

---

## Multi-Version Support

HeapHammer's core is pure Java (zero Minecraft dependencies), running across 5 version branches:

- **1.21.1** (Fabric, Java 21) — Active development trunk (`master`)
- **1.20.1** (Fabric & Forge, Java 17) — Modern LTS
- **1.18.2** (Fabric & Forge, Java 17) — World-Gen LTS
- **1.16.5** (Forge & Fabric, Java 17/8) — Legacy LTS
- **1.12.2** (MinecraftForge, Java 8) — Classic Titan

---

## Documentation & Building

- **Build**: `./gradlew test build buildTestmods` (34 tests pass)
- **Documentation Hub**: [docs/README.md](docs/README.md)
- **Release Guide**: [docs/PUBLICATION.md](docs/PUBLICATION.md)
- **Contributing**: [CONTRIBUTING.md](CONTRIBUTING.md)

---

## License

Licensed under the **[LGPL-3.0 License](LICENSE)**.
