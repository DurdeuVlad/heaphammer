# Multi-Version Expansion Milestone Plan: Popular Minecraft Versions

This document defines the strategic roadmap, outcome-based milestones, and executable issue specifications for extending HeapHammer to additional high-demand Minecraft versions across the modded ecosystem.

---

## 1. Goal, System & Architectural Contract

### 1.1 Strategic Goal
Expand HeapHammer’s version accommodation matrix beyond the initial 5 versions (`1.21.1`, `1.20.1`, `1.18.2`, `1.16.5`, `1.12.2-forge`) to support the highest-population and active modern frontiers of modded Minecraft:
1. **Minecraft 1.21.4 (Modern Active Frontier)**: Latest minor release with Pale Garden and Bundles overhaul.
2. **NeoForge 1.21.x (Modern Ecosystem Loader)**: First-class NeoForge loader support for modern 1.21+ modpacks (e.g. ATM10) that do not run Fabric.
3. **Minecraft 1.19.2 (Modern LTS Bridge)**: Wild Update LTS anchor powering landmark packs (All The Mods 8, Better MC, Create: Astral, Medieval MC).
4. **Minecraft 1.7.10 (Golden Age Classic Titan)**: The enduring classic technical era (GregTech: New Horizons 1.7.10, Thaumcraft 4, Witchery), where servers run 24/7 with massive retained-memory leak exposure.

### 1.2 System & Invariant Guarantees
Every added version line strictly adheres to HeapHammer's Tier 0 Architectural Invariants:
- **Zero-Minecraft Imports in Core Domain**: Core domain (`domain`, `scenario`, `detection`, `reporting`, `storage`, `infrastructure`) remains 100% pure Java with zero `net.minecraft.*` or modloader imports.
- **Zero-Leaked-Reference Invariant**: HeapHammer never retains live object references to `LevelChunk`, `ServerLevel`, `Entity`, or `BlockEntity`. Primitive coordinates (`long` ChunkPos, integer x/y/z, UUIDs) are stored exclusively.
- **Tick Budgeting & Server Safety**: Max operations per tick ($\le 10$) and max milliseconds per tick ($\le 15\text{ ms}$) are enforced without blocking the dedicated server thread.
- **Dedicated Chunk Ticket Isolation**: Only manipulate HeapHammer's dedicated `TicketType<ChunkPos>` or `ForgeChunkManager.Ticket`. Never touch player or spawn tickets. Leftover tickets constitute a test failure (`FAIL`).
- **Statistical OLS Regression**: All detection continues to rely on Ordinary Least Squares ($y = mx + b$) trend slope and plateau pattern detection vs GC noise.

---

## 2. Version Ecosystem Landscape & Accommodation Matrix

```text
Modern Cutting-Edge        Modern LTS Hub            Modern LTS Overhaul       Nether Bridge          Classic Titan          Golden Age Legend
     [1.21.4] ───────────────> [1.20.1] ─────────────────> [1.19.2] ─────────────> [1.18.2] ────────────> [1.16.5] ────────────> [1.12.2] ────────────> [1.7.10]
     Java 21                   Java 17                     Java 17                  Java 17               Java 17/8              Java 8                 Java 8
     Fabric & NeoForge         Fabric & Forge              Fabric & Forge           Fabric & Forge        Fabric & Forge         MinecraftForge         MinecraftForge / Cleanroom
```

| Target Version | Mod Loader(s) | JVM Target | Modding Role & Community Value | Primary Technical Delta |
|---|---|---|---|---|
| **1.21.4** | Fabric, NeoForge | Java 21 | **Active Modern Frontier.** Primary release for modern mods adopting Data Components and modern Mojmap. | Data Component registry shifts, Fabric API 0.110+, Loom 1.17+. |
| **NeoForge 1.21.x** | NeoForge | Java 21 | **Modern Heavy Modpacks.** Dominant loader on 1.21+ for tech/magic packs (ATM10). | EventBus registration, `RegisterCommandsEvent`, NeoForge ticket wrapper. |
| **1.19.2** | Fabric, Forge | Java 17 | **Modern LTS Anchor.** Highest modpack population between 1.18 and 1.20 (ATM8, Create: Astral). | Mojmap standardization, 384-block world height ($Y=-64..320$), Fabric API 0.77. |
| **1.7.10** | MinecraftForge | Java 8 | **Classic Golden Era Titan.** Massive tech packs (GT:NH 1.7.10, TC4). 24/7 server memory leaks. | Pre-flattening block IDs, pre-`BlockPos` (x,y,z), `ForgeChunkManager.Ticket`, `CommandBase`. |

