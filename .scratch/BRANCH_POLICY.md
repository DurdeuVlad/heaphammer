# Open Source Branch Policy & Repository Alignment Plan

This scratchpad records the unified branch policy across both Mod Release Versions and Minecraft Platform Versions.

## 1. Mod Release Version Branch Policy
- **`master`**: Always contains the **latest production version of HeapHammer** (currently `1.0.0`).
- **Historical Mod Releases**: We maintain branches *only* for historical mod versions that are officially designated **LTS** or that we actively commit to supporting with backported security/critical fixes. Non-supported or EOL mod versions do not retain active branches—they are permanently preserved via Git tags (`vX.Y.Z`).

## 2. Minecraft Platform Version Branch Policy
- **`master`**: Serves as the primary production trunk targeting the **latest production Minecraft version** (`1.21.1`, Fabric, Java 21).
- **Historical Minecraft Versions (`ver/*`)**: Kept **exclusively for Long-Term Support (LTS) or versions we actively commit to supporting** (`ver/1.20.1`, `ver/1.18.2`, `ver/1.16.5`, `ver/1.12.2-forge`).
- **Retire `ver/1.21.1`**: Because `master` already serves as the production line for 1.21.1, the redundant `ver/1.21.1` branch is retired across CI/CD workflows, scripts, and Git refs.
- Intermediate non-LTS Minecraft versions (e.g. 1.20.4, 1.19.4) do not receive dedicated branches.

## 3. Implementation Checklist
- `docs/OSS.md`: Formalize dual-axis branch policy (Mod Version LTS Support & Minecraft Version LTS Support).
- `docs/MULTI_VERSION_ARCHITECTURE.md`: Remove `ver/1.21.1` from diagrams; clarify `master` is the 1.21.1 production trunk.
- `CONTRIBUTING.md`: Update Section 3 branching model to reflect dual-axis policy and remove `ver/1.21.1`.
- `docs/DECISION.md`: Update ADR-003 with rationale for mod LTS branches, Minecraft LTS branches, and retiring redundant trunk branches.
- `docs/PUBLICATION.md`: Update artifact matrix to specify `master` as 1.21.1 release line.
- `docs/JENKINS_PIPELINE.md`: Remove `ver/1.21.1` from `JDK21` branch mapping.
- `SECURITY.md`: Update supported versions table to list actual supported mod & MC combinations (`1.21.1 master`, `1.20.1`, `1.18.2`, `1.16.5`, `1.12.2`).
- `README.md`: Update line 265 parameter rejection description.
- `.github/workflows/sync-version-branches.yml`: Remove `ver/1.21.1` from matrix.
- `tools/sync-version-branches.ps1`: Remove `ver/1.21.1` from default target branches.
- `.github/ISSUE_TEMPLATE/bug_report.yml`: Update version placeholder and Minecraft version dropdown to match supported LTS lines.
- Git branch hygiene: Delete local and remote `ver/1.21.1`.
