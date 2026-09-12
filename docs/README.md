# 📚 HeapHammer Documentation Hub

Welcome to the HeapHammer documentation center. Here you will find architectural specifications, live dedicated server benchmarks, multi-version platform accommodation guides, and release procedures.

---

## 🎯 Navigation by Role

| Role | Start Here | Recommended Reading |
|---|---|---|
| **Server Admins & Modpack Creators** | **[SERVER_ADMIN_GUIDE.md](SERVER_ADMIN_GUIDE.md)** (Quickstart & Triage) | [CASE_STUDIES.md](CASE_STUDIES.md) • [README.md](../README.md#quickstart) |
| **Mod Developers & Contributors** | **[CONTRIBUTING.md](../CONTRIBUTING.md)** (Contribution Standards) | [MULTI_VERSION_ARCHITECTURE.md](MULTI_VERSION_ARCHITECTURE.md) • [DECISION.md](DECISION.md) • [AGENTS.md](../AGENTS.md) |
| **DevOps & Release Engineers** | **[PUBLICATION.md](PUBLICATION.md)** (Release & Distribution) | [BUILD_TARGETS.md](BUILD_TARGETS.md) • [MULTI_VERSION_ARCHITECTURE.md](MULTI_VERSION_ARCHITECTURE.md#5-automated-branch-synchronization) |

---

## 📖 Complete Document Map

### 1. Architecture & Platform Portability
- **[MULTI_VERSION_ARCHITECTURE.md](MULTI_VERSION_ARCHITECTURE.md)**: Hexagonal Architecture (Ports & Adapters), Minecraft version era analysis (1.21.1 to 1.12.2), compatibility matrix, and automated branch synchronization.
- **[FORGE_1_12_2_BRIDGE.md](FORGE_1_12_2_BRIDGE.md)**: Platform adapter and reflection-decoupled bridge specification for classic Minecraft 1.12.2 Forge.
- **[DECISION.md](DECISION.md)**: Architectural Decision Records (ADRs) explaining domain isolation, OLS regression math, branch matrix, and release gating.

### 2. Empirical Benchmarks & Leak Triage
- **[SERVER_ADMIN_GUIDE.md](SERVER_ADMIN_GUIDE.md)**: Non-technical quickstart guide for server admins and modpack creators. Explains how to catch 3-day memory leaks in 2 minutes.
- **[CASE_STUDIES.md](CASE_STUDIES.md)**: Live dedicated server test matrix (9 scenarios), OLS mathematical retention formulation ($y = mx + b$), empirical cross-mod collision proof, and the 4-step modpack leak triage playbook.

### 3. Release & Operations
- **[PUBLICATION.md](PUBLICATION.md)**: Canonical release lifecycle, milestone-batched release branching model (`release/v*`), strict Semantic Versioning (Zero Alpha policy), verification checklists, and Modrinth/CurseForge packaging.
- **[BUILD_TARGETS.md](BUILD_TARGETS.md)**: Canonical version × loader target matrix, nested `loaders/` build layout, per-loader toolchain map, and how to add a new loader target.

### 4. Governance & Community
- **[OSS.md](OSS.md)**: Open-source governance, licensing policy (LGPL-3.0), dual-axis branch strategy, and historical LTS version maintenance roadmap.
- **[CONTRIBUTING.md](../CONTRIBUTING.md)**: Engineering guidelines, conventional commits, test-first proof (RED $\to$ GREEN), and AI disclosure standards.
- **[AGENTS.md](../AGENTS.md)**: Machine-readable agent constitution, invariant rules, and issue reporting contract.
