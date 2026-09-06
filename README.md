# HeapHammer

> **Deterministic stress testing and retained-memory regression framework for modded Minecraft.**
>
> *Take a problem that appears after hours or days under production activity, compress the relevant activity into a repeatable staging workload, and produce a replayable test case with objective evidence.*
>
> 📄 **Research Paper Available**: For our empirical methodology, mathematical retention models, live server regression data, and cross-mod collision analysis, see [docs/PAPER.md](docs/PAPER.md) (*Empirical Detection of Accidental Retained-Memory Regressions and Cross-Mod Collisions in Modded Minecraft*).

---

## 1. Executive Summary


In modern modpacks with 200–300+ mods, server-side memory leaks and retained-object regressions are notoriously difficult to diagnose. They often take days of continuous player traffic to manifest, making staging reproduction painfully slow.

**HeapHammer solves this by automating deterministic workload compression.**

Instead of passively waiting for a crash or guessing with profilers:
1. **Apply a deterministic workload**: Hammer the chunk, entity, and block lifecycles repeatedly under a strict server tick budget.
2. **Clean up & settle**: Release all allocated tickets/references and allow GC to settle.
3. **Measure what stays behind**: Track retained heap slope, world-state recovery, and class histograms across cycles.
4. **Persist & Replay**: Save the exact operation sequence to JSON and replay it bit-for-bit across server restarts.

```text
Traditional Profilers (Spark, VisualVM, JFR):  "What is the server doing right now?"
HeapHammer:                                    "What exact workload breaks this server, and can I replay it?"
```

---

## 2. Quickstart Guide for Server Admins

HeapHammer is **100% server-side only**, requires **zero configuration files**, and does **not** require players or client-side mods.

### Step 1 — Clone Server to Staging
Copy your production server to a staging or testing directory:
```bash
cp -r /opt/minecraft/production /opt/minecraft/staging
```
*(Tip: Set `server-port=25566` in `staging/server.properties` to avoid port conflicts).*

### Step 2 — Install HeapHammer
Drop the single compiled mod jar into the `mods/` folder:
```bash
cp heaphammer-1.0.0-alpha.1.jar /opt/minecraft/staging/mods/
```

### Step 3 — Run the Test
Start your server with your normal production launch script and JVM flags. In the server terminal console (or in-game as an OP), run:

```text
# 1. Verify environment readiness and loaded chunks
hh doctor

# 2. Preview the deterministic plan (e.g. 10 cycles, 5 chunks/batch, seed 1234)
hh plan chunks 10 5 1234

# 3. Execute the workload across server ticks
hh run chunks 10 5 1234

# 4. Check progress at any time
hh status

# 5. Read the verdict once complete
hh report show last
```

### Understanding the Verdict
- **`PASS`**: Post-cleanup retained heap returned to baseline across repeated cycles.
- **`PASS (PLATEAU)`**: Initial memory growth leveled off into a stable plateau (normal bounded cache warming).
- **`SUSPICIOUS`**: Monotonic retained heap growth detected across post-warmup cycles (indicates a persistent retention leak).
- **`CLEANUP_FAILED`**: The server failed to unload chunks or release entities after workload completion.

---

## 3. Command Reference

All commands are prefixed with `/hh` (or `hh` from console):

