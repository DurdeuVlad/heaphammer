# Contributing to HeapHammer

Thank you for your interest in contributing to **HeapHammer**!

HeapHammer is a deterministic stress-testing and retained-memory regression framework for modded Minecraft. Because HeapHammer is designed to diagnose insidious memory leaks and cross-mod collisions, we hold our codebase, architecture, and testing to strict engineering standards.

---

## Table of Contents
1. [The Most Valuable Contribution: A Great Issue](#1-the-most-valuable-contribution-a-great-issue)
2. [Core Architectural Principles](#2-core-architectural-principles)
3. [Multi-Version Minecraft Branching Model](#3-multi-version-minecraft-branching-model)
4. [Development Workflow & Conventional Commits](#4-development-workflow--conventional-commits)
5. [How to Prove Your Fix Works (Testing & Verification)](#5-how-to-prove-your-fix-works-testing--verification)
6. [Pull Request Checklist](#6-pull-request-checklist)
7. [AI-Assisted Contributions & Disclosure Policy](#7-ai-assisted-contributions--disclosure-policy)
8. [Code of Conduct & License Agreement](#8-code-of-conduct--license-agreement)

---

## 1. The Most Valuable Contribution: A Great Issue

You do not need to write code to make a high-impact contribution to HeapHammer.

In a diagnostic framework targeting elusive memory leaks, a **thorough, reproducible issue report** is often *more* valuable than an unreviewed PR. It enables maintainers and AI coding agents to reproduce the regression in an isolated environment and resolve it at the canonical owner without architectural churn.

### What Makes a Great Issue:
- **Descriptive Title**: State the affected component and failure mode clearly (e.g., `[1.20.1-Forge] ForgeChunkTicketManager fails to unforce ticket on abort during SETTLING phase`).
- **Platform & Environment Context**: Target Minecraft version, loader version, Java runtime (vendor + version), and active modpack size.
- **Reproducible Command**: Exact CLI invocation (`/hh run chunks --iterations=5 --batch=10 ...`) including any custom parameters and world seed.
- **Observed vs. Expected**: Concrete metrics (e.g., slope $+10.56\text{ MB/cycle}$ vs expected $\le 0.75\text{ MB/cycle}$, active tickets remaining).
- **Diagnostics Attached**: JSON run report from `run/heaphammer/reports/` or class histogram snippet (`/hh diagnostics histogram`).
- **Agent Handoff Standards**: For AI agent instructions and machine-readable issue specs, see [AGENTS.md](AGENTS.md).

---

## 2. Core Architectural Principles

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

## 3. Multi-Version Minecraft Branching Model

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

- **`master`**: The protected stable development trunk. Direct pushes to `master` are strictly prohibited.
- **`release/v*`**: Batched release staging lines (e.g. `release/v1.0.0` for v1.0.0). We do **not** publish on every individual fix; all work is gathered on the active release branch before tagging.
- **`ver/<minecraft_version>`**: Dedicated downstream platform branches (e.g. `ver/1.20.1`, `ver/1.12.2-forge`).
- **Automated Synchronization**: Merges to `master` automatically trigger `.github/workflows/sync-version-branches.yml` to propagate pure-domain improvements downstream.

### 3.1 Protected Trunk Policy: No Direct Pushes to `master`
To maintain open-source integrity, reproducible builds, and strict code review:
1. **Direct pushes to `master` and release branches are forbidden**.
2. **Always branch off the active target**:
   - For new features and general improvements: `git checkout -b feat/<name> origin/master`
   - For critical bug fixes: `git checkout -b fix/<issue>-<name> origin/master`
3. **Submit a Pull Request**: All changes must be proposed via a Pull Request using our [PR Template](.github/pull_request_template.md) with passing automated tests (`./gradlew test`) before merging.

For details on the release lifecycle, see [docs/PUBLICATION.md](docs/PUBLICATION.md) and [docs/MULTI_VERSION_ARCHITECTURE.md](docs/MULTI_VERSION_ARCHITECTURE.md).

---

## 4. Development Workflow & Conventional Commits

### 4.1 Prerequisites
- Java 21 JDK (OpenJDK or Eclipse Temurin)
- Git 2.30+

### 4.2 Branch Naming
- Features: `feat/<short-description>` (e.g., `feat/blockentity-filtering`)
- Bug fixes: `fix/<issue-number>-<short-description>` (e.g., `fix/ticket-leak-on-abort`)
- Documentation: `docs/<topic>` (e.g., `docs/triage-guide`)

### 4.3 Commit Message Convention
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

## 5. How to Prove Your Fix Works (Testing & Verification)

*Nothing works until proven it works in real environments.* No PR will be merged without concrete test proof.

### 5.1 Test-First Requirement (RED → GREEN)
For all production bug fixes and feature additions:
1. **RED**: Write a unit test in `src/test/java/...` or execute against a companion synthetic testmod fixture that captures the bug before modifying production code. Confirm it fails.
2. **GREEN**: Apply the minimal fix at the canonical owner. Confirm the test passes.

### 5.2 Verification Tiers
Every contribution must pass all three test tiers:

#### Tier 1: Unit & Domain Invariant Tests
Fast, pure Java JUnit 5 tests covering domain state machines, codecs, mathematical regressions, and ticket managers:
```bash
./gradlew test
```

#### Tier 2: Compilation & Synthetic Testmod Assembly
Ensures the mod jar and all companion test mod fixtures compile cleanly:
```bash
./gradlew build buildTestmods
```

#### Tier 3: Dedicated Server Multi-Mod Matrix Verification
Executes live dedicated server integration benchmarks:
```powershell
# Run all matrix verification scenarios
powershell -ExecutionPolicy Bypass -File tools/run-mod-matrix-test.ps1

# Or test a specific scenario
powershell -ExecutionPolicy Bypass -File tools/run-mod-matrix-test.ps1 -SpecificScenario 01_Baseline_Clean
```

---

## 6. Pull Request Checklist

Before submitting a pull request, ensure:
- [ ] Code compiles with `./gradlew build buildTestmods` without errors or warnings.
- [ ] All unit tests pass (`./gradlew test`).
- [ ] Production behavior changes include test-first RED → GREEN verification.
- [ ] Zero Minecraft/Fabric imports added outside of `com.dwurdy.heaphammer.platform` and `command`.
- [ ] Any new command or scenario is documented in `README.md` and `docs/`.
- [ ] Commit history is clean, readable, and follows conventional commits.
- [ ] If modifying detection thresholds or scenarios, include empirical evidence (test logs or report JSON diff).
- [ ] If AI assistance was used, include the AI disclosure tag in the pull request description.

---

## 7. AI-Assisted Contributions & Disclosure Policy

HeapHammer supports the transparent, responsible use of AI tools and coding agents (e.g. Antigravity, Claude Code, Cursor, Copilot).

### 7.1 Mandatory Transparency
If a pull request, issue, or code submission was generated or assisted by AI, contributors **must** state this clearly in their PR description:
```markdown
> 🤖 **AI Disclosure**: This PR was developed with AI assistance (Tool: <ToolName>). All code has been reviewed, locally built, and empirically verified against the test suite.
```

### 7.2 Human Accountability
- **You are responsible**: The human author remains 100% accountable for the correctness, safety, and security of all merged code.
- **Zero Hallucinated Code**: Ensure that all referenced Minecraft, Fabric, and Forge APIs genuinely exist.
- **Empirical Proof**: Never submit AI-generated code without running `./gradlew test` locally.
- For complete agent directives, see [AGENTS.md](AGENTS.md).

---

## 8. Code of Conduct & License Agreement

By contributing to HeapHammer, you agree to:
1. Abide by our [Code of Conduct](CODE_OF_CONDUCT.md).
2. License all submitted contributions under the [GNU Lesser General Public License v3.0 (LGPL-3.0)](LICENSE).
3. Certify that you authored the contribution or have the right to submit it under the Developer Certificate of Origin (DCO).
