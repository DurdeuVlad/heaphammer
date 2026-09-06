# Minecraft Multi-Version Support Roadmap (1.21.1 down to 1.12.2)

This document establishes the empirical research, modding era analysis, architectural feasibility, and implementation milestones for extending HeapHammer from modern Minecraft 1.21.1 back to classic 1.12.2.

---

## 1. Modding Ecosystem & Version Popularity Analysis

In modded Minecraft, community adoption is concentrated in distinct **modding eras** driven by API stability, mod availability, and world-generation changes.

Based on global modding metrics (Modrinth & CurseForge modpack downloads, active server populations), the versions selected for HeapHammer support are:

```text
2026                 2023–2024               2021–2022              2020–2021               2017–2019
Modern Cutting-Edge  Modern Gold Standard    World-Gen Overhaul     Nether Bridge Era       Classic Titan
     [1.21.1] ───────> [1.20.1] ───────────> [1.18.2] ────────────> [1.16.5] ─────────────> [1.12.2]
     Java 21           Java 17                Java 17                Java 8 / 16             Java 8
     Fabric/NeoForge   Fabric/Forge           Fabric/Forge           Fabric/Forge            Forge Only
```

### The 5 Target Anchor Versions

| Version | Role in Ecosystem | JVM Runtime | Mod Loaders | Why HeapHammer is Essential Here |
|---|---|---|---|---|
| **1.21.1** | **Active Frontier** | Java 21 | Fabric, NeoForge | High mod churn; newest optimizations (Lithium, FerriteCore) and latest Vanilla mechanics. |
| **1.20.1** | **The Modern Gold Standard** | Java 17 | Fabric, Forge | **Highest current modpack player count.** Major tech mods (Mekanism, AE2, Create, Botania) are in peak production. Heavy memory leak surface from large 300+ modpacks. |
| **1.18.2** | **World-Gen Overhaul** | Java 17 | Fabric, Forge | 384-block world height ($Y=-64$ to $320$), new chunk format, heavy chunk generation/unloading memory pressure. |
| **1.16.5** | **Pre-Caves Bridge** | Java 8 / 16 | Fabric, Forge | Highly stable long-term modpacks; transition point before 1.17+ architectural shifts. |
| **1.12.2** | **The Classic Titan** | Java 8 | MinecraftForge | **Legendary technical mega-packs** (GT: New Horizons, SevTech, Enigmatica 2). Long-running servers suffer massive retention leaks. |

---

## 2. Architectural Feasibility & Compatibility Matrix

| Architectural Subsystem | Modern (1.21.x) | Modern LTS (1.20.1, 1.18.2) | Legacy Bridge (1.16.5) | Classic Titan (1.12.2) |
|---|---|---|---|---|
| **Core Domain Logic** | Pure Java 21 | Pure Java 17 | Pure Java 8 / 11 | Pure Java 8 |
| **Mod Loader** | Fabric Loader | Fabric Loader | Fabric Loader | MinecraftForge (FML) |
| **Build Tooling** | Fabric Loom 1.17 | Fabric Loom 1.17 | Fabric Loom 1.5+ | ForgeGradle 2.3 / Cleanroom Loom |
| **Chunk Ticket System** | `TicketType<ChunkPos>` | `TicketType<ChunkPos>` | `TicketType<ChunkPos>` | `ForgeChunkManager` / World Tickets |
| **Entity Lifecycle Hook** | `ServerEntityEvents.ENTITY_LOAD` | `ServerEntityEvents.ENTITY_LOAD` | `ServerEntityEvents.ENTITY_LOAD` | `EntityJoinWorldEvent` (Forge Bus) |
| **Block State Registry** | `BuiltInRegistries.BLOCK` | `BuiltInRegistries.BLOCK` | `Registry.BLOCK` | `GameRegistry.findRegistry(Block.class)` |

---

## 3. The 3-Tier Multi-Version Implementation Strategy

Because of the radical difference between modern Fabric (1.16–1.21) and legacy Forge (1.12.2), we organize support into three strategic tiers:

### Tier 1: Modern Fabric Line (`1.21.1` & `1.20.1` & `1.19.2`)
- **Status**: `1.21.1` Complete; `1.20.1` Active.
- **Mechanism**: Standard Fabric Loom multi-branching (`ver/1.21.1`, `ver/1.20.1`).
- **Domain Compatibility**: 100% shared code; Java 17 bytecode compatibility.

### Tier 2: Transitional Fabric Line (`1.18.2` & `1.16.5`)
- **Status**: Planned.
- **Mechanism**: Fabric Loom with legacy mapping channels (Mojang mappings for 1.18.2, Yarn or Intermediary for 1.16.5).
- **Adaptation**: `PlatformAdapter` accommodates `Registry.BLOCK` instead of `BuiltInRegistries`.

### Tier 3: Classic Forge Line (`1.12.2`)
- **Status**: Planned (Dedicated Subproject / Branch `ver/1.12.2-forge`).
- **Mechanism**: ForgeGradle or CleanroomMC Loom with MCP mappings.
- **Platform Bridge**: `ForgePlatformAdapter` maps Forge's `EventBus` (`ChunkEvent.Load`, `EntityJoinWorldEvent`) to HeapHammer's `PlatformAdapter` interfaces.

---

## 4. Multi-Version Jenkins & CI Strategy

All branches are compiled and tested automatically in Jenkins and GitHub Actions:

```text
Jenkins Multibranch Pipeline
  ├── master        -> JDK 21 -> ./gradlew test -> Artifact: heaphammer-1.21.1.jar
  ├── ver/1.21.1    -> JDK 21 -> ./gradlew test -> Artifact: heaphammer-1.21.1.jar
  ├── ver/1.20.1    -> JDK 17 -> ./gradlew test -> Artifact: heaphammer-1.20.1.jar
  ├── ver/1.18.2    -> JDK 17 -> ./gradlew test -> Artifact: heaphammer-1.18.2.jar
  ├── ver/1.16.5    -> JDK 8  -> ./gradlew test -> Artifact: heaphammer-1.16.5.jar
  └── ver/1.12.2    -> JDK 8  -> ./gradlew test -> Artifact: heaphammer-1.12.2.jar
```