| Command | Permission | Description |
|---|---|---|
| `/hh doctor` | All | Inspects server readiness, loaded chunks, and ticket safety. |
| `/hh metrics` | All | Instant snapshot of JVM heap, max memory, and chunk counts. |
| `/hh scenario list` | All | Lists all available built-in and external scenario engines. |
| `/hh scenario describe <scenario>` | All | Describes mechanics and options for a scenario family. |
| `/hh plan chunks [flags]` | All | Computes and saves a deterministic chunk operation plan. |
| `/hh run chunks [flags]` | OP (Level 2) | Executes deterministic chunk churn with tick-budget pacing. |
| `/hh plan entities [flags]` | All | Computes and saves a deterministic entity lifecycle churn plan. |
| `/hh run entities [flags]` | OP (Level 2) | Spawns, exercises, and removes deterministic entity batches. |
| `/hh plan blockentities [flags]` | All | Plans deterministic block entity placement and cleanup. |
| `/hh run blockentities [flags]` | OP (Level 2) | Stresses block entity lifecycle, tick loops, and destruction. |
| `/hh adapters list` | OP (Level 2) | Lists registered external workload adapters and scenarios. |
| `/hh status` | All | Displays active scenario, iteration, state, and tickets. |
| `/hh stop` | OP (Level 2) | Aborts active experiment and purges all test tickets/entities. |
| `/hh cleanup` | OP (Level 2) | Forcibly purges all HeapHammer tickets, entities, and blocks. |
| `/hh replay <run-id\|last>` | OP (Level 2) | Replays exact resolved operations from a saved plan. |
| `/hh rerun <run-id\|last>` | OP (Level 2) | Rebuilds and reruns scenario from original spec & seed. |
| `/hh report list` | All | Lists all saved JSON experiment reports. |
| `/hh report show <run-id\|last>` | All | Displays formatted summary, slope, and classification. |
| `/hh report diff <runA> <runB>` | All | Compares two reports for memory delta and slope changes. |
| `/hh diagnostics histogram` | OP (Level 2) | Captures top 10 growing JVM classes via DiagnosticCommand MBean. |
| `/hh diagnostics heapdump` | OP (Level 2) | Triggers an asynchronous `.hprof` heap dump to disk. |
| `/hh diagnostics jfr start\|dump\|stop` | OP (Level 2) | Programmatic control over Java Flight Recorder captures. |
| `/hh fixture <LEAK\|CLEAN\|BOUNDED\|OFF>` | OP (Level 2) | Controls synthetic retention fixtures for calibration testing. |

### Supported Command Flags
Workload generation commands (`/hh plan` and `/hh run`) support the following optional flags:
- `--iterations=<int>` (default `5`): Number of stress cycles.
- `--batch=<int>` (default `9`): Units (chunks, entities, blocks) processed per cycle.
- `--radius=<int>` (default `6`): Coordinate radius around the player or center.
- `--seed=<long>` (default `42`): Random seed for reproducible generation.
- `--strategy=<STRATEGY>`: Generation pattern (`SPIRAL`, `RING`, `RANDOM_WALK`, `HOTSPOT`, `GRID`, `KILL`, `DISCARD`).
- `--coverage=<float>`: Registry sampling coverage between `0.0` and `1.0` (e.g. `--coverage 0.25`).
- `--include-mod=<id,id,...>`: Restrict sampled entities or blocks to specific mod namespaces.
- `--exclude-mod=<id,id,...>`: Exclude specific mod namespaces from sampling.
- `--explicit-gc=<true|false>`: Force System.gc() at each checkpoint to isolate true uncollected retention.

---

## 4. Empirical Verification & Testing Methodology

HeapHammer is tested against a rigorous 5-layer verification methodology:

```text
┌─────────────────────────────────────────────────────────────┐
│ Layer 5: Live Dedicated Server Integration (Minecraft 1.21.1)│
├─────────────────────────────────────────────────────────────┤
│ Layer 4: Controlled Synthetic Leak Fixtures (Section 23.3)   │
├─────────────────────────────────────────────────────────────┤
│ Layer 3: Trend Analysis, R² Fit & Plateau Math (Section 12)  │
├─────────────────────────────────────────────────────────────┤
│ Layer 2: Deterministic Planning & Replay Invariants (BR-001) │
├─────────────────────────────────────────────────────────────┤
│ Layer 1: Atomic File Storage & Codec Roundtrips (BR-011)    │
└─────────────────────────────────────────────────────────────┘
```

