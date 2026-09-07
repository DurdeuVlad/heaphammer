# Architectural Decision Log (ADR)

This document records the foundational architectural decisions for HeapHammer, detailing context, evaluated alternatives, chosen rationale, and consequences.

---

## ADR-001: Hexagonal Architecture & Zero-Minecraft Core Domain

- **Status**: Accepted & Active
- **Context**: Minecraft modding is historically plagued by coupling between domain logic and Mojang internal classes (`net.minecraft.*`) or loader-specific APIs. Version changes routinely force mod rewrites.
- **Decision**: Quarantine all core domain logic (`domain`, `scenario`, `detection`, `reporting`, `storage`, `infrastructure`) with **zero** Minecraft or mod loader imports. Platform interactions flow through pure Java port interfaces under `com.dwurdy.heaphammer.platform`.
- **Consequences**:
  - *Positive*: 95% of the codebase compiles identically across Java 21, 17, and 8. Core fixes auto-sync across all 5 Minecraft version branches without merge conflicts.
  - *Negative*: Requires interface indirection and dedicated ticket adapters per mod loader.

---

## ADR-002: Ordinary Least Squares (OLS) Regression & Plateau Detection

- **Status**: Accepted & Active
- **Context**: Instantaneous memory deltas ($\Delta \text{Heap}$) produce unacceptable false positives on clean servers due to non-deterministic GC cycles and normal cache warming.
- **Decision**: Calculate retained memory trends using **Ordinary Least Squares (OLS) Linear Regression** ($y = mx + b$) across post-cleanup checkpoints. Implement a **Plateau Detector** to classify early cache warming that flattens into zero incremental growth as `PASS`.
- **Consequences**:
  - *Positive*: Empirically validated on live dedicated servers. Yields zero false positives on vanilla baselines (+0.75 MB/cycle noise) while detecting genuine leaks (+10.56 MB/cycle) with $R^2 > 0.999$.
  - *Negative*: Requires 3–5 iterations to establish high statistical confidence.

---

## ADR-003: Multi-Version Git Branching with Automated PR Sync

- **Status**: Accepted & Active
- **Context**: HeapHammer targets 5 distinct Minecraft eras: `1.21.1` (Trunk), `1.20.1`, `1.18.2`, `1.16.5`, and `1.12.2-forge`. An omni-jar is impossible due to incompatible bytecode targets (Java 21 vs Java 8).
- **Decision**: Maintain dedicated release branches (`ver/<version>`) alongside `master`. An automated GitHub Actions workflow merges `master` down on every commit. If clean, it pushes automatically; if conflicts arise, it opens an automated PR labeled `needs-version-adaptation`.
- **Consequences**:
  - *Positive*: Zero manual overhead for syncing core improvements down to older Minecraft versions.
  - *Negative*: Multiple remote Git branches to maintain.

---

## ADR-004: Dynamic Jenkins JDK Toolchain Resolution

- **Status**: Accepted & Active
- **Context**: Multibranch Jenkins pipelines must build branches targeting Java 21 (`master`, `ver/1.21.1`), Java 17 (`ver/1.20.1`, `ver/1.18.2`), and Java 8 (`ver/1.16.5`, `ver/1.12.2-forge`).
- **Decision**: Dynamically inspect `gradle.properties` (`java_version`) at pipeline startup, map the target version to configured Jenkins JDK Tools (`JDK21`, `JDK17`, `JDK8`), and bind the selected tool to `PATH` and `JAVA_HOME`.
- **Consequences**:
  - *Positive*: Single declarative `Jenkinsfile` runs universally across all version branches.
  - *Negative*: Jenkins administrators must configure `JDK21`, `JDK17`, and `JDK8` in Global Tool Configuration.

---

## ADR-005: Reflection-Decoupled Legacy Forge 1.12.2 Bridge

- **Status**: Accepted & Active
- **Context**: Minecraft 1.12.2 uses legacy `ForgeChunkManager` and FML event buses. Linking modern Fabric code directly against Forge classes causes classpath crashes.
- **Decision**: Implement `ForgeTicketBridge` and `ForgePlatformAdapter` using Java reflection with cached `MethodHandle` references. Provide a safe standalone fallback for headless JUnit testing.
- **Consequences**:
  - *Positive*: Compiles cleanly and runs unit tests anywhere without a running 1.12.2 Forge runtime.
  - *Negative*: Reflection invocation overhead is negligible (tickets are requested once per batch).

---

## ADR-006: Milestone-Batched Release Branching vs. Continuous Deployment

- **Status**: Accepted & Active
- **Context**: Automatically publishing artifacts to Modrinth, CurseForge, and GitHub Releases on every individual issue fix creates modpack update fatigue and risks deploying partial or unverified releases.
- **Decision**: Routine PRs and issue fixes land continuously on `master` with CI test verification only. Releases are batched via dedicated release branches (`release/v*`) where work is gathered, changelogs compiled, and live server matrix tests executed before tagging and deploying.
- **Consequences**:
  - *Positive*: Stable, predictable releases for modpack creators. Release candidates undergo full matrix validation.
  - *Negative*: Requires explicit release branch management and version bumping on `master`.
