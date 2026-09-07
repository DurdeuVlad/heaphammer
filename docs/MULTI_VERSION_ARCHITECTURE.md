# Multi-Version Minecraft Architecture & Accommodation Guide

HeapHammer supports 5 major Minecraft version lines (`1.21.1` down to `1.12.2`) while maintaining a single, unified source of truth for its core domain, deterministic scenario planning, and statistical regression engines.

---

## 1. Modding Ecosystem & Version Matrix

Community adoption concentrates in distinct Minecraft eras:

```text
Modern Cutting-Edge      Modern Gold Standard      World-Gen Overhaul       Legacy Bridge          Classic Titan
     [1.21.1] ───────────────> [1.20.1] ───────────────> [1.18.2] ────────────> [1.16.5] ────────────> [1.12.2]
     Java 21                   Java 17                   Java 17                Java 17/8              Java 8
     Fabric/NeoForge           Fabric/Forge              Fabric/Forge           Fabric/Forge           MinecraftForge
```

| Version | Ecosystem Role | JVM | Mod Loaders | Primary Challenge |
|---|---|---|---|---|
| **1.21.1** | **Active Frontier** | Java 21 | Fabric, NeoForge | Rapid mod API churn. Primary development trunk (`master`). |
| **1.20.1** | **Modern Gold Standard** | Java 17 | Fabric, Forge | **Highest modpack population.** 300+ modpacks create high cross-mod collision risks. |
| **1.18.2** | **World-Gen Overhaul** | Java 17 | Fabric, Forge | 384-block world height ($Y=-64$ to $320$), high chunk memory pressure. |
| **1.16.5** | **Pre-Caves Bridge** | Java 17/8 | Fabric, Forge | Transitional registry access before modern Mojmap standardization. |
| **1.12.2** | **Classic Titan** | Java 8 | MinecraftForge | **Massive technical packs** (GT:NH, SevTech). Legacy ticket and event bus models. |

---

## 2. Hexagonal Architecture: The Portability Boundary

To eliminate version migration friction, HeapHammer strictly decouples core logic from Minecraft runtime classes:

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
│   ZERO net.minecraft.*  │  ZERO net.fabricmc.*  │  100% Pure Java      │
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
- **95% of the codebase** has **zero** Minecraft or mod loader imports.
- Any improvement to scenario planners, OLS slope math, or JSON reporting is **100% binary-compatible** across all 5 Minecraft version branches.
- Version-specific logic is strictly quarantined inside `com.dwurdy.heaphammer.platform.fabric` or `com.dwurdy.heaphammer.platform.forge`.

---

## 3. Subsystem Compatibility Matrix

| Architectural Subsystem | Modern (1.21.x / 1.20.1) | World-Gen (1.18.2) | Legacy (1.16.5) | Classic Titan (1.12.2) |
|---|---|---|---|---|
| **JVM Target** | Java 21 / Java 17 | Java 17 | Java 17 / 8 | Java 8 |
| **Loader** | Fabric Loader | Fabric Loader | Fabric Loader | MinecraftForge (FML) |
| **Chunk Tickets** | `TicketType<ChunkPos>` | `TicketType<ChunkPos>` | `TicketType<ChunkPos>` | `ForgeChunkManager.Ticket` |
| **Entity Lifecycle** | `ServerEntityEvents` | `ServerEntityEvents` | `ServerEntityEvents` | `EntityJoinWorldEvent` |
| **Block Registry** | `BuiltInRegistries.BLOCK`| `BuiltInRegistries.BLOCK`| `Registry.BLOCK` | `GameRegistry.findRegistry` |
| **Commands** | Brigadier Callback | Brigadier Callback | Brigadier Callback | `CommandBase` (FML) |

---

## 4. Multi-Version Branching Strategy

HeapHammer maintains a single source of truth for all pure-domain logic on `master` and propagates updates to supported historical Minecraft version branches:

```text
master (Active Production Trunk — 1.21.1 Fabric, Java 21)
  │
  ├──> release/v*        (Batched release staging — gathers work before official tagging)
  │
  ├──> ver/1.20.1        (Modern LTS Fabric/Forge — Java 17)
  ├──> ver/1.18.2        (World-Gen LTS Fabric/Forge — Java 17)
  ├──> ver/1.16.5        (Nether Legacy LTS Fabric/Forge — Java 17/8)
  └──> ver/1.12.2-forge  (Classic Titan LTS Forge — Java 8)
```

### Branch Policy Rules
1. **`master` as Production Trunk**: The `master` branch directly targets the latest production Minecraft version (`1.21.1`). To avoid duplicate builds and maintenance overhead, no separate `ver/1.21.1` branch is maintained.
2. **Historical LTS-Only Branches**: Dedicated `ver/*` branches are maintained exclusively for historical versions that are officially designated **LTS** or actively supported. Non-LTS intermediate versions (e.g. `1.20.4`, `1.19.4`) do not receive branches.
3. **Mod Version Line Branches**: Release staging lines (`release/v*`) gather batched releases before tagging. Only officially designated LTS mod releases receive long-term maintenance branches.

### Automated Branch Synchronization
Every push to `master` triggers [`.github/workflows/sync-version-branches.yml`](../.github/workflows/sync-version-branches.yml):
1. Merges `master` into each historical `ver/*` branch (`ver/1.20.1`, `ver/1.18.2`, `ver/1.16.5`, `ver/1.12.2-forge`).
2. Executes `./gradlew test` with the branch's specific JVM target.
3. If clean $\rightarrow$ pushes automatically.
4. If conflict/adaptation required $\rightarrow$ automatically opens a PR labeled `needs-version-adaptation`.

---

## 5. Version Adaptation Recipes

When implementing a new platform branch or resolving an adaptation PR:

### 1. Registry Resolution
- **1.20.x – 1.21.x**: Use `BuiltInRegistries.BLOCK` and `BuiltInRegistries.ENTITY_TYPE`.
- **1.16.5**: Use `Registry.BLOCK` and `Registry.ENTITY_TYPE`.
- **1.12.2**: Use `GameRegistry.findRegistry(Block.class)`.

### 2. Chunk Ticket Management
- **Modern Fabric (1.16–1.21)**:
  ```java
  public static final TicketType<ChunkPos> HEAPHAMMER_TICKET =
      TicketType.create("heaphammer", Comparator.comparingLong(ChunkPos::toLong));
  ```
- **Classic Forge (1.12.2)**:
  Uses reflection-decoupled `ForgeChunkManager.requestTicket` via [`ForgeTicketBridge`](FORGE_1_12_2_BRIDGE.md).

### 3. Local Multi-Branch Verification
Test synchronization locally across all branches before pushing:
```powershell
powershell -ExecutionPolicy Bypass -File tools/sync-version-branches.ps1
```
