# Project Milestones Ledger

This document defines the outcome-based checkpoints for the HeapHammer project. Each milestone represents a verifiable capability or architectural release horizon.

---

## Milestone Status Overview

| Milestone | Target Horizon | Capability Outcome | Verification Surface | Status |
|---|---|---|---|---|
| **M1: CI/CD & Build Automation** | Release 1.0.0-alpha.1 | Declarative multi-version Jenkins & GitHub Actions build matrix | Jenkinsfile lint, multi-branch detection | **Completed & Verified** |
| **M2: Modern LTS Expansion** | Release 1.0.0-alpha.1 | 1.20.1 & 1.18.2 Fabric release lines | `./gradlew test` passes 100% on 1.20.1 & 1.18.2 | **Completed & Verified** |
| **M3: Legacy Bridge & Classic Titan** | Release 1.0.0-alpha.1 | 1.16.5 & 1.12.2 Forge release lines | `./gradlew test` passes 100% on 1.16.5 & 1.12.2 Forge | **Completed & Verified** |
| **M4: Entity & Block Entity Churn** | Release 1.1.0 | Deterministic entity & tile entity lifecycle stress scenarios | Live dedicated server stress matrix | **Active Specification** |
| **M5: Registry Coverage Sampling** | Release 1.2.0 | Namespace targeting (`/hh target <modid>`) | Registry iteration & filter test suite | **Planned Horizon** |
| **M6: Third-Party Workload SPI** | Release 2.0.0 | Workload Adapter SPI for external mod authors | SPI contract test suite | **Future Exploration** |
| **M7: Modern Frontier (1.21.4 & NeoForge)** | Release 1.3.0 | 1.21.4 Fabric & NeoForge 1.21.x loader adapter | `./gradlew test`, NeoForge server launch | **Planned Specification** |
| **M8: Modern LTS Bridge (1.19.2)** | Release 1.3.0 | 1.19.2 Fabric & Forge release line | `./gradlew test`, 1.19.2 dedicated server | **Planned Specification** |
| **M9: Golden Age Titan (1.7.10)** | Release 1.4.0 | 1.7.10 Forge & CleanroomMC legacy adapter | `./gradlew test`, 1.7.10 server ticket churn | **Planned Specification** |

---

## Completed Milestones

### Milestone M1: Multi-Version Build Automation & Jenkins CI/CD
- **Intended Outcome**: Enable automated compilation, unit testing, and artifact packaging across all target Minecraft versions and JDKs without manual environment toggling.
- **Scope Boundary**: Root `Jenkinsfile` and GitHub Actions workflow [`.github/workflows/ci.yml`](../.github/workflows/ci.yml).
- **Dependencies**: None.
- **Acceptance Evidence**:
  - Declarative `Jenkinsfile` dynamically detects `java_version` from `gradle.properties` and binds appropriate JDK (`JDK21`, `JDK17`, `JDK8`).
  - Unit tests run and produce JUnit XML reports archived automatically.
  - Multi-version synchronization workflow configured in [`.github/workflows/sync-version-branches.yml`](../.github/workflows/sync-version-branches.yml).
- **Documentation**: [docs/JENKINS_PIPELINE.md](../docs/JENKINS_PIPELINE.md).
- **Status**: **Completed & Verified** (Merged into `master`).

---

### Milestone M2: Modern LTS Expansion (Minecraft 1.20.1 & 1.18.2)
- **Intended Outcome**: Extend HeapHammer to the most popular modern modpack versions, providing native chunk stress testing on Minecraft 1.20.1 and 1.18.2.
- **Scope Boundary**: Release branches `ver/1.20.1` and `ver/1.18.2`, `FabricPlatformAdapter` registry checks, Java 17 compatibility.
- **Dependencies**: M1 (multi-version build tooling).
- **Acceptance Evidence**:
  - `ver/1.20.1` compiles against Fabric API `0.92.2+1.20.1` and passes all 34 invariant unit tests (`BUILD SUCCESSFUL`).
  - `ver/1.18.2` compiles against Fabric API `0.76.0+1.18.2` and passes all 34 invariant unit tests (`BUILD SUCCESSFUL`).
  - Pure domain code remains 100% untouched.
- **Status**: **Completed & Verified** (Commit `41dcd9b`, merged into `master`).

---

### Milestone M3: Legacy Bridge (Minecraft 1.16.5) & Classic Titan (Minecraft 1.12.2 Forge)
- **Intended Outcome**: Extend HeapHammer into pre-1.17 LTS modding (1.16.5) and classic 1.12.2 Forge modpacks (GTNH, SevTech).
- **Scope Boundary**: Release branches `ver/1.16.5` and `ver/1.12.2-forge`, `ForgePlatformAdapter`, `ForgeChunkTicketManager`, `ForgeTicketBridge`.
- **Dependencies**: M1, M2.
- **Acceptance Evidence**:
  - `ver/1.16.5` adapts `Registry.BLOCK` and `Registry.ENTITY_TYPE` and passes all unit tests (`BUILD SUCCESSFUL`).
  - `ver/1.12.2-forge` implements `ForgePlatformAdapter` and passes `ForgePlatformAdapterTest` with 100% pass rate.
  - Bridge architecture documented in [docs/MULTI_VERSION_ARCHITECTURE.md](../docs/MULTI_VERSION_ARCHITECTURE.md).
  - Bridge contract fully documented in [docs/FORGE_1_12_2_BRIDGE.md](../docs/FORGE_1_12_2_BRIDGE.md).