---

## 3. Milestone Decomposition

### Milestone M7: Modern Frontier & NeoForge Hub (Minecraft 1.21.4 & NeoForge 1.21.x)
- **Outcome**: Deliver official HeapHammer releases for the latest Minecraft 1.21.4 release line, and introduce the `NeoForgePlatformAdapter` for modern 1.21.x servers.
- **Scope Boundary**: Branch `ver/1.21.4`, `com.dwurdy.heaphammer.platform.neoforge` package, dual-loader packaging.
- **Dependencies**: M1 (Turnkey Jenkins CI/CD), M2 (`ver/1.20.1` baseline).
- **Residual Risks**: NeoForge and Fabric API rapid churn during 1.21 minor releases.

### Milestone M8: Modern LTS Bridge (Minecraft 1.19.2 Fabric & Forge)
- **Outcome**: Close the gap between 1.18.2 and 1.20.1 by providing native HeapHammer binaries for Minecraft 1.19.2.
- **Scope Boundary**: Branch `ver/1.19.2`, Fabric API `0.77.0+1.19.2`, Forge `43.4.x` bridge, automated CI branch discovery.
- **Dependencies**: M1, M2.
- **Residual Risks**: Registry accessor variations between 1.19.2 and 1.19.4.

### Milestone M9: Golden Age Classic Titan (Minecraft 1.7.10 Forge & CleanroomMC)
- **Outcome**: Provide a deterministic retained-memory regression framework for 1.7.10 Forge and CleanroomMC servers.
- **Scope Boundary**: Branch `ver/1.7.10-forge`, `ForgePlatformAdapter1710`, `ForgeChunkTicketManager1710`, RetroFuturaGradle build pipeline.
- **Dependencies**: M3 (`ver/1.12.2-forge` legacy bridge architecture).
- **Residual Risks**: Gradle 4.x / 8.x RetroFuturaGradle toolchain differences on modern JDKs.

---

## 4. Executable Issue Specifications

```
ISSUE 1: [1.21.4] Implement Minecraft 1.21.4 Fabric Platform Support and Modern Registry Alignment
ISSUE 2: [1.21.x-NeoForge] Implement NeoForge 1.21.x Platform Adapter and Command Event Bridge
ISSUE 3: [1.19.2] Implement Minecraft 1.19.2 Modern LTS Release Line and CI Pipeline Integration
ISSUE 4: [1.7.10-Forge] Implement Golden Age 1.7.10 Forge Platform Adapter and Legacy Chunk Ticket Bridge
```

---

### Issue 1: [1.21.4] Implement Minecraft 1.21.4 Fabric Platform Support and Modern Registry Alignment

#### 1. Strategic Intent & Milestone Placement
- **Milestone Placement**: Milestone M7 (Modern Frontier Expansion).
- **Strategic Intent**: Minecraft 1.21.4 is the active production release of Java Edition. Modpack creators and server administrators updating from 1.21.1 require native HeapHammer binaries to catch memory regressions and chunk caching leaks during modern pack migration.

#### 2. Expected Behavior & System Outcomes
- Branch `ver/1.21.4` compiles cleanly on Java 21 against Minecraft 1.21.4 and Fabric API `0.110.0+1.21.4`.
- Server administrators can install `heaphammer-1.21.4-1.0.0.jar` into a 1.21.4 dedicated server and run `/hh run chunks` with OP Level 2.
- Chunk loading, ticket allocation via `TicketType<ChunkPos>`, and unloading callbacks execute without reflection errors or deprecation warnings.
- All 34 pure-domain invariant tests pass with 100% success rate.

#### 3. Context That Code Cannot Infer
- Minecraft 1.21.4 introduced refined Data Component registrations and subtle internal changes to chunk status transitions (`ChunkStatus.FULL`).
- Fabric Loom 1.17+ and Gradle 9.5+ must execute with `JDK21`.
- The branch will be synchronized automatically via `.github/workflows/sync-version-branches.yml` and built by the local/remote Jenkins multibranch pipeline.

