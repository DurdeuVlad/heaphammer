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

## ADR-003: Dual-Axis Branching Strategy: Production Master & Historical LTS Branches

- **Status**: Accepted & Active
- **Context**: HeapHammer must support both modern production Minecraft (`1.21.1`) and major historical modding eras (`1.20.1`, `1.18.2`, `1.16.5`, and `1.12.2-forge`). Simultaneously, HeapHammer mod releases require stability without branch sprawl. Bytecode and runtime incompatibilities (Java 21 down to Java 8) prevent an omni-jar.
- **Decision**: 
  1. **Production Trunk (`master`)**: `master` directly hosts the latest production release of HeapHammer and targets Minecraft `1.21.1` (Java 21). We do not maintain a redundant `ver/1.21.1` branch.
  2. **Historical LTS Minecraft Branches**: Maintain dedicated downstream branches (`ver/<version>`) **strictly for historical versions that are LTS or actively supported** (`ver/1.20.1`, `ver/1.18.2`, `ver/1.16.5`, `ver/1.12.2-forge`). Non-LTS intermediate versions do not receive branches.
  3. **Historical Mod Release Branches**: Dedicated maintenance branches are kept only for mod release lines officially designated as LTS; EOL releases are archived as Git tags.
  4. **Automated Upstream-to-Downstream Sync**: An automated GitHub Actions workflow merges `master` down to the 4 historical LTS branches on every push. If clean, it pushes automatically; if conflicts arise, it opens an automated PR labeled `needs-version-adaptation`.
- **Consequences**:
  - *Positive*: Zero duplication between `master` and modern 1.21.1. Clear boundaries on which historical versions are supported. Automated synchronization of core domain improvements down to older Minecraft versions.
  - *Negative*: Four historical LTS Git branches must be maintained and synchronized.

---

## ADR-004: Dynamic Jenkins JDK Toolchain Resolution

- **Status**: Accepted & Active
- **Context**: Multibranch Jenkins pipelines must build branches targeting Java 21 (`master`), Java 17 (`ver/1.20.1`, `ver/1.18.2`), and Java 8 (`ver/1.16.5`, `ver/1.12.2-forge`).
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
