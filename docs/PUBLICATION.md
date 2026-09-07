# HeapHammer Release & Publication Guide

This document establishes the canonical release, packaging, and distribution procedure for HeapHammer across GitHub Releases, Modrinth, and CurseForge.

---

## 1. Release Philosophy: Batched Releases vs. Per-Issue Publishing

HeapHammer strictly adheres to a **Milestone-Batched Release Model** rather than continuous deployment on every issue fix.

### Why We Do Not Publish on Every Issue Fixed:
1. **Modpack & Server Stability**: Server operators and modpack maintainers cannot manage daily version churn. Releasing on every issue creates update fatigue and risks introducing partial regressions into production servers.
2. **Deterministic Quality Gates**: A release must be validated as a coherent whole against our entire 9-scenario multi-mod matrix and live dedicated servers, rather than isolated hotfixes.
3. **Cross-Version Parity**: Because HeapHammer supports 5 major Minecraft version lines (`1.21.1`, `1.20.1`, `1.18.2`, `1.16.5`, `1.12.2`), releases must be synchronized so all versions receive consistent feature and fix milestones simultaneously.

---

## 2. The Release Lifecycle & Branching Workflow

Our release lifecycle flows through four distinct phases:

```mermaid
gitGraph
   commit id: "fix(chunk): issue #12"
   commit id: "feat(entity): issue #14"
   branch release/v1.0.0
   checkout release/v1.0.0
   commit id: "chore(release): gather & freeze v1.0.0"
   commit id: "test(matrix): full server pass"
   checkout master
   commit id: "fix(diagnostics): issue #16"
   checkout release/v1.0.0
   commit id: "tag: v1.0.0-alpha.1 [DEPLOY]"
   checkout master
   merge release/v1.0.0 id: "merge v1.0.0 to master"
   commit id: "chore(version): bump to 1.1.0-SNAPSHOT"
```

### Phase 1: Continuous Development on `master`
- All regular feature development, bug fixes, and community pull requests land continuously on `master`.
- Every commit on `master` automatically runs unit tests and build verification via CI.
- **Invariant**: Commits on `master` **never** publish or deploy artifacts to Modrinth, CurseForge, or GitHub Releases.

### Phase 2: Release Version Branch (`release/v<major>.<minor>.x`)
- When the milestone scope for a release is complete (e.g. Milestone `v1.0.0`), cut a dedicated release branch from `master`:
  ```bash
  git checkout master
  git pull origin master
  git checkout -b release/v1.0.0
  git push -u origin release/v1.0.0
  ```
- **Gathering the Work**:
  - Consolidate all closed issue fixes into release notes and changelog.
  - Finalize version strings in `gradle.properties` (`mod_version=1.0.0-alpha.1`) and `src/main/resources/fabric.mod.json`.
  - Execute full matrix verification benchmarks on live dedicated servers (`tools/run-mod-matrix-test.ps1`).
  - Feature freeze is enforced: only critical release-blocking bug fixes are cherry-picked or committed here.

### Phase 3: Merge, Tag & Deploy Gate
- Once the release candidate passes all quality gates on the release branch:
  1. **Tag the Release**:
     ```bash
     git tag -a v1.0.0-alpha.1 -m "Release v1.0.0-alpha.1: Deterministic workload & retained-memory regression framework"
     git push origin v1.0.0-alpha.1
     ```
  2. **Merge Back to `master`**:
     ```bash
     git checkout master
     git merge --no-ff release/v1.0.0 -m "chore(release): merge release/v1.0.0 into master"
     git push origin master
     ```
  3. **Trigger Deployment**:
     - Jenkins and GitHub Actions detect the official tag (`v*.*.*`) or release branch and trigger the deployment pipeline.
     - Artifacts are published to **GitHub Releases**, **Modrinth**, and **CurseForge**.

### Phase 4: Next Minor Version Transition
- Immediately after the release is merged and deployed:
  1. On `master`, bump the project version to the next minor snapshot:
     ```properties
     # gradle.properties
     mod_version=1.1.0-alpha.1-SNAPSHOT
     ```
  2. Commit and push the version bump to `master`:
     ```bash
     git commit -am "chore(version): bump master to 1.1.0-alpha.1-SNAPSHOT for next development cycle"
     git push origin master
     ```
  3. Open the next milestone (e.g. `v1.1.0`) on GitHub Issues.
  4. Routine development and issue fixes resume targeting `master`.