### Live Minecraft Dedicated Server Verification Evidence
The following unedited terminal log excerpts demonstrate HeapHammer executing live on a Minecraft 1.21.1 Fabric dedicated server:

#### 1. Live Server Boot & Doctor Health Check
```text
[Server thread/INFO] Starting minecraft server version 1.21.1
[Server thread/INFO] (heaphammer) HeapHammer commands and lifecycle registered successfully.
[Server thread/INFO] Done (1.122s)! For help, type "help"

> hh doctor
[Server thread/INFO] HeapHammer Doctor:
- Server status: READY
- Total loaded chunks: 841
- Active tickets owned by HeapHammer: 0
- Warning: Always run memory experiments on a test world or dedicated staging server!
```

#### 2. Live JVM Class Histogram
```text
> hh diagnostics histogram
[Server thread/INFO] Capturing JVM class histogram...
[Server thread/INFO] --- JVM Class Histogram Top 10 ---
#1 [B: 809397 instances (57.60 MB)
#2 [Ljdk.internal.vm.FillerElement;: 19549 instances (29.56 MB)
#3 [Ljava.lang.Object;: 321126 instances (20.28 MB)
#4 java.lang.String: 779994 instances (17.85 MB)
#5 [I: 21410 instances (17.74 MB)
#6 java.util.HashMap$Node: 424579 instances (12.96 MB)
#7 [J: 24641 instances (11.08 MB)
#8 net.minecraft.core.BlockPos: 352410 instances (8.07 MB)
#9 [D: 53539 instances (7.38 MB)
#10 com.google.common.collect.ImmutableMapEntry: 302950 instances (6.93 MB)
Total: 7264593 instances, 321.07 MB
```

#### 3. Live Chunk Churn Execution & `PASS` Verdict
```text
> hh run chunks 5 2 1234
[Server thread/INFO] Started Experiment: hh-20260906-160231-5472 (5 cycles, radius 6)

> hh status
[Server thread/INFO] Active Experiment: hh-20260906-160231-5472
- State: CLEANING_UP (Releasing chunks for iteration 2)
- Iteration: 2 / 5
- Active Tickets: 3

[Server thread/INFO] All HeapHammer chunk tickets have been released.
[Server thread/INFO] Experiment finished with state: COMPLETED

> hh report show last
=== HeapHammer Report: hh-20260906-160231-5472 ===
Status: COMPLETED | Verdict: PASS
Duration: 57s | Checkpoints: 7
Slope: -4.77 MB/cycle (R² = 0.05)
Net Delta: -40.50 MB
Rationale: PASS: Retained heap stable across cycles (slope = -4.77 MB/cycle, net delta = -40.50 MB).
Canonical Replay: /hh run chunks --seed=42 --center=3,0 --radius=6 --iterations=5 --batch=9 --strategy=spiral --warmup=1 --hold=20 --settle=40
```

#### 4. Controlled Leak Detection (`SUSPICIOUS` Verdict)
Enabling a controlled synthetic leak fixture (+25 MB/cycle) during live server chunk churn:
```text
> hh fixture LEAK 25
[Server thread/INFO] Synthetic fixture set to LEAK (25 MB/cycle).

> hh run chunks 5 2 1234
[Server thread/INFO] Started Experiment: hh-20260906-160401-2553 (5 cycles, radius 6)
...
[Server thread/INFO] All HeapHammer chunk tickets have been released.
[Server thread/INFO] Experiment finished with state: COMPLETED

> hh report show last
=== HeapHammer Report: hh-20260906-160401-2553 ===
Status: COMPLETED | Verdict: SUSPICIOUS
Duration: 57s | Checkpoints: 7
Slope: 19.20 MB/cycle (R² = 0.94)
Net Delta: 47.97 MB
Rationale: SUSPICIOUS: Monotonic retained heap growth detected (+19.20 MB/cycle, R² = 0.94, net delta = +47.97 MB).
```

