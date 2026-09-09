# Open Source Software (OSS) Guide & Governance

HeapHammer is an open-source project designed to empower the Minecraft modding community with empirical, deterministic memory leak detection and profiling tools.

---

## 1. Project Mission & Values

- **Empirical Grounding**: Every claim, regression detection, and verdict must be verifiable through live server benchmarks and reproducible test fixtures.
- **Ecosystem Portability**: We build tools that bridge across Minecraft modding eras—from the cutting-edge frontier (`1.21.1`) to classic titan modpacks (`1.12.2`).
- **Non-Invasive Diagnostic Tooling**: HeapHammer runs strictly within server tick budgets without causing TPS degradation or unmanaged state corruption.

---

## 2. Licensing & Intellectual Property

- **License**: HeapHammer is licensed under the [GNU Lesser General Public License v3.0 (LGPL-3.0)](../LICENSE).
- **Rationale**: The LGPL-3.0 protects the freedom of HeapHammer's core library while allowing server distributions, modpack aggregators, and private servers to include and run HeapHammer without requiring their private proprietary configurations or other mods to adopt the same license.
- **Third-Party Dependencies**: All runtime dependencies (e.g., Fabric API, Brigadier) remain subject to their respective upstream licenses.

---

## 3. Version Support & Branch Policy

HeapHammer enforces a clear, dual-axis branch and version support policy governing both **HeapHammer mod release versions** and **Minecraft platform versions**:

### 3.1 Mod Release Version Policy
- **`master` (Latest Production Version)**: The `master` trunk always maintains the latest stable production release of HeapHammer (currently `v1.0.0`). It serves as the authoritative source of truth for active production builds.
- **Historical Mod Releases (LTS-Only Branches)**: We maintain active Git branches for historical mod release lines **only** if they are officially designated as **Long-Term Support (LTS)** or release lines we actively commit to supporting with backported security updates and critical fixes.
- **Retirement & Tagging**: Non-supported or End-of-Life (EOL) mod versions do not retain lingering active Git branches. They are permanently archived as immutable Git release tags (e.g. `v1.0.0`).

### 3.2 Minecraft Platform Version Support Matrix
HeapHammer targets major modding eras while avoiding branch sprawl by maintaining dedicated branches **strictly for historical LTS or actively supported Minecraft versions**:

| Version Branch | Ecosystem Role | Support Tier | Maintenance Scope |
|---|---|---|---|
| **`master`** | Modern Frontier (`1.21.1`, Java 21) | **Tier 1 (Active Production Trunk)** | Canonical production codebase, new scenarios, statistical engines. |
| **`ver/1.20.1`** | Modern LTS Gold Standard | **Tier 1 (Full Support)** | Automated sync of domain features, regression fixes. |
| **`ver/1.18.2`** | World-Gen Overhaul LTS | **Tier 2 (Maintenance)** | Automated sync of domain features, critical bug fixes. |
| **`ver/1.16.5`** | Nether Legacy LTS | **Tier 2 (Maintenance)** | Automated sync of domain features, critical bug fixes. |
| **`ver/1.12.2-forge`** | Classic Titan LTS | **Tier 3 (Community Bridge)** | Forge platform adapter compatibility, community backports. |

> [!NOTE]
> **No Redundant or Intermediate Branches**:
> - Because `master` directly represents the latest production Minecraft version (`1.21.1`), no redundant `ver/1.21.1` branch is maintained.
> - Intermediate, non-LTS Minecraft versions (such as `1.20.4`, `1.19.4`, `1.17.1`) do not receive dedicated branches.

### 3.3 Branch Protection & Release Lifecycle
- **Protected Trunk**: Direct pushes to `master` and release branches are prohibited. All contributions must use topic branches (`feat/*`, `fix/*`) and reviewed PRs.
- **Milestone-Batched Releases**: Handled through staging branches (`release/v*`) to protect modpacks from update churn. See **[docs/PUBLICATION.md](PUBLICATION.md)**.
- **Strict Semantic Versioning (Zero Alpha Policy)**: We do not launch alpha, beta, or pre-release qualifiers (`alpha.1`, etc.). Version progression follows a strict rule of thumb:
  - **Major (`X.0.0`)**: Modifying something big or adding a big new feature / fundamental architectural milestone.
  - **Minor (`X.Y.0`)**: Every new feature or feature-ish enhancement.
  - **Bug Fix (`X.Y.Z`)**: Severe bug fixes that are really bad, need fixing, and cannot wait until the next scheduled minor version.
  - See **[docs/PUBLICATION.md](PUBLICATION.md)** for the complete release lifecycle.
- **Upstream Synchronization**: Verified domain improvements merged to `master` are automatically propagated to downstream historical LTS branches (`ver/*`) via GitHub Actions. If a version divergence occurs, an automated PR is raised for maintainer review.

---

## 4. Community & Contribution Standards

We welcome contributions from mod authors, modpack developers, and server administrators.

- **Contribution Guidelines**: Detailed instructions on coding conventions, hexagonal architecture rules, and pull request workflows are documented in [CONTRIBUTING.md](../CONTRIBUTING.md).
- **Code of Conduct**: We adhere to the [Contributor Covenant v2.1](../CODE_OF_CONDUCT.md). All participants are expected to uphold a welcoming, respectful, and harassment-free community.
- **Architectural Decisions**: Read [docs/DECISION.md](DECISION.md) before proposing significant architectural or algorithmic changes.
- **AI Disclosure & Accountability**: HeapHammer requires transparent disclosure when AI agents or LLMs assist in drafting code or issues. The human contributor remains 100% accountable. See [AGENTS.md](../AGENTS.md) and [CONTRIBUTING.md](../CONTRIBUTING.md#7-ai-assisted-contributions--disclosure-policy).

---

## 5. Security & Vulnerability Reporting

The security of Minecraft servers running HeapHammer is paramount.

- **Vulnerability Policy**: See [SECURITY.md](../SECURITY.md) for supported versions and instructions on private security disclosures.
- **Denial-of-Service Defense**: HeapHammer scenarios enforce strict tick execution limits (`maxOperationsPerTick`, `maxMsPerTick`) to guarantee that malicious or misconfigured commands cannot lock the dedicated server thread.

---

## 6. Release Lifecycle & Distribution

- **Batched Releases**: Handled through milestone release staging branches (`release/v*`) to protect modpacks from update churn. See **[docs/PUBLICATION.md](PUBLICATION.md)**.
- **Release Channels**:
  - GitHub Releases: Authoritative release tags and jars across all supported Minecraft versions.
  - CurseForge: [HeapHammer on CurseForge](https://www.curseforge.com/minecraft/mc-mods/heaphammer)
  - Modrinth: Official mod distribution page.
- **Automated CI/CD**: Verified via GitHub Actions and Jenkins multibranch pipelines. See [docs/JENKINS_PIPELINE.md](JENKINS_PIPELINE.md).
