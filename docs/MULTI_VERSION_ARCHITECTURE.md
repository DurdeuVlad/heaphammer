# Multi-Version Minecraft Architecture & Accommodation Guide

HeapHammer is engineered to support multiple minor and major Minecraft versions (from modern `1.21.1` and `1.20.1` down to classic `1.12.2`) while maintaining a single, unified source of truth for its core domain, deterministic scenario planning, and statistical regression detection engines.

This document details the ecosystem era analysis, architectural portability boundaries, compatibility matrix, branching strategy, automated synchronization mechanisms, and version-specific accommodation procedures.

---

## 1. Modding Ecosystem & Version Era Analysis

In modded Minecraft, community adoption concentrates in distinct **modding eras** driven by API stability, mod availability, and world-generation overhauls.

```text
2026                 2023–2024               2021–2022              2020–2021               2017–2019
Modern Cutting-Edge  Modern Gold Standard    World-Gen Overhaul     Nether Bridge Era       Classic Titan
     [1.21.1] ───────> [1.20.1] ───────────> [1.18.2] ────────────> [1.16.5] ─────────────> [1.12.2]
     Java 21           Java 17                Java 17                Java 17 / 8             Java 8
     Fabric/NeoForge   Fabric/Forge           Fabric/Forge           Fabric/Forge            MinecraftForge
```

### The 5 Target Anchor Versions

| Version | Ecosystem Role | JVM Target | Mod Loaders | Why HeapHammer is Essential Here |
|---|---|---|---|---|
| **1.21.1** | **Active Frontier** | Java 21 | Fabric, NeoForge | High mod churn; newest optimizations (Lithium, FerriteCore) and latest Vanilla mechanics. Primary development trunk (`master`). |
| **1.20.1** | **Modern Gold Standard** | Java 17 | Fabric, Forge | **Highest modpack player count.** Major tech mods (Mekanism, AE2, Create, Botania) in peak production. Heavy memory leak surface from 300+ modpacks. |
| **1.18.2** | **World-Gen Overhaul** | Java 17 | Fabric, Forge | 384-block world height ($Y=-64$ to $320$), new chunk format, heavy chunk generation/unloading memory pressure. |
| **1.16.5** | **Pre-Caves Bridge** | Java 17 / 8 | Fabric, Forge | Highly stable long-term modpacks; transition point before 1.17+ architectural shifts. |
| **1.12.2** | **The Classic Titan** | Java 8 | MinecraftForge | **Legendary technical mega-packs** (GT: New Horizons, SevTech, Enigmatica 2). Long-running servers suffer massive retention leaks. |

---