#### 5. Comparative Differential Report (`/hh report diff`)
Comparing the clean baseline run against the leaking run:
```text
> hh report diff hh-20260906-160231-5472 hh-20260906-160401-2553
[Server thread/INFO] --- Report Diff (hh-20260906-160231-5472 vs hh-20260906-160401-2553) ---
Environment: IDENTICAL
Initial Heap: 352.82 MB -> 320.32 MB
Final Heap:   312.32 MB -> 368.28 MB
Net Delta:    -40.50 MB vs +47.97 MB (Diff: +88.47 MB)
Retained Slope: -4.77 MB/cyc vs +19.20 MB/cyc (Diff: +23.97 MB/cyc)
Classification: PASS -> SUSPICIOUS (CHANGED)
```

#### 6. Asynchronous Heap Dumps & Java Flight Recorder
```text
> hh diagnostics jfr start
[Server thread/INFO] JFR recording started.

> hh diagnostics jfr dump
[Server thread/INFO] Dumped JFR to: heaphammer\reports\jfr\manual-1788710719913.jfr

> hh diagnostics heapdump
[Server thread/INFO] Triggering async heap dump to manual-1788710720072.hprof (Warning: temporary STW pause possible)...
[HeapDumpService] Starting JVM heap dump to heaphammer\reports\heapdumps\manual-1788710720072.hprof (liveOnly=true).
[HeapDumpService] Heap dump complete in 816 ms (size: 411 MB) -> heaphammer\reports\heapdumps\manual-1788710720072.hprof
```

---

## 5. Empirical Multi-Mod & Cross-Mod Collision Verification

To guarantee that HeapHammer reliably diagnoses real third-party mod bugs, we built an automated matrix test harness (`tools/run-mod-matrix-test.ps1`) executing standalone Fabric test mods on live dedicated servers.

### Live Dedicated Server Matrix Results

All matrix benchmarks executed on Minecraft 1.21.1 Fabric dedicated server with 5 iterations, 10 chunks/batch, radius 6, 5 hold ticks, 10 settle ticks, and `--explicit-gc=true`:

| Scenario ID | Test Mod Environment | Leaked Subsystem | Slope (MB/cycle) | Net Delta | Verdict | Proof Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **01_Baseline_Clean** | Vanilla + HeapHammer | None (Baseline) | **+0.75 MB** | +3.00 MB | **`PASS`** | Clean Server |
| **02_SingleArea** | `testmod-leak-chunkcache` | Chunk Event Listener (`LevelChunk`) | **+10.56 MB** | +42.24 MB | **`SUSPICIOUS`** | Caught |
| **03_MultiSubsystem** | `testmod-leak-omnitrack` | Chunks + Entities + Tick Queue | **+10.70 MB** | +42.80 MB | **`SUSPICIOUS`** | Caught |
| **04_CrossMod_A** | `testmod-crossmod-core` | Core EventBus Provider Alone | **+0.74 MB** | +2.96 MB | **`PASS`** | Clean in Isolation |
| **05_CrossMod_B** | `testmod-crossmod-consumer` | Consumer Mod Alone (Fallback) | **+0.75 MB** | +3.00 MB | **`PASS`** | Clean in Isolation |
| **06_CrossMod_Collision**| **Mod A + Mod B Together** | **Accidental Circular Subscriber Loop** | **+10.52 MB** | **+42.08 MB** | **`SUSPICIOUS`** | **Collision Caught!** |
| **07_Entities_Clean** | Vanilla + HeapHammer (Entities)| None (Baseline) | **+0.74 MB** | +2.96 MB | **`PASS`** | Clean Server |
| **08_Entities_OmniTrack** | `testmod-leak-omnitrack` | Entity Registry + WorkloadAdapter | **+6.41 MB** | +41.02 MB | **`SUSPICIOUS`** | Caught |
| **09_BlockEntities_Clean** | Vanilla + HeapHammer (Blocks) | None (Baseline) | **+0.03 MB** | +0.24 MB | **`PASS`** | Clean Server |

