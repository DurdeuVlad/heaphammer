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

## 3. Version Support & Maintenance Policy

HeapHammer maintains 5 active release branches corresponding to major modding eras:

| Version Branch | Ecosystem Status | Support Tier | Maintenance Scope |
|---|---|---|---|
| **`master`** | Modern Frontier (`1.21.1`) | **Tier 1 (Active Development)** | New features, scenarios, statistical enhancements, bug fixes. |
| **`ver/1.20.1`** | Modern LTS Gold Standard | **Tier 1 (Full Support)** | Automated sync of all domain features, regression fixes. |
| **`ver/1.18.2`** | World-Gen Overhaul LTS | **Tier 2 (Maintenance)** | Automated sync of domain features, critical bug fixes. |
| **`ver/1.16.5`** | Nether Legacy Era | **Tier 2 (Maintenance)** | Automated sync of domain features, critical bug fixes. |
| **`ver/1.12.2-forge`** | Classic Titan Era | **Tier 3 (Community Bridge)** | Forge platform adapter compatibility, community backports. |

### Upstream Synchronization
All improvements made to `master` are automatically propagated to downstream version branches via GitHub Actions. If a version divergence occurs, an automated PR is raised for maintainer review.

---

## 4. Community & Contribution Standards

We welcome contributions from mod authors, modpack developers, and server administrators.

- **Contribution Guidelines**: Detailed instructions on coding conventions, hexagonal architecture rules, and pull request workflows are documented in [CONTRIBUTING.md](../CONTRIBUTING.md).
- **Code of Conduct**: We adhere to the [Contributor Covenant v2.1](../CODE_OF_CONDUCT.md). All participants are expected to uphold a welcoming, respectful, and harassment-free community.
- **Architectural Decisions**: Read [docs/DECISION.md](DECISION.md) before proposing significant architectural or algorithmic changes.

---

## 5. Security & Vulnerability Reporting

The security of Minecraft servers running HeapHammer is paramount.

- **Vulnerability Policy**: See [SECURITY.md](../SECURITY.md) for supported versions and instructions on private security disclosures.
- **Denial-of-Service Defense**: HeapHammer scenarios enforce strict tick execution limits (`maxOperationsPerTick`, `maxMsPerTick`) to guarantee that malicious or misconfigured commands cannot lock the dedicated server thread.

---

## 6. Release Lifecycle & Distribution

- **Alpha Releases**: Tagged directly from passing version branch builds (e.g. `v1.0.0-alpha.1-1.21.1`).
- **Release Channels**:
  - GitHub Releases: All compiled jars across all 5 Minecraft versions.
  - Modrinth & CurseForge: Official mod distributions for server administrators and modpack creators.
- **Automated CI/CD**: Every commit is verified against our multi-version matrix via GitHub Actions and Jenkins. See [docs/JENKINS_PIPELINE.md](JENKINS_PIPELINE.md).
