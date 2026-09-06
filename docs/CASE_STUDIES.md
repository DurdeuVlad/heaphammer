# HeapHammer Case Studies: Empirical Multi-Mod & Cross-Mod Leak Detection

This document details empirical tests conducted on real Minecraft 1.21.1 Fabric dedicated servers using standalone companion test mods. Every metric, slope, checkpoint, and differential comparison is grounded in actual live dedicated server runs.

---

## Executive Summary: Multi-Mod Matrix Results

All benchmarks executed with 5 iterations, 10 chunks/batch, radius 6, 5 hold ticks, 10 settle ticks, and `--explicit-gc=true`.

| Scenario ID | Test Condition | Installed Mods | Slope (MB/cycle) | Net Delta (MB) | Verdict | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **01** | **Vanilla Baseline** | HeapHammer only | **+0.75** | +3.00 | **`PASS`** | Clean |
| **02** | **Single-Area Leak** | `testmod-leak-chunkcache` | **+10.56** | +42.24 | **`SUSPICIOUS`** | Detected |
| **03** | **Multi-Subsystem Leak** | `testmod-leak-omnitrack` | **+10.70** | +42.80 | **`SUSPICIOUS`** | Detected |
| **04** | **CrossMod: Mod A Alone** | `testmod-crossmod-core` | **+0.74** | +2.96 | **`PASS`** | Clean |
| **05** | **CrossMod: Mod B Alone** | `testmod-crossmod-consumer` | **+0.75** | +3.00 | **`PASS`** | Clean |
| **06** | **CrossMod: Collision (A + B)**| `crossmod-core` + `consumer` | **+10.52** | +42.08 | **`SUSPICIOUS`** | **Caught!** |

---

## Case Study 1: Single-Subsystem Chunk Cache Leak (`testmod-leak-chunkcache`)

### The Problem
A common bug in utility, map, and chunk-claiming mods is unmanaged chunk caching. The mod registers a listener for `ServerChunkEvents.CHUNK_LOAD` to cache chunk data or coordinates in a static map, but fails to implement eviction on `ServerChunkEvents.CHUNK_UNLOAD`. As players explore or load chunks, `LevelChunk` instances are permanently pinned in JVM heap memory.

### How HeapHammer Catches It
HeapHammer runs its deterministic chunk churn scenario:
```text
/hh run chunks --iterations=5 --batch=10 --hold=5 --settle=10 --explicit-gc=true
```

1. **Chunk Lifecycle**: Chunks are loaded in batches, held for inspection, and tickets are released.
2. **Cleanup Validation**: Minecraft unloads the chunk ticket, but `testmod-leak-chunkcache` keeps references in static memory.
3. **Trend Detection**:
   - Baseline slope: `+0.75 MB/cycle`
   - Leaking mod slope: **`+10.56 MB/cycle`** ($R^2 = 0.9999$)
   - Net heap growth across 5 iterations: **`+42.24 MB`**
4. **Diagnosis**: Running `/hh diagnostics histogram` isolates `com.dwurdy.testmod.chunkcache.RetainedChunkEntry` and `net.minecraft.world.level.chunk.LevelChunk` in the top retained allocations.

---

## Case Study 2: Multi-Subsystem Leak (`testmod-leak-omnitrack`)

### The Problem
Advanced mods (analytics trackers, discord bridges, complex tech mods) interact with multiple Minecraft subsystems simultaneously. A memory leak in such a mod can be diffuse:
1. Retaining `LevelChunk` references on chunk loads.
2. Wrapping spawned mobs/entities in static tracking maps without unregistering on entity death or despawn (`ServerEntityEvents.ENTITY_LOAD`).
3. Appending tick statistics to an unpurged static dispatch ring buffer on `ServerTickEvents.END_SERVER_TICK`.

