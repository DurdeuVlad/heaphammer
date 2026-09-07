# HeapHammer Agent Constitution & Guidelines

This document serves as the canonical instructions and machine-readable constitution for all AI agents and developers working in the HeapHammer repository.

---

## 1. Project Philosophy & System Invariants

HeapHammer is a **deterministic server workload and retained-memory regression framework** for modded Minecraft.
- **Core Purpose**: Take an insidious memory leak or cross-mod collision that normally requires 24+ hours of chaotic player traffic to manifest, compress that activity into repeatable, tick-budgeted staging cycles, and produce an objective, replayable verdict with empirical evidence.
- **Hexagonal Architecture (Ports & Adapters)**:
  - The core domain (`domain`, `scenario`, `detection`, `reporting`, `storage`, `infrastructure`) is **100% pure Java** with **zero** Minecraft (`net.minecraft.*`) or mod loader imports.
  - All game interactions pass through explicit port interfaces in `com.dwurdy.heaphammer.platform` (`PlatformAdapter`, `ChunkTicketManager`).
  - This guarantees binary portability across all 5 supported Minecraft version branches (`master` / `1.21.1`, `1.20.1`, `1.18.2`, `1.16.5`, `1.12.2-forge`).

---

## 2. Scratchpad & Documentation Conventions

- **Temporary Planning in `.scratch/`**: All working drafts, task breakdowns, milestone tracking notes, and scratchpad specs **must** be stored in [`.scratch/`](.scratch).
- **Permanent Docs in `docs/`**: The [`docs/`](docs) folder is reserved strictly for permanent, polished open-source documentation. Never commit transient scratchpads or temporary sprint plans to `docs/`.
- **Full Uppercase Naming**: All markdown documents in `docs/` and `.scratch/` must be named in **FULL UPPERCASE** (e.g., `DECISION.md`, `CASE_STUDIES.md`, `MULTI_VERSION_ARCHITECTURE.md`, `MILESTONES.md`).

---

## 3. Tier 0 Architectural & Engineering Invariants (Non-Obvious Rules)

1. **Zero-Minecraft Imports in Core Domain**:
   - Never import `net.minecraft.*`, `net.fabricmc.*`, or `net.minecraftforge.*` into domain, scenario, or detection packages.
   - Violating this breaks automated GitHub Actions synchronization and compilation across all downstream Minecraft version branches.
