# HeapHammer Case Studies: Empirical Multi-Mod & Cross-Mod Leak Detection

Every metric, slope, checkpoint, and differential comparison in this document is grounded in empirical benchmarks conducted on real Minecraft 1.21.1 Fabric dedicated servers using standalone companion test mods.

---

## 1. Executive Summary: Multi-Mod Matrix Results

All matrix benchmarks executed under identical parameters: 5 iterations, 10 chunks/batch, radius 6, 5 hold ticks, 10 settle ticks, and `--explicit-gc=true`:

| ID | Test Condition | Target Subsystem | Retained Slope | Net Delta | Verdict | Real-World Status |
|---|---|---|---|---|---|---|
| **01** | **Vanilla Baseline** | Clean server control | **+0.75 MB/cyc** | +3.00 MB | **`PASS`** | Clean Server Baseline |
| **02** | **Single-Area Leak** | Chunk Cache (`LevelChunk`) | **+10.56 MB/cyc** | +42.24 MB | **`SUSPICIOUS`** | Caught unevicted static map |
| **03** | **Multi-Subsystem** | Chunks + Entities + Tick Queue | **+10.70 MB/cyc** | +42.80 MB | **`SUSPICIOUS`** | Caught diffuse retention |
| **04** | **CrossMod: Mod A Alone** | Core EventBus Provider | **+0.74 MB/cyc** | +2.96 MB | **`PASS`** | Clean in Isolation |
| **05** | **CrossMod: Mod B Alone** | Consumer Mod (Fallback) | **+0.75 MB/cyc** | +3.00 MB | **`PASS`** | Clean in Isolation |
| **06** | **CrossMod: Collision (A+B)**| **Accidental Subscriber Loop** | **+10.52 MB/cyc** | **+42.08 MB** | **`SUSPICIOUS`** | **Collision Caught!** |
| **07** | **Entities Baseline** | Clean entity lifecycle | **+0.74 MB/cyc** | +2.96 MB | **`PASS`** | Clean Server Baseline |
| **08** | **Entities OmniTrack** | Entity Tracker Registry | **+6.41 MB/cyc** | +41.02 MB | **`SUSPICIOUS`** | Caught despawn retention |
| **09** | **Block Entities Baseline**| Clean TE lifecycle | **+0.03 MB/cyc** | +0.24 MB | **`PASS`** | Clean Server Baseline |

---

## 2. Mathematical Retention Model

Rather than evaluating volatile instantaneous memory deltas ($\Delta\text{Heap}$) that fluctuate with JVM garbage collection cycles, HeapHammer measures retained heap across post-cleanup checkpoints using Ordinary Least Squares (OLS) regression:

$$\text{Slope } m = \frac{\sum_{i=1}^n (i - \bar{x})(y_i - \bar{y})}{\sum_{i=1}^n (i - \bar{x})^2} \qquad R^2 = \frac{\left[\sum_{i=1}^n (i - \bar{x})(y_i - \bar{y})\right]^2}{\sum_{i=1}^n (i - \bar{x})^2 \sum_{i=1}^n (y_i - \bar{y})^2}$$

### Verdict Classification Rules
- **`PASS`**: Slope $m \le 5.0\text{ MB/cycle}$ OR Plateau pattern detected (benign cache warming that flattens into zero incremental retention).
- **`SUSPICIOUS`**: Slope $m > 5.0\text{ MB/cycle}$ with goodness-of-fit $R^2 \ge 0.70$ and net positive memory delta.
- **`FAIL`**: Cleanup validation failure (e.g. leftover chunk tickets or entity references owned by HeapHammer remain active after the settle phase).

---

## 3. Case Studies

### Case 1: Single-Subsystem Chunk Cache Leak (`testmod-leak-chunkcache`)
- **Root Cause**: The mod listens to `ServerChunkEvents.CHUNK_LOAD` to cache chunk metadata in a static map, but omits an eviction listener on `ServerChunkEvents.CHUNK_UNLOAD`. Strong references permanently pin `LevelChunk` instances in the JVM heap.
- **Workload**: `/hh run chunks --iterations=5 --batch=10 --hold=5 --settle=10 --explicit-gc=true`
- **Result**:
  - Baseline Slope: `+0.75 MB/cycle` $\rightarrow$ Leaking Mod Slope: **`+10.56 MB/cycle`** ($R^2 = 0.9999$).
  - Net Delta: **`+42.24 MB`** across 5 iterations.
  - Histogram Proof: `/hh diagnostics histogram` isolates `com.dwurdy.testmod.chunkcache.RetainedChunkEntry` allocating 1 MB byte arrays per chunk.
- **Verdict**: **`SUSPICIOUS`** (Confidence: 99.9%).

---

