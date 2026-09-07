# HeapHammer Agent Directives & Engineering Invariants

These directives define mandatory operating principles, architectural invariants, and non-destructive coding rules for any AI agent working on the HeapHammer codebase.

---

## 1. Scratchpad & Documentation Rules

### 1.1 The `.scratch/` Directory Policy
- **Temporary Planning**: All transient task breakdowns, working issue plans, exploratory scratchpads, and intermediate notes **must** reside in [`.scratch/`](../../.scratch).
- **Never Pollute `docs/`**: The [`docs/`](../../docs) directory is strictly reserved for permanent, production open-source documentation. Never place temporary sprint plans or WIP scratchpads in `docs/`.
- **Full Uppercase Naming**: All markdown documents in `docs/` and `.scratch/` must be named in **FULL UPPERCASE** (e.g., `DECISION.md`, `CASE_STUDIES.md`, `MULTI_VERSION_ARCHITECTURE.md`, `MILESTONES.md`).

---

## 2. Non-Obvious Code Rules & Architectural Invariants

### 2.1 The Zero-Minecraft Core Domain Boundary (Hexagonal Architecture)
- **Zero Leaky Imports**: Packages under `domain`, `scenario`, `detection`, `reporting`, `storage`, and `infrastructure` must contain **ZERO** imports from `net.minecraft.*`, `net.fabricmc.*`, or `net.minecraftforge.*`.
- **Port Interfaces**: Any interaction with the game world (chunk tickets, entity queries, block registry queries, tick hooks) must pass through port interfaces in `com.dwurdy.heaphammer.platform` (e.g., `PlatformAdapter`, `ChunkTicketManager`).
- **Why It Matters**: 95% of the codebase is version-agnostic pure Java. Violating this boundary breaks automated GitHub Actions synchronization and compilation across the 4 other Minecraft version branches (`1.20.1`, `1.18.2`, `1.16.5`, `1.12.2-forge`).

### 2.2 The Zero-Leaked-Reference Invariant (Don't Become the Leak!)
- HeapHammer is a memory regression detection tool. If HeapHammer retains live game references, it causes the very leak it is testing for.
- **Never store live references**: Never hold fields or collections containing `LevelChunk`, `ServerLevel`, `Entity`, `BlockEntity`, or `MinecraftServer` across tick boundaries or inside plans/reports.
- **Store primitives & immutable keys**: Persist only chunk coordinate longs (`ChunkPos.toLong()`), integer `(x, y, z)`, entity `UUID`s, or string identifier keys.

### 2.3 Tick Budget & Production Safety (Non-Destructive Execution)
- HeapHammer executes workloads on live dedicated servers while other threads and mods run.
- **Never block the Server Thread**: Never execute blocking I/O, heavy file serialization, or unbounded loops on the server tick thread.
- **Honor Tick Budgets**: All scenario generators must chunk operations into slices respecting `maxOperationsPerTick` (default `10`) and `maxMsPerTick` (default `15 ms`).
- **Do Not Cause Watchdog Timeouts**: If a workload step cannot complete within its tick budget, schedule continuation on the next tick via `PlatformAdapter`.

### 2.4 Ticket Ownership & Isolation
- Chunk tickets must use HeapHammer's dedicated `TicketType<ChunkPos>` registered with the game.
- **Never touch foreign tickets**: Never release, alter, or query chunk tickets owned by players, world spawn, or other mods.
- **100% Cleanup Guarantee**: The `CLEANING_UP` state machine phase must release every ticket registered by HeapHammer. Any leftover ticket is a test failure (`FAIL`).

### 2.5 Determinism & Replay Invariant
- Every scenario generator (Spiral, Ring, Hotspot, Random Walk) must be deterministic and seeded.
- Given the same `seed` and initial parameters, replaying with `/hh replay <run-id>` must produce the exact bit-for-bit sequence of operations across server restarts.

### 2.6 OLS Linear Regression & Plateau Detection vs. GC Noise
- Never use instantaneous memory deltas ($\Delta \text{Heap} = \text{Heap}_{\text{end}} - \text{Heap}_{\text{start}}$) to judge a memory leak. GC allocation noise and one-time JVM cache warming will trigger false positives.
- Always use Ordinary Least Squares (OLS) linear regression across post-cleanup evaluation checkpoints.
- Recognize **Plateau Patterns**: Initial memory growth that flattens into a horizontal slope ($|m| < 0.25\text{ MB/cycle}$) is benign cache warming (`PASS`), not a leak.