## 2. Architectural Portability Boundary: Hexagonal Architecture

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
│   ZERO net.minecraft.*  │  ZERO net.fabricmc.*  │  PURE Java           │
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
│   Modern Fabric (1.16–1.21.1)        Classic Forge (1.12.2)            │
│   ├── FabricPlatformAdapter          ├── ForgePlatformAdapter          │
│   └── FabricChunkTicketManager       ├── ForgeChunkTicketManager       │
│                                      └── ForgeTicketBridge             │
└────────────────────────────────────────────────────────────────────────┘
```

### The Invariant Contract
- **95% of the codebase** (`domain`, `scenario`, `detection`, `reporting`, `storage`, `infrastructure`) has **zero** Minecraft or modloader imports.
- When `master` receives a new scenario, a statistical slope enhancement, or a CLI command, that code is **100% binary-compatible** across all supported Minecraft versions.
- Only the platform boundary (`platform.fabric` or `platform.forge`) and command dispatch registration require version-specific adjustments.

---

## 3. Subsystem Compatibility Matrix

| Architectural Subsystem | Modern Frontier (1.21.x) | Modern LTS (1.20.1, 1.18.2) | Legacy Bridge (1.16.5) | Classic Titan (1.12.2) |
|---|---|---|---|---|
| **Core Domain Logic** | Pure Java 21 | Pure Java 17 | Pure Java 17 / 8 | Pure Java 8 |
| **Mod Loader** | Fabric Loader | Fabric Loader | Fabric Loader | MinecraftForge (FML) |
| **Build Tooling** | Fabric Loom 1.17 | Fabric Loom 1.17 | Fabric Loom 1.17 | Java Library / ForgeGradle |
| **Chunk Ticket System** | `TicketType<ChunkPos>` | `TicketType<ChunkPos>` | `TicketType<ChunkPos>` | `ForgeChunkManager` / World Tickets |
| **Entity Lifecycle Hook** | `ServerEntityEvents.ENTITY_LOAD` | `ServerEntityEvents.ENTITY_LOAD` | `ServerEntityEvents.ENTITY_LOAD` | `EntityJoinWorldEvent` (Forge Bus) |
| **Block State Registry** | `BuiltInRegistries.BLOCK` | `BuiltInRegistries.BLOCK` | `Registry.BLOCK` | `GameRegistry.findRegistry(Block.class)` |
| **Command System** | Brigadier Callback | Brigadier Callback | Brigadier Callback | `net.minecraft.command.CommandBase` |

---

## 4. The 3-Tier Multi-Version Implementation Strategy

Because of the radical difference between modern Fabric (1.16–1.21) and legacy Forge (1.12.2), support is organized into three strategic tiers:

### Tier 1: Modern Fabric Line (`1.21.1` & `1.20.1`)
- **Status**: Production (`master`, `ver/1.21.1`, `ver/1.20.1`).
- **Mechanism**: Standard Fabric Loom multi-branching.
- **Domain Compatibility**: 100% shared code; Java 21 on `master`/`1.21.1`, Java 17 on `1.20.1`.
- **Registries**: `BuiltInRegistries.BLOCK` and `BuiltInRegistries.ENTITY_TYPE`.

### Tier 2: Transitional Fabric Line (`1.18.2` & `1.16.5`)
- **Status**: Production (`ver/1.18.2`, `ver/1.16.5`).
- **Mechanism**: Fabric Loom with legacy mapping channels.
- **Platform Adaptation**:
  - `1.18.2`: Java 17, `BuiltInRegistries` resolution.
  - `1.16.5`: Java 17/8, uses `Registry.BLOCK` and `Registry.ENTITY_TYPE` instead of `BuiltInRegistries`.

### Tier 3: Classic Forge Line (`1.12.2`)
- **Status**: Production (`ver/1.12.2-forge`).
- **Mechanism**: Dedicated Forge adapter with reflection-decoupled bridge (`ForgeTicketBridge`).
- **Platform Bridge**: Maps Forge's `EventBus` (`ChunkEvent.Load`, `EntityJoinWorldEvent`) and `ForgeChunkManager.Ticket` to HeapHammer's `PlatformAdapter` interfaces.
- **See**: [docs/FORGE_1_12_2_BRIDGE.md](FORGE_1_12_2_BRIDGE.md) for the complete bridge contract.

---

## 5. The Multi-Version Branching Model

We maintain dedicated release branches for each active Minecraft version line alongside `master`:

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

## 6. Automated Branch Synchronization Workflow

Every commit pushed to `master` triggers the automated synchronization pipeline:
[`.github/workflows/sync-version-branches.yml`](../.github/workflows/sync-version-branches.yml).

### Pipeline Flow:
1. **Trigger**: Push to `master`.
2. **Matrix Execution**: Runs in parallel for each target version branch (`ver/1.21.1`, `ver/1.20.1`, `ver/1.18.2`, `ver/1.16.5`, `ver/1.12.2-forge`).
3. **Merge Attempt**:
   ```bash
   git checkout ver/1.20.1
   git merge --no-ff master -m "chore(sync): automated merge from master"
   ```
4. **Automated Verification**:
   - Compiles and runs tests against the branch's specific `gradle.properties`:
     ```bash
     ./gradlew test
     ```
5. **Outcome Handling**:
   - **Path A (Clean Merge & Tests Pass)**: Pushes the updated version branch automatically.
   - **Path B (Merge Conflict or Test Failure)**:
     - Aborts the automated push.
     - Automatically creates a GitHub Pull Request titled:
       `[Auto-Sync] Merge master into ver/<version>`
     - Applies the label `needs-version-adaptation`.
     - Maintainers review the conflict, adjust platform adapter mappings, and merge.

---

## 7. Accommodating Version Differences

When adding a new version branch or resolving an adaptation PR, follow this protocol:

### Step 1: Branch Creation & `gradle.properties` Configuration
Create the branch from `master` and update the dependency properties:

```properties
# Example for ver/1.20.1
minecraft_version=1.20.1
loader_version=0.15.11
fabric_api_version=0.92.2+1.20.1
java_version=17
```

### Step 2: Adapt Platform Differences

Common Minecraft version divergence points and their resolution patterns:

#### 1. Registry Access (`BuiltInRegistries`)
- **1.21.x / 1.20.x**: `BuiltInRegistries.ENTITY_TYPE` and `BuiltInRegistries.BLOCK` are directly accessible.
- **1.16.5**: Requires `Registry.BLOCK` and `Registry.ENTITY_TYPE`.
- **1.12.2**: Requires `GameRegistry.findRegistry(Block.class)`.

#### 2. Chunk Ticket Registration
- **1.21.x / 1.20.x / 1.18.2 / 1.16.5**:
  ```java
  public static final TicketType<ChunkPos> HEAPHAMMER_TICKET = 
      TicketType.create("heaphammer", Comparator.comparingLong(ChunkPos::toLong));
  ```
  Handled cleanly in [`FabricChunkTicketManager.java`](../src/main/java/com/dwurdy/heaphammer/platform/fabric/FabricChunkTicketManager.java).
- **1.12.2 Forge**: Uses `ForgeChunkManager.requestTicket` and `ForgeChunkManager.forceChunk`.
  Handled in [`ForgeChunkTicketManager.java`](../src/main/java/com/dwurdy/heaphammer/platform/forge/ForgeChunkTicketManager.java).

#### 3. Command Registration API
- **Fabric (1.16–1.21)**: Brigadier registrations in [`HeapHammerCommands.java`](../src/main/java/com/dwurdy/heaphammer/command/HeapHammerCommands.java) rely on `CommandRegistrationCallback.EVENT.register(...)`.
- **Forge (1.12.2)**: `ServerStartingEvent` registering a `CommandBase` child.

### Step 3: Local Offline Verification
Run the local branch verification tool before pushing:
```powershell
powershell -ExecutionPolicy Bypass -File tools/sync-version-branches.ps1
```
This tests building, merging, and running the unit test suite across all configured version branches locally.