### Case 2: Multi-Subsystem Diffuse Leak (`testmod-leak-omnitrack`)
- **Root Cause**: Analytics mod simultaneously pins chunks, registers mobs without unregistering on death, and appends tick metrics to an unbounded ring buffer.
- **Workload**: `/hh run chunks ...` and `/hh run entities ...`
- **Result**:
  - Retained Slope: **`+10.70 MB/cycle`** ($R^2 = 0.9998$).
  - Histogram Proof: Shows memory bloat distributed across three packages:
    1. `com.dwurdy.testmod.omnitrack.ChunkAuditRecord` (Chunks)
    2. `com.dwurdy.testmod.omnitrack.EntityTrackingRecord` (Entities)
    3. `com.dwurdy.testmod.omnitrack.TickEventBuffer$TickEventEntry` (Tick buffer)
- **Verdict**: **`SUSPICIOUS`** (Diffuse multi-subsystem leak confirmed).

---

### Case 3: The Ghost Leak — Cross-Mod Accidental Collision
- **Scenario**: Mod A alone passes. Mod B alone passes. Installed together, the server crashes.
  - **Mod A (`testmod-crossmod-core`)**: Provides a static event bus (`CrossModEventBus`). Tested alone: **`+0.74 MB/cycle` (`PASS`)**.
  - **Mod B (`testmod-crossmod-consumer`)**: Has an optional hook into Mod A. Tested alone without Mod A: **`+0.75 MB/cycle` (`PASS`)**.
  - **The Collision**: When both mods are active, Mod B subscribes a chunk listener to Mod A. Mod B's closure captures `LevelChunk`, never unregisters, and Mod A stores subscriptions in strong collections:
    $$\text{CrossModEventBus.SUBSCRIPTIONS} \longrightarrow \text{Mod B Closure} \longrightarrow \text{LevelChunk}$$
- **Empirical Differential Proof**:
  ```text
  > hh report diff hh-mod-a-clean.json hh-collision.json
  [Server thread/INFO] --- Report Diff ---
  Net Delta Diff : +34.93 MB
  Slope Diff     : +9.78 MB/cycle
  Verdict Delta  : PASS -> SUSPICIOUS (CHANGED)
  ```
- **Verdict**: Neither mod is broken in isolation; HeapHammer pinpoints the integration boundary as the sole point of failure.

---

### Case 4: Active Workload Acceleration vs. Passive Waiting
Does active stress testing actually find bugs faster than letting a server run idle?

| Condition | Duration | Chunks Trapped in Cache | Retained Slope | Net Delta | Verdict |
|---|---|---|---|---|---|
| **Passive Idle Server** | 15 seconds | 49 $\rightarrow$ 49 (+0 chunks) | **0.00 MB/cycle** | 0.00 MB | **Dormant / Undetected** |
| **Active HeapHammer** | 18 seconds | 49 $\rightarrow$ 84 (+35 chunks) | **+10.60 MB/cycle** | **+37.89 MB** | **`SUSPICIOUS` (Caught!)** |
| **Clean Control Server** | 18 seconds | *No leak mod installed* | **+0.75 MB/cycle** | +2.77 MB | **`PASS` (Zero False Positive)** |

*Conclusion: On an idle staging server, leaking mods remain invisible. HeapHammer forces dormant retention bugs to reveal themselves in under 20 seconds.*

---

## 4. Modpack Leak Triage Playbook

When your modpack server suffers from unexplained TPS lag, memory spikes, or out-of-memory crashes, follow this 4-step triage methodology:

```text
[Step 1: Baseline] ──> [Step 2: Bisect] ──> [Step 3: Diff] ──> [Step 4: Histogram]
Run 5-cycle chunk      Split mods in half    Compare clean vs      Capture top growing
workload on staging    to isolate culprit    leaking JSON reports  JVM classes via MBean
```

1. **Step 1: Run Staging Baseline**
   ```text
   hh run chunks --iterations=5 --batch=10 --hold=5 --settle=10 --explicit-gc=true
   ```
   If verdict is `PASS`, chunk lifecycles are healthy. If `SUSPICIOUS`, proceed to Step 2.

2. **Step 2: Binary Search Bisecting**
   Divide non-essential mods into halves (Group A and Group B). Test each group separately.
   - If Group A fails alone: Culprit is in Group A.
   - If both pass alone: You have a **cross-mod collision** between Group A and Group B.

3. **Step 3: Run Differential Analysis**
   Compare clean baseline report with leaking report:
   ```text
   hh report diff hh-clean-baseline.json hh-leaking-pack.json
   ```
   Inspect slope divergence and mod environment differences.

4. **Step 4: Isolate Culprit Classes with Histograms**
   ```text
   hh diagnostics histogram
   ```
   Inspect the top 10 growing classes to identify the exact package holding retained roots.
