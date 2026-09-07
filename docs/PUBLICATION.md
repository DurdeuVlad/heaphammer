# HeapHammer Release & Publication Guide

This document establishes the canonical release, packaging, and distribution procedure for HeapHammer across GitHub Releases, Modrinth, and CurseForge.

---

## 1. Batched Release Philosophy & Lifecycle

HeapHammer uses a **Milestone-Batched Release Model**. We do **not** publish on individual issue fixes to prevent modpack update fatigue and ensure releases are verified against our full multi-mod test matrix.

```mermaid
gitGraph
   commit id: "fix(chunk): issue #12"
   commit id: "feat(entity): issue #14"
   branch release/v1.0.0
   checkout release/v1.0.0
   commit id: "chore: gather & freeze v1.0.0"
   commit id: "test: matrix validation"
   checkout master
   commit id: "fix(diag): issue #16"
   checkout release/v1.0.0
   commit id: "tag: v1.0.0 [DEPLOY]"
   checkout master
   merge release/v1.0.0 id: "merge to master"
   branch release/v1.1.0
   commit id: "chore: open next release v1.1.0"
```

### The 4-Phase Release Cycle

| Phase | Branch | Actions | Deployment |
|---|---|---|---|
| **1. Development** | Topic Branches (`feat/*`, `fix/*`) | Features and fixes developed in isolation with test-first evidence. | **None** |
| **2. Active Release** | `release/v1.1.0` | Active release line gathering upcoming features. CI compiles and verifies 34 tests. | **Staging Gate** |
| **3. Release Gate** | `release/v<M>.<m>.x` | Tag release (`v1.0.0`). Merge back to `master` and sync downstream to `ver/*`. | **Publish to GitHub, Modrinth, CurseForge** |
| **4. Next Cycle** | `release/v<M>.<m+1>.0` | Cut next release branch, bump `gradle.properties` (`1.1.0-alpha.1`), resume development. | **Development Resumes** |

---

## 2. Multi-Version Artifact Matrix

When a release branch is tagged and deployed, artifacts are built across the 5 supported version lines:

| Minecraft Version | Loader | Git Branch | Java Target | Release JAR |
|---|---|---|---|---|
| **1.21.1** *(Primary)* | Fabric | `master` / `release/v*` | Java 21 | `heaphammer-1.0.0.jar` |
| **1.20.1** | Fabric & Forge | `ver/1.20.1` | Java 17 | `heaphammer-1.20.1-1.0.0.jar` |
| **1.18.2** | Fabric & Forge | `ver/1.18.2` | Java 17 | `heaphammer-1.18.2-1.0.0.jar` |
| **1.16.5** | Forge & Fabric | `ver/1.16.5` | Java 8 / 11 | `heaphammer-1.16.5-1.0.0.jar` |
| **1.12.2** | Forge | `ver/1.12.2-forge` | Java 8 | `heaphammer-1.12.2-1.0.0.jar` |

---

## 3. Pre-Release Verification Checklist

Run these quality gates on the `release/v*` branch before tagging:

```powershell
# 1. Run unit & integration test suite (34 tests must pass)
./gradlew clean test

# 2. Package release JAR and synthetic testmod fixtures
./gradlew build buildTestmods

# 3. Run live dedicated server multi-mod matrix verification
powershell -ExecutionPolicy Bypass -File tools/run-mod-matrix-test.ps1
```

- [ ] `fabric.mod.json`: Verify `id: "heaphammer"`, `version`, `license: "LGPL-3.0"`, and `environment: "*"`.
- [ ] Brand Assets: Verify `icon.png` (512x512) and `assets/heaphammer_banner.png` (16:9).

---

## 4. Release Execution: Step-by-Step

### Step 1: Tag and Push Release
```bash
git checkout release/v1.0.0
git tag -a v1.0.0 -m "Release v1.0.0: Deterministic Minecraft server stress testing and retained-memory regression detector"
git push origin v1.0.0
```

### Step 2: Merge Back to Master
```bash
git checkout master
git merge --no-ff release/v1.0.0 -m "chore(release): merge release/v1.0.0 into master"
git push origin master
```

### Step 3: Cut Next Release Branch
```bash
git checkout -b release/v1.1.0
# Update mod_version=1.1.0-alpha.1 in gradle.properties
git commit -am "chore(version): initialize release/v1.1.0 development line"
git push origin release/v1.1.0
```

---

## 5. Platform Distribution Configuration

| Configuration Field | GitHub Releases | Modrinth | CurseForge |
|---|---|---|---|
| **Project Name** | `HeapHammer` | `HeapHammer` | `HeapHammer` |
| **Category** | Release Tag | `Server Utility`, `Optimization` | `Server Utilities` $\to$ `Administrative` |
| **Client / Server Side** | N/A | **Server: Required**, Client: Unsupported | **Server Only** (Client: Not Needed) |
| **License** | LGPL-3.0 | LGPL-3.0-only | GNU LGPL v3.0 |
| **Primary File** | `build/libs/heaphammer-*.jar` | `build/libs/heaphammer-*.jar` | `build/libs/heaphammer-*.jar` |
| **Icon Asset** | `assets/heaphammer_logo.png` | `assets/heaphammer_logo.png` | `assets/heaphammer_logo.png` |
| **Banner Asset** | `assets/heaphammer_banner.png` | `assets/heaphammer_banner.png` | `assets/heaphammer_banner.png` |

---

## 6. Automated CI/CD Gating

- **Pushes to `master` / `ver/*`**: Run continuous integration (compile, test, archive artifacts). **Never deploy**.
- **Tags matching `v*.*.*` or pushes to `release/*`**: Trigger the `Release Deployment Gate` stage in `Jenkinsfile`, publishing verified production jars.