#### 4. Scope & Non-Goals
- **In Scope**:
  - Creation of branch `ver/1.21.4`.
  - Updating `gradle.properties`: `minecraft_version=1.21.4`, `fabric_version=0.110.0+1.21.4`, `loader_version=0.16.9`, `java_version=21`.
  - Updating `FabricPlatformAdapter` and `FabricChunkTicketManager` if 1.21.4 Mojmap changes require method signature updates.
  - Adding `ver/1.21.4` to CI/CD workflows and documentation.
- **Non-Goals**:
  - Do not alter pure-domain math or scenario executors.
  - Do not port client-side renderers or GUIs (HeapHammer is 100% server-side).

#### 5. Expected Agent Responsibilities
1. Branch `ver/1.21.4` from `master`.
2. Update `gradle.properties` to target Minecraft `1.21.4` and latest compatible Fabric API.
3. Validate and adapt `FabricPlatformAdapter` and `FabricChunkTicketManager` to resolve any 1.21.4 Mojmap symbol updates.
4. Run `./gradlew check test build buildTestmods --no-daemon` and confirm exit code `0`.
5. Update `.github/workflows/sync-version-branches.yml`, `tools/sync-version-branches.ps1`, and `docs/MULTI_VERSION_ARCHITECTURE.md`.
6. Open PR targeting `master` (for documentation/sync updates) and push `ver/1.21.4`.

#### 6. Explicit Anti-Assumptions (What the Agent MUST NOT Infer)
- **MUST NOT** import `net.minecraft.*` into `com.dwurdy.heaphammer.domain.*`.
- **MUST NOT** retire or overwrite `master` (which currently tracks 1.21.1 LTS).
- **MUST NOT** bump mod version beyond `1.0.0`.

#### 7. Exact File Boundaries
- `[NEW]` Branch: `ver/1.21.4`
- `[MODIFY]` `gradle.properties` (on `ver/1.21.4`)
- `[MODIFY]` `src/main/resources/fabric.mod.json` (on `ver/1.21.4`)
- `[MODIFY]` `src/main/java/com/dwurdy/heaphammer/platform/fabric/FabricPlatformAdapter.java` (if symbol changes exist)
- `[MODIFY]` `.github/workflows/sync-version-branches.yml` (on `master`)
- `[MODIFY]` `tools/sync-version-branches.ps1` (on `master`)
- `[MODIFY]` `docs/MULTI_VERSION_ARCHITECTURE.md` (on `master`)

#### 8. Measurable Acceptance Criteria & Test Surfaces
- Automated Test Command:
  ```powershell
  ./gradlew test --no-daemon
  ```
  *Signal*: All 34 tests pass with exit code `0`.
- Build Packaging Command:
  ```powershell
  ./gradlew build buildTestmods --no-daemon
  ```
  *Signal*: Produces `build/libs/heaphammer-1.21.4-1.0.0.jar` and testmod JARs.
- Dedicated Server Health Check:
  Execute `/hh status` and `/hh run chunks --iterations=3 --batch=5` on 1.21.4 dedicated server; zero crash reports, tickets cleanly unforced.

---

### Issue 2: [1.21.x-NeoForge] Implement NeoForge 1.21.x Platform Adapter and Command Event Bridge

#### 1. Strategic Intent & Milestone Placement
- **Milestone Placement**: Milestone M7 (Modern Frontier Expansion).
- **Strategic Intent**: On Minecraft 1.20.4+ and 1.21+, the majority of technical, magic, and heavy modpacks (e.g. All The Mods 10) have standardized on **NeoForge** rather than Fabric or legacy Forge. Providing a native NeoForge platform adapter ensures HeapHammer can stress-test any modern server without loader exclusivity.

#### 2. Expected Behavior & System Outcomes
- A dedicated NeoForge module or adapter (`com.dwurdy.heaphammer.platform.neoforge`) implements `PlatformAdapter` and `ChunkTicketManager`.
- The mod registers using NeoForge’s `@Mod("heaphammer")` annotation and subscribes to `RegisterCommandsEvent` on the `NeoForge.EVENT_BUS`.
- Server operators on NeoForge 1.21.1 / 1.21.4 can execute `/hh` commands with OP level 2.
- Chunk tickets are acquired and released cleanly via NeoForge's `ServerLevel` ticket accessors.