- **Status**: **Completed & Verified** (Commit `aaeca2e`, merged into `master`).

---

## Active & Upcoming Horizons

### Milestone M4: Entity & Block Entity Churn Scenarios
- **Intended Outcome**: Expand active stress testing beyond chunk caching to cover mob tracking leaks (`/hh run entities`) and tile entity ticking leaks (`/hh run blockentities`).
- **Scope Boundary**: `EntityScenarioExecutor`, `BlockEntityScenarioExecutor`, and companion testmod fixtures.
- **Dependencies**: M1–M3.
- **Acceptance Evidence**:
  - Live dedicated server benchmark demonstrating detection of entity tracking leaks (e.g. `testmod-leak-omnitrack` entity subsystem).
  - Clean baseline passes with slope $\le 1.0\text{ MB/cycle}$.
- **Target Horizon**: Release 1.1.0.
- **Status**: Active Specification.

---

### Milestone M5: Registry Coverage & Mod Namespace Targeting
- **Intended Outcome**: Allow server administrators and developers to stress-test specific mod namespaces (e.g. `/hh target mekanism`) by querying Minecraft's block and entity registries dynamically.
- **Scope Boundary**: Command layer (`HeapHammerCommands`), platform adapter registry iteration methods.
- **Dependencies**: M4.
- **Acceptance Evidence**:
  - Filter command resolves registered entity types and block types matching the specified modid prefix.
  - Automatically generates targeted lifecycle workloads.
- **Target Horizon**: Release 1.2.0.
- **Status**: Planned Horizon.

---

### Milestone M6: Third-Party Workload Adapter SPI
- **Intended Outcome**: Provide an official Service Provider Interface (SPI) enabling third-party mod authors to supply custom lifecycle generators and assertions for their own mods.
- **Scope Boundary**: `com.dwurdy.heaphammer.api.spi` package and Java `ServiceLoader` integration.
- **Dependencies**: M5.
- **Acceptance Evidence**:
  - External mod can implement `HeapHammerStressProvider` without compile-time coupling to HeapHammer internal classes.
- **Target Horizon**: Release 2.0.0.
- **Status**: Future Exploration.

---

### Milestone M7: Modern Frontier & NeoForge Hub (Minecraft 1.21.4 & NeoForge 1.21.x)
- **Intended Outcome**: Extend HeapHammer to Minecraft 1.21.4 (Pale Garden / Bundles) and provide a native NeoForge 1.21.x platform adapter for modern tech/magic modpacks.
- **Scope Boundary**: Branch `ver/1.21.4`, `com.dwurdy.heaphammer.platform.neoforge` package, dual-loader build configurations.
- **Dependencies**: M1–M3.
- **Acceptance Evidence**:
  - `ver/1.21.4` compiles against Fabric API `0.110.0+1.21.4` and passes all 34 pure-domain unit tests.
  - NeoForge platform adapter registers commands via `RegisterCommandsEvent` and manages tickets cleanly.
- **Detailed Specification**: See [.scratch/MILESTONES_POPULAR_VERSIONS.md](MILESTONES_POPULAR_VERSIONS.md#milestone-m7-modern-frontier--neoforge-hub-minecraft-1214--neoforge-121x).
- **Target Horizon**: Release 1.3.0.
- **Status**: Planned Specification.

---

### Milestone M8: Modern LTS Bridge (Minecraft 1.19.2 Fabric & Forge)
- **Intended Outcome**: Provide native HeapHammer support for Minecraft 1.19.2, covering landmark modpacks (All The Mods 8, Better MC 1.19.2, Medieval MC, Create: Astral).
- **Scope Boundary**: Branch `ver/1.19.2`, Fabric API `0.77.0+1.19.2`, Forge `43.4.x` bridge, CI multibranch scan.
- **Dependencies**: M1–M3.
- **Acceptance Evidence**:
  - `ver/1.19.2` compiles on Java 17 and passes 100% of unit tests.
  - Dedicated server chunk churn executes with zero leftover tickets.
- **Detailed Specification**: See [.scratch/MILESTONES_POPULAR_VERSIONS.md](MILESTONES_POPULAR_VERSIONS.md#milestone-m8-modern-lts-bridge-minecraft-1192-fabric--forge).
- **Target Horizon**: Release 1.3.0.
- **Status**: Planned Specification.

---

### Milestone M9: Golden Age Classic Titan (Minecraft 1.7.10 Forge & CleanroomMC)
- **Intended Outcome**: Deliver a deterministic retained-memory stress testing and regression framework for 1.7.10 Forge and CleanroomMC servers (GT:NH 1.7.10, Thaumcraft 4, Witchery).
- **Scope Boundary**: Branch `ver/1.7.10-forge`, `ForgePlatformAdapter1710`, `ForgeChunkTicketManager1710`, RetroFuturaGradle build pipeline.
- **Dependencies**: M3 (`ver/1.12.2-forge` legacy bridge).
- **Acceptance Evidence**:
  - Pure-domain code compiles on Java 8 bytecode and passes all 34 unit tests.
  - Legacy `ForgeChunkManager.forceChunk(...)` and `unforceChunk(...)` cycles operate without ticket retention.
- **Detailed Specification**: See [.scratch/MILESTONES_POPULAR_VERSIONS.md](MILESTONES_POPULAR_VERSIONS.md#milestone-m9-golden-age-classic-titan-minecraft-1710-forge--cleanroommc).
- **Target Horizon**: Release 1.4.0.
- **Status**: Planned Specification.

