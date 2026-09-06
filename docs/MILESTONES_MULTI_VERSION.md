# Flux Milestone Issue Plan: Multi-Version Minecraft Expansion (1.21.1 to 1.12.2)

This document contains the executable issue specifications for extending HeapHammer across Minecraft modding eras from 1.21.1 down to 1.12.2. Every issue adheres to the Flux Milestone and Production Handoff Directives.

---

## Milestone M1: Multi-Version Build Automation & Jenkins CI/CD

### Issue M1.1: Multi-Version Declarative Jenkins Pipeline & Automated Matrix Builds

#### 1. Strategic Intent & Milestone Placement
HeapHammer must support multiple minor and major Minecraft versions with varying JVM targets (Java 21, Java 17, Java 8). Jenkins must automatically detect each branch's target environment and execute tests without manual intervention.

#### 2. Expected Agent Responsibilities
1. Implement a root `Jenkinsfile` utilizing the Declarative Pipeline format.
2. Parse `gradle.properties` dynamically to extract `minecraft_version` and `java_version`.
3. Map detected Java version to configured Jenkins JDK Tools (`JDK21`, `JDK17`, `JDK8`).
4. Execute `./gradlew check test` and `./gradlew build buildTestmods`.
5. Archive compiled jar artifacts (`build/libs/*.jar`, `build/testmods/*.jar`) and JUnit XML test reports (`build/test-results/**/*.xml`).
6. Provide an optional parameter `RUN_MATRIX_BENCHMARKS` to trigger live dedicated server matrix tests.
7. Author complete setup and configuration documentation in `docs/JENKINS_PIPELINE.md`.

#### 3. Explicit Anti-Assumptions (What the Agent MUST NOT Infer)
- The agent must NOT hardcode JDK 21 across all builds; branches targeting Java 17 or Java 8 must use their respective toolchain.
- The agent must NOT assume Docker is mandatory on the Jenkins agent; commands must support both standard Unix agents and Windows bat nodes (`isUnix()`).

#### 4. Exact File Boundaries
- `[NEW] Jenkinsfile`
- `[NEW] docs/JENKINS_PIPELINE.md`

#### 5. Measurable Acceptance Criteria & Test Surfaces
- Automated test: Jenkinsfile syntax validates cleanly without pipeline lint errors.
- Dynamic detection test: Reads `java_version` from `gradle.properties` and selects appropriate JDK tool.
- Artifact archive test: Mod jars and test reports are successfully indexed on pipeline completion.

---

## Milestone M2: Modern LTS Expansion (Minecraft 1.20.1 & 1.18.2)

### Issue M2.1: Minecraft 1.20.1 Target Branch & Platform Accommodation

#### 1. Strategic Intent & Milestone Placement
Minecraft 1.20.1 is the primary "Gold Standard" of modern modding with the largest modpack library (Mekanism, Create, AE2). Adding `ver/1.20.1` ensures HeapHammer can diagnose memory leaks in the most widely played modpacks today.

#### 2. Expected Agent Responsibilities
1. Create dedicated branch `ver/1.20.1` from `master`.
2. Configure `gradle.properties` for Minecraft 1.20.1:
   - `minecraft_version=1.20.1`
   - `loader_version=0.15.11` (or latest compatible 0.16.x)
   - `fabric_api_version=0.92.2+1.20.1`
   - `java_version=17`
3. Verify Java 17 source/target compatibility in `build.gradle`.
4. Accommodate any 1.20.1 vs 1.21.1 differences in `FabricPlatformAdapter`:
   - Verify `BuiltInRegistries.ENTITY_TYPE` and `BuiltInRegistries.BLOCK` resolution.
   - Verify `TicketType.create` comparator signature on 1.20.1.
5. Execute full unit test suite `./gradlew test` and confirm `BUILD SUCCESSFUL`.
6. Add `ver/1.20.1` to `.github/workflows/sync-version-branches.yml` matrix.