#### 3. Context That Code Cannot Infer
- NeoForge is a distinct loader with its own package namespace (`net.neoforged.neoforge.*`, `net.neoforged.bus.api.*`).
- Unlike Fabric's `CommandRegistrationCallback`, NeoForge registers commands during `RegisterCommandsEvent`.
- Chunk ticket types in NeoForge 1.21 use `TicketType.create(...)` registered in mod initialization.

#### 4. Scope & Non-Goals
- **In Scope**:
  - Implementing `NeoForgePlatformAdapter` and `NeoForgeChunkTicketManager`.
  - Creating `NeoForgeHeapHammerMod` entrypoint with `@Mod("heaphammer")`.
  - Registering Brigadier commands on `RegisterCommandsEvent`.
  - Ensuring tick budget executor hooks into `ServerTickEvent.Post`.
- **Non-Goals**:
  - Do not introduce client-only event subscribers.
  - Do not modify core scenario or math classes.

#### 5. Expected Agent Responsibilities
1. Design `com.dwurdy.heaphammer.platform.neoforge` package adhering strictly to `PlatformAdapter` and `ChunkTicketManager` interfaces.
2. Implement command registration bridging `HeapHammerCommands.register(...)` to `RegisterCommandsEvent.getDispatcher()`.
3. Wire server tick scheduling to `ServerTickEvent.Post`.
4. Provide unit test verifying adapter lifecycle and ticket isolation.
5. Provide Gradle configuration (e.g. multi-project or ModDevGradle) to package `heaphammer-neoforge-1.21.1-1.0.0.jar`.

#### 6. Explicit Anti-Assumptions (What the Agent MUST NOT Infer)
- **MUST NOT** mix Fabric Loader and NeoForge imports in the same class.
- **MUST NOT** alter the pure-domain `PlatformAdapter` contract to accommodate loader-specific idioms.
- **MUST NOT** retain hard references to `LevelChunk` inside NeoForge event subscribers.

#### 7. Exact File Boundaries
- `[NEW]` `src/main/java/com/dwurdy/heaphammer/platform/neoforge/NeoForgePlatformAdapter.java`
- `[NEW]` `src/main/java/com/dwurdy/heaphammer/platform/neoforge/NeoForgeChunkTicketManager.java`
- `[NEW]` `src/main/java/com/dwurdy/heaphammer/platform/neoforge/NeoForgeHeapHammer.java`
- `[NEW]` `src/test/java/com/dwurdy/heaphammer/platform/neoforge/NeoForgePlatformAdapterTest.java`
- `[MODIFY]` `build.gradle` (or multi-loader build setup)
- `[MODIFY]` `docs/MULTI_VERSION_ARCHITECTURE.md`

#### 8. Measurable Acceptance Criteria & Test Surfaces
- Automated Test Command:
  ```powershell
  ./gradlew test --tests "*NeoForge*" --no-daemon
  ```
  *Signal*: All NeoForge adapter unit tests pass with exit code `0`.
- Server Verification:
  Deploy JAR on NeoForge 1.21.1 dedicated server. Confirm `/hh status` returns version `1.0.0` and loader `NeoForge`.

---

### Issue 3: [1.19.2] Implement Minecraft 1.19.2 Modern LTS Release Line and CI Pipeline Integration

#### 1. Strategic Intent & Milestone Placement
- **Milestone Placement**: Milestone M8 (Modern LTS Bridge).
- **Strategic Intent**: Minecraft 1.19.2 is one of the most widely deployed modern LTS modpack versions (ATM8, Medieval MC, Better MC 1.19.2, Create: Astral). Server admins on 1.19.2 currently have no HeapHammer binary to diagnose 24-hour player exploration leaks.

#### 2. Expected Behavior & System Outcomes
- Branch `ver/1.19.2` compiles cleanly on Java 17 targeting Minecraft 1.19.2.
- Fabric and Forge 1.19.2 dedicated servers can run HeapHammer without bytecode errors.
- Automated CI pipeline (Jenkins and GitHub Actions) discovers `ver/1.19.2` and executes test verification automatically.