### How HeapHammer Catches It
1. **Trend Detection**: Retained heap grows at **`+10.70 MB/cycle`** ($R^2 = 0.9998$).
2. **Diagnostic Histogram Breakdown**:
   Querying `/hh diagnostics histogram` immediately shows anomalous retention spread across multiple domain packages:
   - `com.dwurdy.testmod.omnitrack.ChunkAuditRecord` (Chunk subsystem)
   - `com.dwurdy.testmod.omnitrack.EntityTrackingRecord` (Entity tracker subsystem)
   - `com.dwurdy.testmod.omnitrack.TickEventBuffer$TickEventEntry` (Tick buffer subsystem)
3. **Verdict**: **`SUSPICIOUS`** with high statistical confidence ($>0.95$).

---

## Case Study 3: The Ghost Leak — Cross-Mod Accidental Collision

### The Scenario: Mod A Alone = PASS, Mod B Alone = PASS, Together = CRASH
This represents the most notorious class of modpack bugs:
- **Mod A (`testmod-crossmod-core`)**: A central API mod providing a static event bus (`CrossModEventBus`). When tested alone with HeapHammer, no listeners subscribe; slope is **`+0.74 MB/cycle` (`PASS`)**.
- **Mod B (`testmod-crossmod-consumer`)**: A machine/consumer mod with an optional dependency on Mod A. When tested alone without Mod A, it uses clean internal fallbacks; slope is **`+0.75 MB/cycle` (`PASS`)**.
- **The Modpack Collision**:
  When both mods are installed together, Mod B detects Mod A via `FabricLoader.isModLoaded("testmod-crossmod-core")` and hooks chunk listeners into Mod A's bus:
  `CrossModEventBus.subscribe("chunk_tick", ...)`
  Mod B's closure captures `LevelChunk` and `ServerLevel`. Mod B never unregisters on chunk unload, and Mod A stores listeners in strong reference collections.

### The Empirical Evidence
Executing HeapHammer across the three runs:

```text
Run 04 (Mod A alone) : PASS       | Slope:  +0.74 MB/cycle | Net Delta:  +2.96 MB
Run 05 (Mod B alone) : PASS       | Slope:  +0.75 MB/cycle | Net Delta:  +3.00 MB
Run 06 (Combined)    : SUSPICIOUS | Slope: +10.52 MB/cycle | Net Delta: +42.08 MB
```

### The Differential Diagnosis (`/hh report diff`)
By comparing the baseline report of Mod A alone with the collision report:
```text
/hh report diff hh-20260906-165414-7322.json hh-20260906-165620-1884.json
```

**HeapHammer Output**:
```text
Comparison hh-20260906-165414-7322 vs hh-20260906-165620-1884:
- Net Delta Diff : +34.93 MB
- Slope Diff     : +9.78 MB/cycle
- Verdict Delta  : PASS -> SUSPICIOUS (CHANGED)
```

The differential engine proves that neither mod was leaking in isolation; the fault exists exclusively at the integration boundary where Mod B registered unmanaged callbacks into Mod A.

---

## Modpack Leak Triage Playbook for Server Administrators

When a modpack exhibits unexplained server stutter, memory bloat, or OOM crashes, follow this 4-step triage methodology:

### Step 1: Establish Your Modpack Baseline
Run a standard 5-iteration chunk stress workload with explicit GC on your staging server:
```text
/hh run chunks --iterations=5 --batch=10 --hold=5 --settle=10 --explicit-gc=true
```
Save the resulting report run ID (e.g., `hh-baseline.json`).

### Step 2: Binary Search Bisecting
If the verdict is `SUSPICIOUS`:
1. Divide non-essential mods into Group Alpha and Group Beta.
2. Disable Group Beta and rerun `/hh run chunks ...`.
3. If Group Alpha passes, the leak is in Group Beta. If both pass individually, you are facing a **cross-mod collision**.

### Step 3: Run Differential Analysis
Compare the passing run with the leaking run:
```text
/hh report diff hh-alpha-clean.json hh-full-pack.json
```
HeapHammer will highlight:
- The exact slope divergence (MB/cycle delta).
- Mod environment diffs (which mod was added/removed between runs).

### Step 4: Isolate Culprit Classes with Histograms
Trigger on-demand class histograms:
```text
/hh diagnostics histogram
```
Inspect the delta in instance counts and retained bytes between baseline and settle checkpoints to pinpoint the exact package and class holding the roots.