---

## 3. Release Architecture & Multi-Version Matrix

HeapHammer maintains 5 synchronized version lines. When a release branch is tagged and merged, artifacts are produced across the matrix:

| Minecraft Version | Target Loader | Git Branch | Java Version | Primary Output Jar |
|---|---|---|---|---|
| **1.21.1** *(Primary)* | Fabric | `master` | Java 21 | `heaphammer-1.0.0-alpha.1.jar` |
| **1.20.1** | Fabric & Forge | `ver/1.20.1` | Java 17 | `heaphammer-1.20.1-1.0.0-alpha.1.jar` |
| **1.18.2** | Fabric & Forge | `ver/1.18.2` | Java 17 | `heaphammer-1.18.2-1.0.0-alpha.1.jar` |
| **1.16.5** | Forge & Fabric | `ver/1.16.5` | Java 8 / 11 | `heaphammer-1.16.5-1.0.0-alpha.1.jar` |
| **1.12.2** | Forge | `ver/1.12.2-forge` | Java 8 | `heaphammer-1.12.2-1.0.0-alpha.1.jar` |

---

## 4. Pre-Release Verification Checklist

Execute this checklist on the `release/vX.Y.x` branch prior to tagging:

### Step 1: Unit & Integration Test Suite
```powershell
./gradlew clean test
```
- **Acceptance Criteria**: Exit code `0`. All 34 core domain tests pass with zero failures.

### Step 2: Assemble Release Jars & Testmod Fixtures
```powershell
./gradlew build buildTestmods
```
- **Acceptance Criteria**:
  - `build/libs/heaphammer-*.jar` is generated.
  - All synthetic testmod jars in `build/testmods/` compile successfully.

### Step 3: Automated Multi-Mod Matrix Verification
```powershell
powershell -ExecutionPolicy Bypass -File tools/run-mod-matrix-test.ps1
```
- **Acceptance Criteria**: Live dedicated server runs all 9 matrix scenarios, correctly verifying clean baselines and flagging synthetic retention leaks.

### Step 4: Asset & Metadata Verification
- Verify `src/main/resources/fabric.mod.json`:
  - `id`: `"heaphammer"`
  - `version`: Matches target release version.
  - `license`: `"LGPL-3.0"`
  - `icon`: `"assets/heaphammer/icon.png"` (512x512 authentic Minecraft voxel icon)
  - `environment`: `"server"` (or `"*"` with server-side only logic)
- Verify `src/main/resources/assets/heaphammer/icon.png` is present and valid PNG.

---

## 5. GitHub Releases Publication

GitHub Releases serves as the authoritative repository for release tags, release notes, and all version jar artifacts.

### 5.1 Tagging the Release
```bash
git checkout release/v1.0.0
git tag -a v1.0.0-alpha.1 -m "Release v1.0.0-alpha.1: Deterministic workload & retained-memory regression framework"
git push origin v1.0.0-alpha.1
```