#### 3. Context That Code Cannot Infer
- Minecraft 1.19.2 uses Mojang mappings and Java 17.
- World height is 384 blocks ($Y=-64$ to $320$), identical to 1.18.2 and 1.20.1.
- Gradle build daemon requires Java 21 to run Gradle 9.5 and Loom 1.17, while target bytecode is Java 17.

#### 4. Scope & Non-Goals
- **In Scope**:
  - Creation of branch `ver/1.19.2`.
  - `gradle.properties`: `minecraft_version=1.19.2`, `fabric_version=0.77.0+1.19.2`, `loader_version=0.15.11`, `java_version=17`.
  - Inclusion of `ver/1.19.2` in `Jenkinsfile`, `sync-version-branches.yml`, and `tools/sync-version-branches.ps1`.
- **Non-Goals**:
  - Do not introduce 1.19.4-specific features (e.g. display entities or interaction entities).

#### 5. Expected Agent Responsibilities
1. Create branch `ver/1.19.2` from `ver/1.20.1`.
2. Configure `gradle.properties` and `fabric.mod.json` for Minecraft 1.19.2.
3. Validate `FabricPlatformAdapter` compatibility against Fabric API `0.77.0+1.19.2`.
4. Run `./gradlew check test build buildTestmods --no-daemon` and ensure exit code `0`.
5. Update sync scripts and Jenkins multibranch discovery.

#### 6. Explicit Anti-Assumptions (What the Agent MUST NOT Infer)
- **MUST NOT** lower Java compatibility below Java 17 for 1.19.2.
- **MUST NOT** break automated merge compatibility with `master`.

#### 7. Exact File Boundaries
- `[NEW]` Branch: `ver/1.19.2`
- `[MODIFY]` `gradle.properties` (on `ver/1.19.2`)
- `[MODIFY]` `src/main/resources/fabric.mod.json` (on `ver/1.19.2`)
- `[MODIFY]` `.github/workflows/sync-version-branches.yml` (on `master`)
- `[MODIFY]` `tools/sync-version-branches.ps1` (on `master`)
- `[MODIFY]` `docs/MULTI_VERSION_ARCHITECTURE.md` (on `master`)

#### 8. Measurable Acceptance Criteria & Test Surfaces
- Automated Test Command:
  ```powershell
  ./gradlew test --no-daemon
  ```
  *Signal*: All 34 tests pass with exit code `0`.
- Packaging Command:
  ```powershell
  ./gradlew build buildTestmods --no-daemon
  ```
  *Signal*: Produces `build/libs/heaphammer-1.19.2-1.0.0.jar`.
- Jenkins Multi-Branch Verification:
  Scan discovers `HeapHammer/ver%2F1.19.2` and executes build to `SUCCESS`.

---

### Issue 4: [1.7.10-Forge] Implement Golden Age 1.7.10 Forge Platform Adapter and Legacy Chunk Ticket Bridge

#### 1. Strategic Intent & Milestone Placement
- **Milestone Placement**: Milestone M9 (Golden Age Classic Titan).
- **Strategic Intent**: 1.7.10 is the immortal golden era of technical modding (GregTech: New Horizons 1.7.10, Thaumcraft 4, Witchery). Servers run for months continuously with hundreds of complex automation mods. Retained-memory leaks are rampant, yet no modern regression framework exists for 1.7.10.

#### 2. Expected Behavior & System Outcomes
- Branch `ver/1.7.10-forge` compiles pure domain code against Java 8 and legacy FML 7.10 / CleanroomMC APIs.
- `ForgePlatformAdapter1710` implements `PlatformAdapter` using pre-flattening Minecraft APIs (`int x, int y, int z`, `Block`, `TileEntity`).
- `ForgeChunkTicketManager1710` manages `ForgeChunkManager.Ticket` instances, forcing and unforcing chunk coordinates cleanly.
- Legacy command `/hh` registered via `CommandBase` and `ServerCommandManager`.
- Server operators on 1.7.10 dedicated servers can run `/hh run chunks` and obtain full OLS slope diagnostics.

