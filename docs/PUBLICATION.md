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

### The Versioning Contract: Strict Major.Minor.Patch (Zero Alpha Policy)

HeapHammer adheres to a strict, production-oriented Semantic Versioning standard (`MAJOR.MINOR.PATCH`).

> [!IMPORTANT]
> **Zero Alpha / Pre-Release Policy**:
> We do **not** launch alpha, beta, or pre-release versions (no `alpha.1`, `-beta`, or `-dev` tags in released artifacts or version strings). Every release launched on GitHub, Modrinth, and CurseForge is an official, production-ready release.

#### Version Progression Rule of Thumb:
1. **Major Releases (`X.0.0`)**:
   - Triggered when modifying something big or adding a big new feature / fundamental architectural milestone (e.g. cross-loader paradigm shifts, breaking API evolutions, major framework overhauls).
2. **Minor Releases (`X.Y.0`)**:
   - Triggered for every new feature or feature-ish enhancement (e.g. new scenario executors, new detection heuristics, external workload adapter integrations).
3. **Bug Fix / Patch Releases (`X.Y.Z`)**:
   - Reserved exclusively for bug fixes that are really bad, need fixing, and cannot wait until the next scheduled minor version.

---

### The 4-Phase Release Cycle

| Phase | Branch | Actions | Deployment |
|---|---|---|---|
| **1. Development** | Topic Branches (`feat/*`, `fix/*`) | Features and fixes developed in isolation with test-first evidence. | **None** |
| **2. Active Release** | `release/v<M>.<m>.0` | Staging line gathering upcoming features. CI compiles and verifies unit and integration tests. | **Staging Gate** |
| **3. Release Gate** | `release/v<M>.<m>.x` | Tag release (`v1.0.0`). Merge back to `master` and sync downstream to historical LTS branches (`ver/*`). | **Publish to GitHub, Modrinth, CurseForge** |
| **4. Next Cycle & LTS Decision** | `release/v<M>.<m+1>.0` | If the prior release line is an official LTS line, retain its maintenance branch; otherwise retire the branch. Cut next release branch, bump `gradle.properties`, and resume development. | **Development Resumes** |

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

Run these automated quality gates before tagging:

```bash
# 1. Run unit test suite
./gradlew test

# 2. Run automated live-server adversarial integration suite
./gradlew adversarialServerTest

# 3. Package release JAR and synthetic testmod fixtures
./gradlew build buildTestmods
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

### Step 3: Cut Next Minor/Patch Release Branch
```bash
git checkout -b release/v1.1.0
# Update mod_version=1.1.0 in gradle.properties (strictly X.Y.Z, no alpha tags)
git commit -am "chore(version): initialize release/v1.1.0 development line"
git push origin release/v1.1.0
```

---

## 5. Platform Distribution Configuration

| Configuration Field | GitHub Releases | Modrinth | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/heaphammer) |
|---|---|---|---|
| **Project Name / URL** | `HeapHammer` | `HeapHammer` | [`heaphammer`](https://www.curseforge.com/minecraft/mc-mods/heaphammer) |
| **Project ID** | N/A | N/A | `1687734` |
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

### GitHub Actions Auto-Deployment (`release.yml`)

Production releases are now deployed automatically via `.github/workflows/release.yml`:

- **Trigger**: Pushing a `v*.*.*` tag (e.g. `git tag v1.0.0 && git push origin v1.0.0`), or manual `workflow_dispatch`.
- **Build**: Parallel matrix builds across all 5 supported Minecraft version branches (`master`, `ver/1.20.1`, `ver/1.18.2`, `ver/1.16.5`, `ver/1.12.2-forge`).
- **Publish**: Single `Kir-Antipov/mc-publish@v3.3` step publishes all JARs to GitHub Releases, Modrinth, and CurseForge simultaneously. Game versions and loaders are auto-detected from each JAR's embedded `fabric.mod.json`.
- **Secrets required** (configured in repo Settings → Secrets and variables → Actions):
  - `CURSEFORGE_TOKEN` — CurseForge API token (upload scope on project `1687734`)
  - `MODRINTH_TOKEN` — Modrinth API token (write-version scope on the `heaphammer` project)
  - `GITHUB_TOKEN` — auto-provided by GitHub Actions

The Jenkins `Production Release & Deployment` stage is deprecated for tag-triggered releases; `tools/publish-release.ps1` remains available for local/manual staging.
