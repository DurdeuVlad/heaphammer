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
- **Documentation**: [docs/JENKINS_PIPELINE.md](JENKINS_PIPELINE.md).
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
  - Bridge contract fully documented in [docs/FORGE_1_12_2_BRIDGE.md](FORGE_1_12_2_BRIDGE.md).
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