#### 3. Context That Code Cannot Infer
- Minecraft 1.7.10 does not have `BlockPos` (introduced in 1.8); coordinates are primitive `int x, int y, int z`.
- Brigadier does not exist in 1.7.10; commands inherit from `net.minecraft.command.CommandBase`.
- Text components use `net.minecraft.util.ChatComponentText` rather than modern `Component.literal(...)`.
- `ForgeChunkManager.Ticket` requires a mod instance and a `ForgeChunkManager.Type` (`NORMAL`).
- Modern builds use RetroFuturaGradle (RFG) or CleanroomMC modern Gradle toolchains to compile on Java 8/17.

#### 4. Scope & Non-Goals
- **In Scope**:
  - Branch `ver/1.7.10-forge`.
  - `ForgePlatformAdapter1710` and `ForgeChunkTicketManager1710`.
  - `CommandHeapHammer1710` extending `CommandBase`.
  - Java 8 bytecode compatibility for pure domain and adapter classes.
  - Dedicated unit tests for legacy ticket force/unforce cycles.
- **Non-Goals**:
  - Do not backport modern client rendering or particle effects.
  - Do not modify core hexagonal interfaces (`PlatformAdapter`, `ChunkTicketManager`).

#### 5. Expected Agent Responsibilities
1. Initialize `ver/1.7.10-forge` branch based on the architecture of `ver/1.12.2-forge`.
2. Configure RetroFuturaGradle build pipeline targeting Minecraft `1.7.10` and Forge `10.13.4.1614`.
3. Implement `ForgePlatformAdapter1710` with primitive coordinate translation.
4. Implement `ForgeChunkTicketManager1710` utilizing `ForgeChunkManager.forceChunk(...)` and `ForgeChunkManager.unforceChunk(...)`.
5. Implement `CommandHeapHammer1710` with argument parser matching `/hh` syntax.
6. Verify all 34 pure-domain invariant tests compile and pass on Java 8.

#### 6. Explicit Anti-Assumptions (What the Agent MUST NOT Infer)
- **MUST NOT** introduce modern `BlockPos` or `ResourceLocation` imports into pure-domain interfaces.
- **MUST NOT** retain live `Chunk` or `World` references in `ForgeChunkTicketManager1710`.
- **MUST NOT** compromise tick budgeting: enforce $\le 10$ chunk tickets per tick even on legacy servers.

#### 7. Exact File Boundaries
- `[NEW]` Branch: `ver/1.7.10-forge`
- `[NEW]` `src/main/java/com/dwurdy/heaphammer/platform/forge1710/ForgePlatformAdapter1710.java`
- `[NEW]` `src/main/java/com/dwurdy/heaphammer/platform/forge1710/ForgeChunkTicketManager1710.java`
- `[NEW]` `src/main/java/com/dwurdy/heaphammer/platform/forge1710/CommandHeapHammer1710.java`
- `[NEW]` `src/test/java/com/dwurdy/heaphammer/platform/forge1710/ForgePlatformAdapter1710Test.java`
- `[MODIFY]` `gradle.properties` (on `ver/1.7.10-forge`)
- `[MODIFY]` `build.gradle` (on `ver/1.7.10-forge`)
- `[MODIFY]` `docs/MULTI_VERSION_ARCHITECTURE.md` (on `master`)

#### 8. Measurable Acceptance Criteria & Test Surfaces
- Automated Test Command:
  ```powershell
  ./gradlew test --no-daemon
  ```
  *Signal*: All pure-domain invariant tests pass with exit code `0`.
- Live Server Test:
  Run `heaphammer-1.7.10-1.0.0.jar` on a clean 1.7.10 Forge server; execute `/hh run chunks --iterations=3 --batch=5`; verify zero tickets remain forced in `ForgeChunkManager.getPersistentChunksFor(...)`.

---

## 5. Pull-Request Contract & Verification Gates

Every pull request implementing these milestone issues must include the following standard contract:
1. **Intent**: Problem solved, target Minecraft version, loader ecosystem, and admin benefits.
2. **Expectation**: Observable behavior on a dedicated server (commands, ticket lifecycle, memory diagnostics).
3. **Acceptance Criteria**: Checkbox list mapped to empirical verification evidence.
4. **Non-Code Context**: Version era constraints, JVM compatibility (Java 21 / 17 / 8), and modpack demographics.
5. **Scope & Non-Goals**: File boundaries and explicit negative constraints.
6. **Verification & Risk**: Exact `./gradlew test` output, live server execution log, zero leftover tickets proof, and AI disclosure notice.
