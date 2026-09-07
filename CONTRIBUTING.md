# Contributing to HeapHammer

Thank you for your interest in contributing to **HeapHammer**!

HeapHammer is a deterministic stress-testing and retained-memory regression framework for modded Minecraft. Because HeapHammer is designed to diagnose insidious memory leaks and cross-mod collisions, we hold our codebase, architecture, and testing to strict engineering standards.

---

## Table of Contents
1. [Core Architectural Principles](#1-core-architectural-principles)
2. [Multi-Version Minecraft Branching Model](#2-multi-version-minecraft-branching-model)
3. [Development Workflow & Conventional Commits](#3-development-workflow--conventional-commits)
4. [Testing & Verification Requirements](#4-testing--verification-requirements)
5. [Pull Request Checklist](#5-pull-request-checklist)
6. [Code of Conduct & License Agreement](#6-code-of-conduct--license-agreement)

---

## 1. Core Architectural Principles

All code submitted to HeapHammer must adhere to our **Hexagonal Architecture (Ports & Adapters)**:

### 1.1 Strict Domain Isolation (Zero Leaky Imports)
- Packages under `com.dwurdy.heaphammer.domain`, `scenario`, `detection`, `reporting`, `storage`, and `infrastructure` must contain **ZERO** Minecraft (`net.minecraft.*`) or Fabric (`net.fabricmc.*`) imports.
- Domain logic must be 100% pure Java 21 and completely testable in standard JUnit unit tests without booting a Minecraft server.
- All interactions with the Minecraft world, chunk ticket system, entity tracker, or block state registries must pass through explicit abstraction interfaces in `com.dwurdy.heaphammer.platform` (e.g., `PlatformAdapter`, `ChunkTicketManager`).

### 1.2 Zero Leaked References Invariant
- State machines, plans, checkpoints, and reports must **never** store live object references to `LevelChunk`, `ServerLevel`, `Entity`, or `BlockEntity`.
- Persist only primitive coordinates (`ChunkPos` packed `long` or integer coordinates), UUIDs, and immutable value objects.

### 1.3 Determinism & Replayability
- Every scenario generation algorithm must be purely deterministic given a random `seed` and initial parameters.
- Replaying a plan with `/hh replay <run-id>` or rebuilding with `/hh rerun <run-id>` must produce the exact bit-for-bit sequence of operations.

### 1.4 Tick Budget & Production Safety
- Workloads must run incrementally across server ticks and respect `maxOperationsPerTick` (default `10`) and `maxMillisPerTick` (default `15 ms`) to prevent server tick starvation or watchdog crashes.
- Chunk tickets must use HeapHammer's dedicated `TicketType<ChunkPos>`. The framework must never manipulate or release tickets owned by players or other mods.

---

## 2. Multi-Version Minecraft Branching Model

HeapHammer supports multiple minor and patch versions of Minecraft through a structured branching strategy:

```text
master (Default Development Trunk — 1.21.1)
  │
  ├──> ver/1.21.1        (Modern Frontier — Fabric, Java 21)
  ├──> ver/1.20.1        (Modern LTS Gold Standard — Fabric, Java 17)
  ├──> ver/1.18.2        (World-Gen Overhaul Era — Fabric, Java 17)
  ├──> ver/1.16.5        (Nether Legacy Era — Fabric, Java 17/8)
  └──> ver/1.12.2-forge  (Classic Titan Era — MinecraftForge, Java 8)
```

- **`master`**: The default branch where all new features, core domain logic, scenario generators, regression detection math, and diagnostic tools are committed.
- **`ver/<minecraft_version>`**: Dedicated release lines for each supported Minecraft version (e.g. `ver/1.21.1`).
- **Automated Synchronization**: Every push to `master` automatically triggers our GitHub Actions sync workflow (`.github/workflows/sync-version-branches.yml`), which merges updates into all version branches.
  - If the merge is clean, the test suite is verified and pushed automatically.
  - If code accommodation is required (e.g. Minecraft registry or mapping changes), an automated PR titled `[Auto-Sync] Merge master into ver/<version>` is created for manual adaptation.

For details on accommodating version differences, see [docs/MULTI_VERSION_ARCHITECTURE.md](docs/MULTI_VERSION_ARCHITECTURE.md).

---

## 3. Development Workflow & Conventional Commits

### 3.1 Prerequisites
- Java 21 JDK (OpenJDK or Eclipse Temurin)
- Git 2.30+

### 3.2 Branch Naming
- Features: `feat/<short-description>` (e.g., `feat/blockentity-filtering`)
- Bug fixes: `fix/<issue-number>-<short-description>` (e.g., `fix/ticket-leak-on-abort`)
- Documentation: `docs/<topic>` (e.g., `docs/triage-guide`)

### 3.3 Commit Message Convention
We adhere strictly to [Conventional Commits](https://www.conventionalcommits.org/):

```text
<type>(<scope>): <subject>

[optional body]

[optional footer(s)]
```

- **Types**: `feat`, `fix`, `test`, `docs`, `refactor`, `perf`, `chore`
- **Scopes**: `scenario`, `detection`, `command`, `platform`, `storage`, `adapter`, `matrix`
- **Example**:
  ```text
  feat(detection): implement plateau pattern recognition for bounded caches

  Identifies early growth that stabilizes into a flat slope across post-warmup cycles.
  Closes #15
  ```

---

## 4. Testing & Verification Requirements

No PR will be merged without concrete test proof. Every contribution must pass all three test tiers:

### Tier 1: Unit & Domain Invariant Tests
Fast, pure Java JUnit 5 tests covering domain state machines, codecs, mathematical regressions, and ticket managers:
```bash
./gradlew test
```

### Tier 2: Compilation & Synthetic Testmod Assembly
Ensures the mod jar and all companion test mod fixtures compile cleanly:
```bash
./gradlew build buildTestmods
```

### Tier 3: Dedicated Server Multi-Mod Matrix Verification
Executes live dedicated server integration benchmarks:
```powershell
# Run all matrix verification scenarios
powershell -ExecutionPolicy Bypass -File tools/run-mod-matrix-test.ps1

# Or test a specific scenario
powershell -ExecutionPolicy Bypass -File tools/run-mod-matrix-test.ps1 -SpecificScenario 01_Baseline_Clean
```

---

## 5. Pull Request Checklist

Before submitting a pull request, ensure:
- [ ] Code compiles with `./gradlew build buildTestmods` without errors or warnings.
- [ ] All unit tests pass (`./gradlew test`).
- [ ] Zero Minecraft/Fabric imports added outside of `com.dwurdy.heaphammer.platform` and `command`.
- [ ] Any new command or scenario is documented in `README.md` and `docs/`.
- [ ] Commit history is clean, readable, and follows conventional commits.
- [ ] If modifying detection thresholds or scenarios, include empirical evidence (test logs or report JSON diff).

---

## 6. Code of Conduct & License Agreement

By contributing to HeapHammer, you agree to:
1. Abide by our [Code of Conduct](CODE_OF_CONDUCT.md).
2. License all submitted contributions under the [GNU Lesser General Public License v3.0 (LGPL-3.0)](LICENSE).
3. Certify that you authored the contribution or have the right to submit it under the Developer Certificate of Origin (DCO).
