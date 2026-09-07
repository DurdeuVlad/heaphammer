# Jenkins CI/CD Pipeline Guide for HeapHammer

HeapHammer includes first-class support for **Jenkins Continuous Integration and Continuous Deployment (CI/CD)** via our declarative [Jenkinsfile](../Jenkinsfile).

This guide explains how to configure Jenkins to automatically build, test, and package HeapHammer across all supported Minecraft and Java versions.

---

## 1. Architecture Overview

Because HeapHammer supports multiple Minecraft versions (from modern 1.21.1 and 1.20.1 down to 1.12.2), Jenkins must build each branch with its appropriate Java Virtual Machine:

```text
                        Jenkins Multi-Branch Pipeline
                                      │
              ┌───────────────────────┼───────────────────────┐
              ▼                       ▼                       ▼
      Branch: master           Branch: ver/1.20.1      Branch: ver/1.12.2
    (Minecraft 1.21.1)        (Minecraft 1.20.1)      (Minecraft 1.12.2)
              │                       │                       │
      Detects: java=21        Detects: java=17        Detects: java=8
              │                       │                       │
      Selects: JDK21          Selects: JDK17          Selects: JDK8
              │                       │                       │
     ./gradlew test          ./gradlew test          ./gradlew test
     ./gradlew build         ./gradlew build         ./gradlew build
```

The pipeline dynamically reads `gradle.properties` (`minecraft_version` and `java_version`) at runtime and binds the corresponding Jenkins JDK Tool automatically.

---

## 2. Jenkins Server Prerequisites

To run the pipeline, your Jenkins instance requires:

### 2.1 Required Jenkins Plugins
1. **Pipeline: Multibranch** (`workflow-multibranch`) — Automatically discovers branches (`master`, `ver/*`).
2. **Git Plugin** (`git`) — SCM integration with GitHub or GitLab.
3. **Pipeline Utility Steps** (`pipeline-utility-steps`) — Provides `readProperties` to parse `gradle.properties`.
4. **JUnit Plugin** (`junit`) — Automatically records, graphs, and trends test results across builds.

### 2.2 Configured JDK Tools (Global Tool Configuration)
In Jenkins under **Manage Jenkins** $\rightarrow$ **Tools** $\rightarrow$ **JDK Installations**, define the following JDK tool names:

| Tool Name | Version | Download / Installation Path | Used For |
|---|---|---|---|
| `JDK21` | Java 21 JDK | Eclipse Temurin 21 or OpenJDK 21 | `master` (1.21.1 production trunk), `release/v*` |
| `JDK17` | Java 17 JDK | Eclipse Temurin 17 or OpenJDK 17 | `ver/1.20.1`, `ver/1.18.2` |
| `JDK8` | Java 8 JDK | Eclipse Temurin 8 or OpenJDK 8 | `ver/1.16.5`, `ver/1.12.2-forge` |

---

## 3. Configuring the Multibranch Pipeline Job

1. In Jenkins dashboard, select **New Item**.
2. Name the project `HeapHammer` and select **Multibranch Pipeline**.
3. Under **Branch Sources**:
   - Add **Git** or **GitHub**.
   - Repository URL: `https://github.com/DurdeuVlad/heaphammer.git` (or your staging repository).
   - Add credentials if private.
4. Under **Discover Branches**:
   - Filter by name (wildcards): `master ver/*`
5. Under **Build Configuration**:
   - Mode: `by Jenkinsfile`
   - Script Path: `Jenkinsfile`
6. Under **Scan Multibranch Pipeline Triggers**:
   - Check **Periodically if not otherwise run** (e.g., every 1 hour) or configure a GitHub Webhook.
7. Click **Save**. Jenkins will immediately scan all branches and launch builds for `master` and all version branches.

---

## 4. Pipeline Parameters & Capabilities

The `Jenkinsfile` provides optional execution parameters:

- **`RUN_MATRIX_BENCHMARKS`** (Default `false`):
  When checked, the pipeline executes the live dedicated server multi-mod matrix benchmarks (`tools/run-mod-matrix-test.ps1`), testing HeapHammer against synthetic leak fixtures and archiving JSON reports.
- **`OVERRIDE_JDK`** (Default `AUTO`):
  Allows manual testing of specific JDKs (`JDK21`, `JDK17`, `JDK8`) regardless of the branch configuration.
- **`DEPLOY_PRODUCTION`** (Default `false`):
  When checked (or automatically when building a release tag `v*.*.*` or `release/*` branch), triggers the **Production Release & Deployment** stage. This stages production bundles into `dist/production/`, generates `SHA256SUMS.txt`, and publishes to GitHub Releases if release credentials are configured.

---

## 5. Artifact Archiving

Each successful Jenkins build archives:
- `build/libs/heaphammer-*.jar` — The compiled, remapped production mod jar.
- `build/testmods/*.jar` — All synthetic test mod jars for staging servers.
- `build/test-results/**/*.xml` — JUnit XML test reports parsed and tracked in Jenkins dashboards.
- `build/matrix-reports/*.json` — Empirical slope and plateau reports (when matrix benchmarks are enabled).
- `dist/production/**/*` — Production release bundle (when triggered by tag, release branch, or `DEPLOY_PRODUCTION`):
  - `heaphammer-<version>.jar` (unversioned and version-tagged mod binaries)
  - `HeapHammer_Server_Admin_Guide.pdf` (modpack administrator guide)
  - `heaphammer_logo.png` (official branding asset)
  - `SHA256SUMS.txt` (cryptographic integrity checksums)

---

## 6. Release Gating vs. Continuous Integration

To prevent release fatigue and ensure stable modpack deployments, the pipeline distinguishes routine CI builds from official release deployments:

| Trigger Condition | Target Branches | Pipeline Actions | Deployment Status |
|---|---|---|---|
| **Push / PR Merge** | `master`, `ver/*` | Compiles, executes test suite (34 tests), archives local jars. | **No Public Deployment** (CI Only) |
| **Release Branch** | `release/v*` | Full test suite, packages release candidates, archives release jars to `dist/production/`. | **Staging Gate** |
| **Release Tag** / `DEPLOY_PRODUCTION` | `v*.*.*` | Full test suite, matrix benchmarks, stages bundle to `dist/production/`, verifies SHA-256 sums, uploads to GitHub Releases. | **Official Release Deployment** (GitHub, Modrinth, CurseForge) |

### 6.1 Configuring GitHub Release Credentials in Jenkins
To enable automated GitHub Release uploads in Jenkins:
1. Under **Manage Jenkins** $\rightarrow$ **Credentials**, add a **Secret text** credential.
2. Set ID: `github-release-token`.
3. Set Secret: Your GitHub Personal Access Token (`repo` scope) or GitHub Actions token.
4. The pipeline will automatically bind `GH_TOKEN` and deploy official releases with release notes, checksums, and attached binaries.

See [docs/PUBLICATION.md](PUBLICATION.md) for the complete release lifecycle and branching workflow.

