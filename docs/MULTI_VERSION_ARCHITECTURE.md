# Multi-Version Minecraft Architecture & Accommodation Guide

HeapHammer is engineered to support multiple minor and patch versions of Minecraft (e.g., `1.21.1`, `1.21.0`, `1.20.4`) while maintaining a single, unified source of truth for its core domain, deterministic scenario planning, and statistical regression detection engines.

This document details the architectural separation, branching strategy, automated synchronization mechanisms, and version-specific accommodation procedures.

---

## 1. Architectural Philosophy: The Portability Boundary

In traditional Minecraft mods, version migrations are difficult because domain logic is intertwined with Mojang's `net.minecraft` classes and Obfuscation/Mojang mappings.

HeapHammer prevents this through **Hexagonal Architecture (Ports & Adapters)**:

```text
┌────────────────────────────────────────────────────────────────────────┐
│               PORTABLE CORE DOMAIN (100% Version-Agnostic)             │
│                                                                        │
│   com.dwurdy.heaphammer.domain        com.dwurdy.heaphammer.detection  │
│   ├── ExperimentPlan                  ├── TrendDetectionEngine         │
│   ├── ExperimentState                 ├── OrdinaryLeastSquares         │
│   ├── CheckpointPhase                 └── PlateauDetector              │
│                                                                        │
│   com.dwurdy.heaphammer.scenario      com.dwurdy.heaphammer.reporting  │
│   ├── ChunkScenarioExecutor           ├── JsonReportService            │
│   ├── EntityScenarioExecutor          ├── ReportComparisonService      │
│   └── BlockEntityScenarioExecutor     └── HistogramDiff                │
│                                                                        │
│   ZERO net.minecraft.*  │  ZERO net.fabricmc.*  │  PURE Java 21       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Platform Port Interfaces
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│             VERSION-SPECIFIC PLATFORM ADAPTERS (Isolated)              │
│                                                                        │
│   com.dwurdy.heaphammer.platform                                       │
│   ├── PlatformAdapter (Interface)                                      │
│   └── ChunkTicketManager (Interface)                                   │
│                                                                        │
│   com.dwurdy.heaphammer.platform.fabric (Implementation)               │
│   ├── FabricPlatformAdapter      <-- Adapts Registries & Lifecycle    │
│   └── FabricChunkTicketManager   <-- Adapts TicketType & DistanceLevel │
└────────────────────────────────────────────────────────────────────────┘
```

### The Invariant Contract
- **95% of the codebase** (`domain`, `scenario`, `detection`, `reporting`, `storage`, `infrastructure`) has **zero** Minecraft imports.
- When `master` receives a new scenario, a statistical slope enhancement, or a CLI command, that code is **100% binary-compatible** across all supported Minecraft versions.
- Only the platform boundary (`platform.fabric`) and Brigadier command registration may require version-specific adjustments.

---

## 2. The Multi-Version Branching Model

We maintain a dedicated release branch for each active Minecraft version line alongside `master`:

```text
master (Trunk: Active Development & Cutting Edge — 1.21.1)
  │
  ├──> ver/1.21.1        (Modern Cutting Edge — Fabric, Java 21)
  ├──> ver/1.20.1        (Modern LTS Gold Standard — Fabric, Java 17)
  ├──> ver/1.18.2        (World-Gen Overhaul Era — Fabric, Java 17)
  ├──> ver/1.16.5        (Nether Legacy Era — Fabric, Java 17/8)
  └──> ver/1.12.2-forge  (Classic Titan Era — MinecraftForge, Java 8)
```