### 2.7 Cross-Version Compatibility Awareness
- HeapHammer supports Minecraft `1.21.1` (Java 21), `1.20.1` / `1.18.2` (Java 17), and `1.16.5` / `1.12.2` (Java 8).
- Avoid Java 21-only language features in shared domain code if they prevent Java 17 bytecode backporting without good reason.
- When writing platform adapters, isolate version-specific changes (e.g., `BuiltInRegistries` vs `Registry.BLOCK`, `ForgeChunkManager` vs `TicketType`) in `platform.fabric` or `platform.forge`.

---

## 3. How to Prove Your Fix Works (TDD & Empirical Proof)

*Nothing works until proven it works in real environments.*

### 3.1 Test-First Verification (RED → GREEN)
1. **Reproduce First (RED)**:
   - Before modifying code to fix a bug or regression, write an automated test in `src/test/java/...` or run against a testmod fixture that captures the failure.
   - Run the test and confirm it fails.
2. **Apply Minimal Fix (GREEN)**:
   - Implement the change at the canonical owner.
   - Run the test and confirm it turns green.
3. **Full Regression Test**:
   - Run `./gradlew test` to ensure zero regressions across all 34+ invariant tests.

### 3.2 Proving Memory Leak & Performance Claims
- Always provide baseline vs. candidate measurements using the exact same workload parameters (`--iterations`, `--batch`, `--hold`, `--settle`).
- Quote the Ordinary Least Squares slope ($m$), $R^2$ fit, and net delta.
- Use JVM class histograms (`/hh diagnostics histogram`) to prove that the accumulating class roots were eliminated.

---

## 4. How to Write a High-Value Issue (Inspired by Happier OSS)

Describing the problem thoroughly and objectively is often more valuable than an unreviewed PR.

### 4.1 What Makes a Great Issue
- **Descriptive, Specific Title**: E.g., `[1.20.1-Fabric] Chunk ticket leak on emergency server shutdown during HOLDING phase`.
- **Platform & Environment Context**: Target Minecraft version, loader version, Java runtime (vendor + version), and active modpack size.
- **Reproducible Command**: Exact CLI invocation including all flags and seeds.
- **Observed vs. Expected**: Concrete numbers (e.g., slope $+10.56\text{ MB/cycle}$ vs expected $\le 0.75\text{ MB/cycle}$, active tickets leftover).
- **Diagnostics Attached**: JSON report from `run/heaphammer/reports/` or class histogram snippet.

### 4.2 Production Issue / Task Handoff Standard
When drafting an issue or task specification for an AI agent or contributor, every item must provide:
1. **Strategic Intent & Milestone Placement**: Why this task exists in the overall system outcome.
2. **Expected Agent Responsibilities**: Actionable step-by-step implementation tasks.
3. **Explicit Anti-Assumptions (What the Agent MUST NOT Infer)**: Explicit negative boundaries preventing scope creep.
4. **Exact File Boundaries**: Precise lists of `[NEW]` and `[MODIFY]` files.
5. **Measurable Acceptance Criteria & Test Surfaces**: Executable commands confirming completion.

---

## 5. Being Helpful, Not Destructive

### 5.1 Non-Destructive Code Modifications
- **Preserve Existing Architecture**: Never refactor working subsystems or introduce speculative machinery.
- **Preserve Docstrings & Comments**: Retain all mathematical, architectural, and explanatory docstrings.
- **Check Before Overwriting**: Inspect existing implementations to prevent split-brains or duplicate utility classes.

### 5.2 Safe Server & Environment Handling
- Test fixtures in `testmods/` simulate genuine modpack leak patterns. Do not edit them unless modifying fixture contracts.
- Generated server test runs deposit artifacts in `run/heaphammer/reports/`, `plans/`, and `heapdumps/`. Do not delete or commit these directories.

### 5.3 Git Safety & Verification Requirement
- Never run destructive git commands (`git reset --hard`, `git clean -fd`) in the workspace.
- Always execute `./gradlew test` and confirm all tests pass before completing any task.
