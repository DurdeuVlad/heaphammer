# HeapHammer Documentation Hub

Welcome to the HeapHammer documentation directory. This repository contains the complete architectural specifications, empirical benchmarks, multi-version platform accommodation guides, and decision records for HeapHammer.

---

## 📚 Documentation Index

### 1. Architecture & Portability
- **[MULTI_VERSION_ARCHITECTURE.md](MULTI_VERSION_ARCHITECTURE.md)**:
  Comprehensive guide to HeapHammer's Hexagonal Architecture (Ports & Adapters), modding era analysis (1.21.1 down to 1.12.2), cross-version compatibility matrix, branching strategy, and automated synchronization.
- **[FORGE_1_12_2_BRIDGE.md](FORGE_1_12_2_BRIDGE.md)**:
  Architectural specification for the classic Minecraft 1.12.2 Forge platform adapter, reflection ticket bridge, and lifecycle event mappings.
- **[DECISION.md](DECISION.md)**:
  Durable Architectural Decision Records (ADRs) explaining the rationale behind zero-Minecraft domain coupling, OLS regression, matrix branching, and dynamic Jenkins toolchains.

### 2. Empirical Verification & Case Studies
- **[CASE_STUDIES.md](CASE_STUDIES.md)**:
  Live dedicated server benchmarks, mathematical retention model formulation ($y = mx + b$, $R^2$), empirical test matrix across single-subsystem leaks, multi-subsystem leaks, and cross-mod subscriber collisions. Includes the 4-step modpack leak triage playbook.

### 3. CI/CD, Release & Operations
- **[PUBLICATION.md](PUBLICATION.md)**:
  Canonical release and publishing guide for GitHub Releases, Modrinth, and CurseForge, including pre-release verification checklists, version packaging, and metadata standards.
- **[JENKINS_PIPELINE.md](JENKINS_PIPELINE.md)**:
  Guide to setting up the multibranch Jenkins CI/CD pipeline, dynamic JDK tool resolution (`JDK21`, `JDK17`, `JDK8`), and test artifact archiving.

### 4. Project Governance & Open Source
- **[OSS.md](OSS.md)**:
  Open-source governance, licensing intent (LGPL-3.0), 5-tier version support policy, and release channels.
- **Temporary Planning & Scratchpad**:
  Active development scratchpads, task breakdowns, and issue plans reside in [`.scratch/`](../.scratch).

---

## 🎯 Quick Navigation by Role

### For Server Administrators & Modpack Creators
1. Start with the **[Modpack Leak Triage Playbook](CASE_STUDIES.md#7-modpack-leak-triage-playbook-for-server-administrators)** to diagnose server lag, memory spikes, or OOM crashes.
2. Review the **[Cross-Mod Collision Case Study](CASE_STUDIES.md#5-case-study-3-the-ghost-leak--cross-mod-accidental-collision)** to understand how two clean mods can combine into a severe memory leak.
3. Check the command reference in the root [README.md](../README.md).

### For Mod Developers & Contributors
1. Read the **[Hexagonal Portability Boundary](MULTI_VERSION_ARCHITECTURE.md#2-architectural-portability-boundary-hexagonal-architecture)** to understand why core domain classes must never import `net.minecraft.*`.
2. Follow **[CONTRIBUTING.md](../CONTRIBUTING.md)** for coding standards, pull request processes, and test requirements.
3. Review **[DECISION.md](DECISION.md)** before proposing changes to core mathematical or architectural models.

### For DevOps & Build Engineers
1. Refer to **[JENKINS_PIPELINE.md](JENKINS_PIPELINE.md)** for multibranch pipeline setup.
2. Inspect **[MULTI_VERSION_ARCHITECTURE.md](MULTI_VERSION_ARCHITECTURE.md#6-automated-branch-synchronization-workflow)** to see how commits to `master` automatically propagate to all version branches.