| Branch | Target Minecraft | Build Tooling | Mod Loader | Purpose & JVM Target |
|---|---|---|---|---|
| **`master`** | Latest (`1.21.1`) | `Fabric Loom 1.17` | `Fabric 0.19.5+` | Primary development trunk. Java 21. |
| **`ver/1.21.1`** | `1.21.1` | `Fabric Loom 1.17` | `Fabric 0.19.5+` | Cutting Edge production line. Java 21. |
| **`ver/1.20.1`** | `1.20.1` | `Fabric Loom 1.17` | `Fabric 0.15.11+` | Modern LTS Gold Standard line. Java 17. |
| **`ver/1.18.2`** | `1.18.2` | `Fabric Loom 1.17` | `Fabric 0.15.11+` | World-gen overhaul LTS line. Java 17. |
| **`ver/1.16.5`** | `1.16.5` | `Fabric Loom 1.17` | `Fabric 0.15.11+` | Nether legacy LTS line. Java 17/8. |
| **`ver/1.12.2-forge`** | `1.12.2` | `Java Library / Forge` | `MinecraftForge` | Classic Titan modpack era line. Java 8. |

---

## 3. Automated Branch Synchronization Workflow

Every commit pushed to `master` triggers the automated synchronization pipeline:
[`.github/workflows/sync-version-branches.yml`](../.github/workflows/sync-version-branches.yml).

### How the Pipeline Works:
1. **Trigger**: Push to `master`.
2. **Matrix Execution**: Runs in parallel for each target version branch (`ver/1.21.1`, etc.).
3. **Merge Attempt**:
   ```bash
   git checkout ver/1.21.1
   git merge --no-ff master -m "chore(sync): automated merge from master"
   ```
4. **Automated Verification**:
   - Compiles the mod against the branch's specific `gradle.properties`:
     ```bash
     ./gradlew test
     ```
5. **Outcome Handling**:
   - **Path A (Clean Merge & Tests Pass)**: Pushes the updated version branch automatically.
   - **Path B (Merge Conflict or Compilation Break)**: 
     - Aborts the automated push.
     - Automatically creates a GitHub Pull Request titled:  
       `[Auto-Sync] Merge master into ver/<version>`
     - Applies the label `needs-version-adaptation`.
     - Maintainers can review the conflict, adjust the version-specific `FabricPlatformAdapter`, and merge the PR.

---

## 4. Accommodating Version Differences

When adding a new version branch or resolving an adaptation PR, follow this protocol:

### Step 1: Branch Creation & `gradle.properties` Override
Create the branch from `master` and update the dependency properties:

```properties
# Example for ver/1.21.0
minecraft_version=1.21.0
loader_version=0.19.5
fabric_api_version=0.102.0+1.21.0
```

### Step 2: Adapt Platform Differences

Common Minecraft version divergence points and their resolution patterns:

#### 1. Registry Access (`BuiltInRegistries`)
- **1.21.x**: `BuiltInRegistries.ENTITY_TYPE` and `BuiltInRegistries.BLOCK` are directly accessible.
- **Pre-1.20.x**: May require registry lookup via `BuiltInRegistries.REGISTRY.get(...)`.

#### 2. Chunk Ticket Registration (`TicketType`)
- **1.21.x**:
  ```java
  public static final TicketType<ChunkPos> HEAPHAMMER_TICKET = 
      TicketType.create("heaphammer", Comparator.comparingLong(ChunkPos::toLong));
  ```
- Any changes to `TicketType.create` signature across Mojang versions are isolated inside:
  [`FabricChunkTicketManager.java`](../src/main/java/com/dwurdy/heaphammer/platform/fabric/FabricChunkTicketManager.java).

#### 3. Command Registration API
- Brigadier registrations in [`HeapHammerCommands.java`](../src/main/java/com/dwurdy/heaphammer/command/HeapHammerCommands.java) rely on Fabric's `CommandRegistrationCallback.EVENT.register(...)`. This API has remained stable across 1.19–1.21+.

### Step 3: Local Offline Verification
Run the local verification tool before pushing:
```powershell
powershell -ExecutionPolicy Bypass -File tools/sync-version-branches.ps1
```
This tests building, merging, and running the unit test suite across all configured version branches locally.
