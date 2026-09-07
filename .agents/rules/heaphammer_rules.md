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

## 3. Being Helpful, Not Destructive

### 3.1 Non-Destructive Code Modifications
- **Preserve Existing Architecture**: Do not refactor or rewrite existing working components unless specifically requested.
- **Preserve Docstrings & Comments**: Retain all existing architectural comments, invariants documentation, and explanatory docstrings.
- **Check Before Overwriting**: Inspect existing implementations and tests before adding new abstractions. Avoid introducing redundant utility classes.

### 3.2 Safe Server & Environment Handling
- Test fixtures in `testmods/` simulate genuine modpack leak patterns. Do not edit test mods unless specifically modifying test matrix fixtures.
- Generated server test runs deposit artifacts in `run/heaphammer/reports/`, `plans/`, and `heapdumps/`. Do not delete or commit these directories.

### 3.3 Empirical Verification Requirement
- Never guess or declare that code works without verifying.
- Always execute `./gradlew test` and ensure all unit tests pass before completing a task.
- Check `git status` and `git diff` to ensure no unintended files or formatting changes were introduced.
