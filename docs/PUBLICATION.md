# HeapHammer Release & Publication Guide

This document establishes the canonical release, packaging, and distribution procedure for HeapHammer across GitHub Releases, Modrinth, and CurseForge.

---

## 1. Release Architecture & Version Matrix

HeapHammer is engineered under a **Hexagonal Architecture** where core domain logic is decoupled from Minecraft runtime classes. The project maintains 5 version branches synchronized with upstream `master`:

| Minecraft Version | Target Loader | Git Branch | Java Version | Primary Output Jar |
|---|---|---|---|---|
| **1.21.1** *(Primary)* | Fabric | `master` | Java 21 | `heaphammer-1.0.0-alpha.1.jar` |
| **1.20.1** | Fabric & Forge | `ver/1.20.1` | Java 17 | `heaphammer-1.20.1-1.0.0-alpha.1.jar` |
| **1.18.2** | Fabric & Forge | `ver/1.18.2` | Java 17 | `heaphammer-1.18.2-1.0.0-alpha.1.jar` |
| **1.16.5** | Forge & Fabric | `ver/1.16.5` | Java 8 / 11 | `heaphammer-1.16.5-1.0.0-alpha.1.jar` |
| **1.12.2** | Forge | `ver/1.12.2-forge` | Java 8 | `heaphammer-1.12.2-1.0.0-alpha.1.jar` |

---

## 2. Pre-Release Verification Checklist

Before tagging any release or uploading artifacts, verify that every quality gate passes locally on `master`:

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
  - `build/libs/heaphammer-1.0.0-alpha.1.jar` is generated.
  - All synthetic testmod jars in `build/testmods/` compile successfully.

### Step 3: Automated Multi-Mod Matrix Verification
```powershell
powershell -ExecutionPolicy Bypass -File tools/run-mod-matrix-test.ps1
```
- **Acceptance Criteria**: Live dedicated server runs all 9 matrix scenarios, correctly verifying clean baselines and flagging synthetic retention leaks.

### Step 4: Asset & Metadata Verification
- Verify `src/main/resources/fabric.mod.json`:
  - `id`: `"heaphammer"`
  - `version`: `"1.0.0-alpha.1"`
  - `license`: `"LGPL-3.0"`
  - `icon`: `"assets/heaphammer/icon.png"` (512x512 authentic Minecraft voxel icon)
  - `environment`: `"server"` (or `"*"` with server-side only logic)
- Verify `src/main/resources/assets/heaphammer/icon.png` is present and valid PNG.

---

## 3. GitHub Releases Publication

GitHub Releases serves as the authoritative repository for release tags, release notes, and all version jar artifacts.

### 3.1 Tagging the Release
```bash
git checkout master
git pull origin master
git tag -a v1.0.0-alpha.1 -m "Release v1.0.0-alpha.1: Deterministic workload & retained-memory regression framework"
git push origin v1.0.0-alpha.1
```

### 3.2 GitHub Release Notes Template
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

## 4. Modrinth Publication Checklist

Modrinth is the primary platform for modern Fabric and NeoForge/Forge mods.

### 4.1 Project Settings
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

### 4.2 Brand Imagery
- **Icon**: Upload `assets/heaphammer_logo.png` (authentic Minecraft voxel anvil & netherite hammer).
- **Gallery / Banner**: Upload `assets/heaphammer_banner.png` (16:9 isometric floating Minecraft chunks with redstone automation).

### 4.3 Version Upload
- **Version Number**: `1.0.0-alpha.1`
- **Version Title**: `HeapHammer 1.0.0-alpha.1 (1.21.1 Fabric)`
- **Changelog**: Copy release highlights from Section 3.2.
- **Supported Loaders**: `Fabric`
- **Game Versions**: `1.21.1`
- **Release Type**: `Alpha`
- **Primary File**: Upload `build/libs/heaphammer-1.0.0-alpha.1.jar`.

---

## 5. CurseForge Publication Checklist

CurseForge reaches large modpack creators, hosting providers, and legacy Forge communities.

### 5.1 Project Configuration
- **Name**: `HeapHammer`
- **Category**: `Server Utilities` -> `Administrative Tools`
- **Brief Description**: `Deterministic stress testing and retained-memory regression framework for dedicated Minecraft servers.`
- **Primary Logo**: Upload `assets/heaphammer_logo.png`.
- **Side**: `Server` (Client: `Not Needed` / `Allowed`)
- **License**: `GNU Lesser General Public License v3.0`

### 5.2 File Distribution
- Upload `heaphammer-1.0.0-alpha.1.jar` under **Alpha Files**.
- Set **Release Type** to `Alpha`.
- Supported Minecraft Versions: `1.21.1`
- Mod Loader: `Fabric`
- Java Version: `Java 21`

---

## 6. Automated Jenkins CI/CD Pipeline

For unattended builds and automated multi-branch deployments, refer to [`docs/JENKINS_PIPELINE.md`](JENKINS_PIPELINE.md).

The pipeline automatically:
1. Triggers on tag pushes matching `v*.*.*`.
2. Checks out each version branch (`master`, `ver/1.20.1`, `ver/1.18.2`, `ver/1.16.5`, `ver/1.12.2-forge`).
3. Executes `./gradlew clean test build` in parallel matrix containers.
4. Collects and publishes artifacts to the GitHub Release draft.

---

## 7. Post-Release Monitoring

Following a public release:
1. **GitHub Issues**: Monitor incoming bug reports using the issue template standard defined in [`AGENTS.md`](../AGENTS.md#5-how-to-write-a-high-value-issue).
2. **Community Feedback**: Check Modrinth/CurseForge comments for modpack compatibility inquiries.
3. **Reproducibility**: When a user reports a memory leak, request their `/hh report show last` JSON and replay command.