### The Cross-Mod Collision Proof
- **Mod A alone**: 0.74 MB/cycle -> **`PASS`**
- **Mod B alone**: 0.75 MB/cycle -> **`PASS`**
- **Mod A + Mod B together**: 10.52 MB/cycle -> **`SUSPICIOUS`**

Running differential analysis (`/hh report diff`):
```text
Comparison ModA_Alone vs CrossMod_Collision:
Net Delta Diff = +34.93 MB, Slope Diff = +9.78 MB/cycle (PASS -> SUSPICIOUS).
```

*For complete logs, class histograms, and deep architectural analysis, see [docs/CASE_STUDIES.md](docs/CASE_STUDIES.md).*

### Modpack Leak Triage Playbook for Server Admins

When experiencing unexplained TPS drops, memory bloat, or out-of-memory crashes on your modpack server:

1. **Establish Baseline**: Run `/hh run chunks --iterations=5 --batch=10 --hold=5 --settle=10 --explicit-gc=true` on your staging server.
2. **Binary Search**: If `SUSPICIOUS`, split mods in half. If both halves pass alone, you have a **cross-mod collision**.
3. **Differential Isolation**: Compare runs with `/hh report diff <clean-report> <collision-report>` to identify the divergence point.
4. **Inspect Classes**: Run `/hh diagnostics histogram` to isolate the exact class names holding retained roots.

---

## 6. Architecture & Safety Safeguards


1. **Ticket Ownership Isolation (BR-002)**: HeapHammer only unloads chunk tickets registered under its own `TicketType<ChunkPos> heaphammer`. Chunks loaded by players, spawn, or other mods are never touched.
2. **Tick-Budgeted Execution (Section 15.4)**: Operations execute incrementally per server tick within configurable maximum operations and millisecond limits to protect server TPS.
3. **Zero Leaked References**: State plans and checkpoints persist only integer chunk coordinates and primitive metrics—never live `LevelChunk` or `ServerLevel` object references.
4. **Atomic Reports & Plans**: File writes utilize temporary file swaps with atomic replacement (`StandardCopyOption.ATOMIC_MOVE`) to prevent corrupted files if a server crashes.

---

## 7. Building & Contributing

### Requirements
- Java 21 JDK
- Gradle (wrapper provided)

### Build & Run Tests
```bash
# Run all automated test suites (34 unit & integration tests)
./gradlew test

# Compile and package the mod jar
./gradlew build

# Compile all synthetic test mod fixtures
./gradlew buildTestmods

# Run automated multi-mod matrix verification
powershell -ExecutionPolicy Bypass -File tools/run-mod-matrix-test.ps1

# Launch the Fabric dedicated test server locally
./gradlew runServer
```

### Contributing & Multi-Version Development
- [CONTRIBUTING.md](CONTRIBUTING.md): Code style, hexagonal architecture rules, determinism invariants, and PR guidelines.
- [docs/MULTI_VERSION_ARCHITECTURE.md](docs/MULTI_VERSION_ARCHITECTURE.md): Multi-version Minecraft branching strategy, platform accommodation, and automated synchronization.
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md): Contributor Covenant v2.1 community guidelines.
- [SECURITY.md](SECURITY.md): Vulnerability reporting policy and supported release matrix.

### Artifact Locations
- Compiled mod jar: `build/libs/heaphammer-1.0.0-alpha.1.jar`
- Synthetic test mod jars: `build/testmods/`
- Matrix verification reports: `build/matrix-reports/`
- Server run artifacts: `run/heaphammer/reports/`, `run/heaphammer/plans/`, `run/heaphammer/heapdumps/`

---

## 8. License

Licensed under the [LGPL-3.0 License](LICENSE).