#### 3. Explicit Anti-Assumptions
- The agent must NOT modify pure domain code (`com.dwurdy.heaphammer.domain`, `scenario`, `detection`) to fit 1.20.1; all adjustments must reside strictly in `platform.fabric` and `gradle.properties`.
- The agent must NOT downgrade `master` to Java 17; `master` remains Java 21.

#### 4. Exact File Boundaries
- `[NEW] Branch ver/1.20.1`
- `[MODIFY] gradle.properties` (on `ver/1.20.1`)
- `[MODIFY] src/main/java/com/dwurdy/heaphammer/platform/fabric/FabricPlatformAdapter.java` (if mapping changes apply)
- `[MODIFY] .github/workflows/sync-version-branches.yml` (on `master`)

#### 5. Measurable Acceptance Criteria & Test Surfaces
- Command: `./gradlew check test` on `ver/1.20.1` exits with code 0.
- Command: `./gradlew build` produces `build/libs/heaphammer-1.0.0-alpha.1.jar` compatible with 1.20.1.
- Unit tests: All 34 invariant tests pass without regression.

---

### Issue M2.2: Minecraft 1.18.2 World-Gen Overhaul Target Branch

#### 1. Strategic Intent & Milestone Placement
Minecraft 1.18.2 introduced the 384-block world generation height, causing severe chunk unloading and memory pressure in modded terrain generators.

#### 2. Expected Agent Responsibilities
1. Create dedicated branch `ver/1.18.2` from `master`.
2. Configure `gradle.properties` for Minecraft 1.18.2 (`minecraft_version=1.18.2`, `fabric_api_version=0.76.0+1.18.2`, `java_version=17`).
3. Verify registry and chunk ticket compatibility.
4. Execute test suite and verify build.

#### 3. Explicit Anti-Assumptions
- Do not touch domain mathematical models or scenario generators.

#### 4. Exact File Boundaries
- `[NEW] Branch ver/1.18.2`
- `[MODIFY] gradle.properties` (on `ver/1.18.2`)

#### 5. Measurable Acceptance Criteria & Test Surfaces
- `./gradlew test` succeeds on `ver/1.18.2`.

---

## Milestone M3: Legacy Bridge (Minecraft 1.16.5) & Classic Titan (Minecraft 1.12.2 Forge)

### Issue M3.1: Minecraft 1.16.5 Nether Era Support Branch

#### 1. Strategic Intent & Milestone Placement
1.16.5 is the peak pre-Caves & Cliffs LTS modding version. Adding 1.16.5 covers classic modern modpacks.

#### 2. Expected Agent Responsibilities
1. Create `ver/1.16.5` branch.
2. Configure `gradle.properties` (`minecraft_version=1.16.5`, `fabric_api_version=0.42.0+1.16.5`, `java_version=8` or `16`).
3. Replace `BuiltInRegistries` with `Registry.BLOCK` and `Registry.ENTITY_TYPE` inside `FabricPlatformAdapter` on that branch.
4. Verify compilation and test suite.

---

### Issue M3.2: Minecraft 1.12.2 Classic Forge Bridge Architecture

#### 1. Strategic Intent & Milestone Placement
Minecraft 1.12.2 remains one of the most played modded versions in Minecraft history (GTNH, SevTech). Because 1.12.2 predates standard Fabric, HeapHammer requires a dedicated Forge platform adapter (`ForgePlatformAdapter`).

#### 2. Expected Agent Responsibilities
1. Design `com.dwurdy.heaphammer.platform.forge.ForgePlatformAdapter` mapping Forge 1.12.2 lifecycle events (`ChunkEvent.Load`, `EntityJoinWorldEvent`) to HeapHammer's `PlatformAdapter`.
2. Map `ForgeChunkManager.Ticket` to HeapHammer's `ChunkTicketManager`.
3. Configure `ForgeGradle 2.3` or `CleanroomMC Loom` buildscript for Java 8.
4. Document the Forge bridge contract in `docs/FORGE_1_12_2_BRIDGE.md`.

#### 3. Explicit Anti-Assumptions
- The agent must NOT rewrite the pure domain models; the domain logic must remain identical.
