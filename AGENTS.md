# HeapHammer Agent Guidelines

This file provides machine-readable instructions and mandatory rules for all AI coding agents working on the HeapHammer repository.

---

## 1. Scratchpad & Document Conventions

- **Temporary Planning in `.scratch/`**: All task breakdowns, milestone tracking, intermediate notes, and scratchpad specs **must** be stored in [`.scratch/`](.scratch).
- **Permanent Docs in `docs/`**: The [`docs/`](docs) folder is reserved strictly for permanent, polished open-source documentation. Never commit transient scratchpads or temporary sprint plans to `docs/`.
- **Full Uppercase Naming**: All markdown documents in `docs/` and `.scratch/` must be named in **FULL UPPERCASE** (e.g., `DECISION.md`, `CASE_STUDIES.md`, `MULTI_VERSION_ARCHITECTURE.md`, `MILESTONES.md`).

---

## 2. Non-Obvious Code Rules & Invariants

1. **Zero-Minecraft Imports in Core Domain (Hexagonal Architecture)**:
   - Packages `com.dwurdy.heaphammer.domain`, `scenario`, `detection`, `reporting`, `storage`, and `infrastructure` must have **ZERO** imports from `net.minecraft.*`, `net.fabricmc.*`, or `net.minecraftforge.*`.
   - All platform interactions must pass through `com.dwurdy.heaphammer.platform` interfaces.
   - Violating this breaks multi-version synchronization across `ver/1.20.1`, `ver/1.18.2`, `ver/1.16.5`, and `ver/1.12.2-forge`.

2. **Zero-Leaked-Reference Invariant**:
   - HeapHammer must never retain live references to `LevelChunk`, `ServerLevel`, `Entity`, or `BlockEntity`.
   - Store only primitive coordinates (`ChunkPos.toLong()`), integer positions, UUIDs, or identifier strings.
   - Holding game objects inside HeapHammer turns HeapHammer into a memory leak!

3. **Strict Tick Budgeting (Non-Destructive Execution)**:
   - Workloads must run incrementally across server ticks without starving the server thread.
   - Honor `maxOperationsPerTick` (default `10`) and `maxMsPerTick` (default `15 ms`). Never perform blocking IO or unbounded loops on the server tick thread.

4. **Dedicated Chunk Ticket Isolation**:
   - Only manipulate chunk tickets registered under HeapHammer's dedicated `TicketType<ChunkPos>`.
   - Never release or touch tickets belonging to players, spawn, or other mods.
   - All tickets must be cleanly released during the `CLEANING_UP` phase.

5. **Statistical OLS Regression & Plateau Detection vs. GC Noise**:
   - Never judge memory leaks from raw instantaneous diffs.
   - Always evaluate trend slopes via Ordinary Least Squares ($y = mx + b$) across post-settle checkpoints.
   - Benign cache warming that flattens into zero incremental retention must be classified as `PASS` (Plateau pattern).

6. **Cross-Version Java Compatibility**:
   - Shared domain code must remain compatible with Java 17 and Java 8 backports.
   - Isolate loader-specific code inside `platform.fabric` or `platform.forge`.

---

## 3. Being Helpful, Not Destructive

- **Preserve Working Code**: Do not rewrite existing working components, change established architecture, or delete working test fixtures.
- **Preserve Comments & Docstrings**: Keep all explanatory comments, mathematical notations, and architecture docs intact.
- **Always Verify Empirically**: Run `./gradlew test` and confirm all tests pass before completing any task.
- **Detailed Rules Reference**: See [`.agents/rules/heaphammer_rules.md`](.agents/rules/heaphammer_rules.md).