### 5.2 GitHub Release Notes Template
```markdown
# HeapHammer v1.0.0-alpha.1 — Initial Public Alpha Release

HeapHammer is a deterministic stress-testing and retained-memory regression framework for modded Minecraft. It compresses days of chaotic player activity into repeatable, tick-budgeted staging cycles to produce objective, replayable leak verdicts.

### 🌟 Key Highlights
- **100% Server-Side Only**: Connecting clients do NOT need the mod installed.
- **Zero Configuration**: Drop into `mods/` and run `/hh doctor` or `/hh run chunks`.
- **Deterministic Workload Engine**: Reproducible chunk, entity, and block entity churn via fixed random seeds.
- **Statistical OLS Regression**: Distinguishes true memory leaks (`SUSPICIOUS`) from normal cache warming (`PASS (PLATEAU)`).
- **Cross-Mod Collision Detection**: Automatically captures retention bugs caused by third-party subscriber loops.
- **Non-Destructive Execution**: Strict tick budgeting (`10 ops/tick`, `15 ms/tick`) protects server TPS.

### 📦 Release Assets
- `heaphammer-1.0.0-alpha.1.jar` (Minecraft 1.21.1 Fabric)
- `heaphammer-1.20.1-1.0.0-alpha.1.jar` (Minecraft 1.20.1 Fabric/Forge)
- `heaphammer-1.18.2-1.0.0-alpha.1.jar` (Minecraft 1.18.2 Fabric/Forge)
- `heaphammer-1.16.5-1.0.0-alpha.1.jar` (Minecraft 1.16.5 Forge)
- `heaphammer-1.12.2-1.0.0-alpha.1.jar` (Minecraft 1.12.2 Forge)

### 📖 Documentation & Guides
- [Quickstart Guide](https://github.com/DurdeuVlad/heaphammer#readme)
- [Empirical Case Studies & Collision Proof](https://github.com/DurdeuVlad/heaphammer/blob/master/docs/CASE_STUDIES.md)
- [Multi-Version Architecture](https://github.com/DurdeuVlad/heaphammer/blob/master/docs/MULTI_VERSION_ARCHITECTURE.md)
```

---

## 6. Modrinth Publication Checklist

Modrinth is the primary platform for modern Fabric and NeoForge/Forge mods.

### 6.1 Project Settings
- **Name**: `HeapHammer`
- **Slug**: `heaphammer`
- **Summary**: `Deterministic server stress testing and retained-memory regression framework for modded Minecraft.`
- **Project Type**: `Mod`
- **Client / Server Environment**:
  - **Client**: `Unsupported` (or `Optional` for singleplayer profiling)
  - **Server**: `Required`
- **License**: `LGPL-3.0-only`
- **Categories / Tags**: `Server Utility`, `Optimization`, `Management`, `Diagnostics`
- **Links**:
  - Source Code: `https://github.com/DurdeuVlad/heaphammer`
  - Issues: `https://github.com/DurdeuVlad/heaphammer/issues`
  - Documentation: `https://github.com/DurdeuVlad/heaphammer/tree/master/docs`

### 6.2 Brand Imagery
- **Icon**: Upload `assets/heaphammer_logo.png` (authentic Minecraft voxel anvil & netherite hammer).
- **Gallery / Banner**: Upload `assets/heaphammer_banner.png` (16:9 isometric floating Minecraft chunks with redstone automation).

### 6.3 Version Upload
- **Version Number**: `1.0.0-alpha.1`
- **Version Title**: `HeapHammer 1.0.0-alpha.1 (1.21.1 Fabric)`
- **Changelog**: Copy release highlights from Section 5.2.
- **Supported Loaders**: `Fabric`
- **Game Versions**: `1.21.1`
- **Release Type**: `Alpha`
- **Primary File**: Upload `build/libs/heaphammer-1.0.0-alpha.1.jar`.

---

## 7. CurseForge Publication Checklist

CurseForge reaches large modpack creators, hosting providers, and legacy Forge communities.

### 7.1 Project Configuration
- **Name**: `HeapHammer`
- **Category**: `Server Utilities` -> `Administrative Tools`
- **Brief Description**: `Deterministic stress testing and retained-memory regression framework for dedicated Minecraft servers.`
- **Primary Logo**: Upload `assets/heaphammer_logo.png`.
- **Side**: `Server` (Client: `Not Needed` / `Allowed`)
- **License**: `GNU Lesser General Public License v3.0`

### 7.2 File Distribution
- Upload `heaphammer-1.0.0-alpha.1.jar` under **Alpha Files**.
- Set **Release Type** to `Alpha`.
- Supported Minecraft Versions: `1.21.1`
- Mod Loader: `Fabric`
- Java Version: `Java 21`

---

## 8. Automated Jenkins CI/CD Release Gate

For unattended builds and automated multi-branch deployments, refer to [`docs/JENKINS_PIPELINE.md`](JENKINS_PIPELINE.md).

In accordance with our release philosophy:
1. **Pushes to `master`**: Trigger continuous integration (compile, test, and archive artifacts). **No external publication**.
2. **Pushes to `release/*` or `v*` tags**: Trigger the official release gate stage, verifying artifacts, producing release archives, and publishing to distribution channels.
