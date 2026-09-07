<p align="center">
  <img src="../assets/heaphammer_banner.png" alt="HeapHammer Banner" style="width: 100%; max-height: 220px; object-fit: cover; border-radius: 8px;">
</p>

<div align="center">

<img src="../assets/heaphammer_logo.png" alt="HeapHammer Logo" width="96">

# 🔨 HeapHammer: The Modpack Admin's Guide
> **Catch 3-day memory leaks in 2 minutes before your players notice.**

</div>

---

## 1. The Nightmare You Know Too Well

You build a modpack. It runs great for 12 hours. Then, after 2 or 3 days of player activity:
- Server RAM slowly creeps from 4 GB to 12 GB.
- Tick times spike, TPS drops, and rubber-banding begins.
- Eventually, the server crashes: `java.lang.OutOfMemoryError: Java heap space`.
- You restart the server. It runs fine again... until 3 days later, when it crashes again.

> [!WARNING]
> **Why Spark and traditional profilers can't fix this:**  
> Spark tells you what is in RAM *right now*, but it cannot tell you **which mod caused it** or **whether the RAM will ever be freed**. Finding the guilty mod usually takes days of agonizing trial-and-error.

---

## 2. What HeapHammer Does

**HeapHammer compresses 72 hours of chaotic player traffic into a 2-minute test.**

It loads batches of chunks, spawns test entities, and exercises machine blocks under a strict tick budget. Then it releases everything, forces cleanup, and measures whether RAM actually goes back down.

- **`PASS`**: Memory reset cleanly. Your modpack is safe.
- **`SUSPICIOUS`**: A mod held onto chunks or entities after they unloaded. HeapHammer flags the leak and names the exact Java classes responsible.

---

## 3. Quick Setup (60 Seconds)

1. Download the JAR matching your Minecraft version (e.g. `heaphammer-1.20.1-1.0.0.jar` or `heaphammer-1.21.1-1.0.0.jar`) from Releases and place it into your server's `mods/` folder.
2. Restart the server.
3. Done.

> [!NOTE]
> **100% Server-Side Only**: Your players do **not** need to install HeapHammer. It runs purely on the server. Supported versions: 1.21.1, 1.20.1, 1.18.2, 1.16.5, and 1.12.2.

---

## 4. The 3 Commands You Actually Need

Run these in-game (requires OP Level 2) or directly in your server console:

### Step 1: Health Check
```text
hh doctor
```
*Run this first.* Confirms server memory, current loaded chunks, and ticket system health.

### Step 2: The 2-Minute Stress Test
```text
hh run chunks --iterations=5 --batch=10 --radius=8
```
*This catches 80% of all mod leaks.* It simulates rapid exploration across 5 cycles.
- Loads 10 chunks per batch up to radius 8.
- Holds them for 5 ticks so terrain and tile entities initialize.
- Releases tickets, waits for cleanup, and measures memory retention.
- **Total run time**: ~90 seconds.

### Step 3: View the Verdict
```text
hh report show last
```
Displays the mathematical verdict:
- **Slope $\le 0.75\text{ MB/cycle}$ (`PASS`)**: Completely healthy. Normal JVM noise.
- **Slope $> 2.0\text{ MB/cycle}$ (`SUSPICIOUS`)**: Memory is leaking continuously.

---

## 5. How to Find the Guilty Mod (The 2-Step Method)

If a test comes back `SUSPICIOUS`, here is how you find the exact culprit:

### Method A: Class Histogram (Instant Identity)
Run while the leak is active or right after the test:
```text
hh diagnostics histogram
```
Prints the top 10 object types in memory. Look for mod package names:
- `com.somebadmod.world.ChunkCache` $\rightarrow$ That mod is pinning unloaded chunks.
- `net.brokenmob.entity.Tracker` $\rightarrow$ That mod is leaking dead mob references.

### Method B: Before & After Diff (A/B Testing)
When updating a modpack or adding a new mod:
1. Run a test on your baseline: `hh run chunks` (creates `run-01`).
2. Add the new mod to your server.
3. Run the exact same test: `hh run chunks` (creates `run-02`).
4. Compare them:
   ```text
   hh report diff run-01 run-02
   ```
HeapHammer instantly compares the slopes. If `run-01` was **0.5 MB/cyc** and `run-02` jumped to **10.6 MB/cyc**, the new mod is 100% guilty.

---

## 6. Other Specialized Tests

| To Stress-Test | Run Command | Catches |
|---|---|---|
| **World Exploration** | `hh run chunks` | Minimap mods, land claims, terrain generators. |
| **Mob Farms & Combat** | `hh run entities` | Custom mobs, combat loggers, damage indicators. |
| **Machines & Automation** | `hh run blockentities` | Tech conduits, storage drawers, auto-crafters. |

---

## 7. Emergency Controls & Safety

HeapHammer is engineered never to harm your server:

- **Emergency Stop**: `hh stop` (instantly cancels the test and unloads all tickets).
- **Forced Cleanup**: `hh cleanup` (removes all HeapHammer tickets across all dimensions).
- **Circuit Breaker**: If server free RAM drops below 64 MB, tests abort automatically before an `OutOfMemoryError` can occur.
- **Tick Throttling**: Never lags the server thread (capped at 15 ms/tick).

---

> [!TIP]
> **Best Practice for Modpack Creators**:  
> Run `hh run chunks` before pushing any modpack update to CurseForge or Modrinth. If it passes, you know your server won't crash 3 days down the line.