2. **Zero-Leaked-Reference Invariant (Don't Become the Leak!)**:
   - HeapHammer must **never** retain live object references to `LevelChunk`, `ServerLevel`, `Entity`, or `BlockEntity`.
   - Store only primitive chunk coordinates (`ChunkPos.toLong()`), integer `(x, y, z)`, UUIDs, or identifier strings. Holding game objects inside HeapHammer turns HeapHammer into a memory leak.
3. **Strict Tick Budgeting (Non-Destructive Execution)**:
   - HeapHammer executes workloads on live dedicated servers. It must never starve or stall the server thread.
   - Strictly honor `maxOperationsPerTick` (default `10`) and `maxMsPerTick` (default `15 ms`).
   - Never perform blocking I/O, heavy file serialization, or unbounded loops on the server tick thread.
4. **Dedicated Chunk Ticket Isolation**:
   - Only manipulate chunk tickets registered under HeapHammer's dedicated `TicketType<ChunkPos>`.
   - Never touch, release, or query tickets belonging to players, world spawn, or other mods.
   - 100% of tickets must be cleanly released during the `CLEANING_UP` phase. Leftover tickets constitute a test failure (`FAIL`).
5. **Statistical OLS Regression & Plateau Detection vs. GC Noise**:
   - Never judge memory leaks from raw instantaneous diffs ($\Delta\text{Heap}$).
   - Evaluate trend slopes via Ordinary Least Squares ($y = mx + b$) and goodness-of-fit ($R^2$) across post-settle checkpoints.
   - Benign cache warming that flattens into zero incremental retention must be classified as `PASS` (Plateau pattern).
6. **Canonical Owner over Workaround (No Split-Brains)**:
   - Never introduce competing decision-makers, parallel state paths, or consumer-owned workarounds.
   - Fix issues at their canonical root cause within the appropriate port or adapter.
7. **Cross-Version Java Compatibility**:
   - Shared domain code must remain compatible with Java 17 and Java 8 backports.
   - Isolate loader-specific code inside `platform.fabric` or `platform.forge`.

---

## 4. How to Prove Your Fix Works (Empirical Verification & TDD)

*Nothing works until proven it works in real environments.* We adhere strictly to empirical proof:

### 4.1 Production Behavior Changes Require Test-First (RED → GREEN)
1. **Reproduce First (RED)**:
   - Before modifying production logic, write a targeted unit test in `src/test/java/...` or execute against a companion synthetic testmod fixture in `testmods/` to demonstrate the failure or regression.
   - Verify that the test or metric actually fails on the unmodified code.
2. **Fix at Canonical Owner (GREEN)**:
   - Implement the smallest coherent change at the root cause.
   - Re-run the test to confirm the transition from RED to GREEN.
3. **Regression Check**:
   - Execute the entire unit test suite:
     ```powershell
     ./gradlew test
     ```
   - Confirm all tests pass with exit code `0`.

### 4.2 Proving Memory Leak & Performance Fixes
When claiming a fix for memory retention or performance regressions:
- **Measure Both Sides on Identical Workloads**: Always provide baseline slope vs. fixed slope under the exact same parameters (`--iterations`, `--batch`, `--hold`, `--settle`).
- **Never Rely on Instantaneous Diffs**: Quote the Ordinary Least Squares slope ($m$), $R^2$ fit, and net delta.
- **Isolate Roots with Class Histograms**: Use `/hh diagnostics histogram` to empirically prove that target class instances cease accumulating in JVM memory.
- **Honest Test Reporting**: Never claim a test passed without actually running the command and inspecting the output.

---

## 5. How to Write a High-Value Issue (Inspired by Happier OSS)

A great issue report is often more valuable than an unreviewed PR. It provides the exact context needed for maintainers or AI agents to investigate, reproduce, and fix the problem cleanly.

### 5.1 What Makes a Great Issue
- **Descriptive, Context-Rich Title**: E.g., `[1.20.1-Forge] ForgeChunkTicketManager fails to unforce ticket on abort during SETTLING phase` (avoid vague titles like `tickets broken`).
- **Target Environment & Version**:
  - Minecraft version (e.g. `1.20.1`), Mod Loader (`Fabric 0.15.11` / `Forge 14.23.5.2860`), Java runtime (`Eclipse Temurin 17.0.8`).
  - Active modpack size and presence of performance mods (e.g. Lithium, FerriteCore).
- **Exact Reproducible Workload Command**:
  ```text
  /hh run chunks --iterations=5 --batch=10 --radius=6 --hold=5 --settle=10 --explicit-gc=true
  ```
- **Observed vs. Expected Behavior**:
  - *Observed*: Retained slope was `+10.56 MB/cycle` ($R^2 = 0.999$), resulting in `SUSPICIOUS` verdict. Leftover tickets: 10.
  - *Expected*: Zero active tickets after settle, slope $\le 0.75\text{ MB/cycle}$, verdict `PASS`.
- **Diagnostic Artifacts**:
  - Attach or paste relevant JSON run reports from `run/heaphammer/reports/`.
  - Attach JVM class histogram snippet showing top retained roots (`/hh diagnostics histogram`).

### 5.2 Production Issue / Milestone Specification Standard
When drafting an issue or milestone task for agent or contributor handoff, follow this 5-point contract:
1. **Strategic Intent & Milestone Placement**: Why this task exists in the overall system outcome.
2. **Expected Agent Responsibilities**: Step-by-step actionable implementation instructions.
3. **Explicit Anti-Assumptions (What the Agent MUST NOT Infer)**: Explicit negative constraints preventing scope creep, runtime guessing, or architecture violations.
4. **Exact File Boundaries**: Precise lists of `[NEW]` and `[MODIFY]` files.
5. **Measurable Acceptance Criteria & Test Surfaces**: Concrete, executable verification commands.

---

## 6. How to Contribute (Being Helpful, Not Destructive)

### 6.1 Git & Working Tree Safety
- **Never Discard Uncommitted Changes**: Never run `git reset --hard`, `git clean -fd`, or discard uncommitted changes in the primary checkout without explicit authorization.
- **No Direct Pushes to `master` or Release Branches**:
  - `master` and release branches (`release/v*`) are protected trunks. AI agents must **never** push directly to `master`.
  - Always work on dedicated branches (`feat/<name>`, `fix/<issue>-<name>`) targeting `master` or the active release line (`release/v1.0.0`).
  - Run verification (`./gradlew test`) and prepare the branch/PR for human review.
- **Conventional Commits**: Format commit messages as `<type>(<scope>): <subject>` (e.g., `fix(platform): release chunk tickets cleanly on emergency abort`).

### 6.2 Code Preservation & Scope Discipline
- **Make the Smallest Coherent Change**: Do not rewrite working subsystems, delete existing test fixtures, or introduce speculative architectural machinery.
- **Preserve Comments & Docstrings**: Keep all explanatory comments, mathematical proofs, and architectural notes intact.
- **Inspect Before Editing**: Search existing symbols and registries before creating new abstractions to avoid split-brains and duplicate logic.
- **Review Guidelines**: For broader project contribution rules, refer to [CONTRIBUTING.md](CONTRIBUTING.md) and [docs/OSS.md](docs/OSS.md).

---

## 7. AI Disclosure, Transparency & Attribution Policy

HeapHammer embraces AI-assisted engineering and pair programming while maintaining strict standards for software integrity, security, and attribution.

### 7.1 Mandatory AI Disclosure for Contributions
- **Full Transparency**: If an issue, PR, plan, or commit was drafted, generated, or assisted by an AI agent (e.g. Antigravity, Claude Code, Cursor, Copilot), the author **must** explicitly disclose it in the PR description or issue footer:
  ```markdown
  > 🤖 **AI Disclosure**: This contribution was developed with AI pair-programming assistance (Tool/Agent: Antigravity / Claude 3.7 Sonnet). All code has been reviewed, locally built, and empirically verified against the test suite.
  ```
- **Audit Trails**: If an agent was operated under autonomous loops, link or reference the relevant execution logs or task plan IDs if applicable.

### 7.2 The Human Accountability Invariant
- **The Contributor Owns the Code**: AI tools and coding agents do not have legal standing or repository accountability. The human committer/author remains 100% legally and technically responsible for every line of code, comment, and configuration merged.
- **"The AI Wrote It" is Never an Excuse**: Regressions, hallucinated APIs, broken unit tests, security vulnerabilities, or license contaminations cannot be blamed on the AI model.

### 7.3 Anti-Hallucination & Anti-Slop Safeguards
- **Zero Hallucinated APIs**: Never accept AI-generated code that references non-existent Minecraft, Forge, or Fabric classes, methods, or parameters. Every referenced symbol must resolve against genuine JDK or modloader dependencies.
- **Empirical Execution over Assumption**: An AI agent must never declare a task complete or state that "tests pass" without actually executing `./gradlew test` in the real environment and inspecting the exit code.
- **Cleanroom & License Purity**: Contributors and agents must ensure generated code does not plagiarize or reproduce code from incompatible licensed repositories (e.g., non-free or GPLv2 code into LGPLv3).
