# HeapHammer 1.1.1

HeapHammer 1.1.1 is a release distribution, packaging, and platform presentation maintenance release focusing on ease of installation and mod repository hygiene across CurseForge, Modrinth, and GitHub Releases.

---

## Highlights & Improvements

### 1. CurseForge & Modrinth Distribution Streamlining
- **Scoping to Canonical LTS Targets**: Active production distribution across CurseForge, Modrinth, and GitHub is now strictly scoped to the 6 major Minecraft LTS lines where >98% of community modpack and server traffic resides:
  - **1.21.1** (Fabric primary, NeoForge nested)
  - **1.20.1** (Fabric primary, Forge nested)
  - **1.18.2** (Fabric primary, Forge nested)
  - **1.16.5** (Fabric primary, Forge nested)
  - **1.12.2** (Forge)
  - **1.7.10** (Forge)
- **Eliminating Multi-Version Release Spam**: Transitional/intermediate branches (`1.21.4`, `1.20.6`, `1.20.4`, `1.19.4`, `1.19.2`, `1.17.1`, `1.15.2`, `1.14.4`) remain preserved in Git history and multi-branch synchronization, but are retired from active platform uploads.

### 2. Deterministic 2-Phase Deployment (Download Button Roulette Fix)
- Overhauled `.github/workflows/release.yml` with a two-phase publishing architecture:
  - **Phase 1 (`publish-platforms-secondary`)**: Uploads historical LTS targets (`1.7.10`, `1.12.2`, `1.16.5`, `1.18.2`, `1.20.1`) and secondary loaders (`1.21.1 NeoForge`) in parallel.
  - **Phase 2 (`publish-platforms-primary`)**: Sequentially uploads the modern standard (`1.21.1 Fabric`) after secondary uploads settle.
- **Precedence Guarantee**: CurseForge's main overview page **"Download"** button serves whichever file holds the newest upload timestamp. Uploading `1.21.1 Fabric` last guarantees that clicking "Download" on CurseForge always delivers the modern 1.21.1 Fabric standard instead of an obsolete Forge JAR.

### 3. High-Visibility Bracketed Display Names
- File display names on CurseForge and Modrinth now follow a strict bracketed format:
  `[<Loader> <MC>] HeapHammer <ModVer>`
  - Examples: `[Fabric 1.21.1] HeapHammer 1.1.1`, `[NeoForge 1.21.1] HeapHammer 1.1.1`, `[Forge 1.20.1] HeapHammer 1.1.1`.
- Prevents critical loader and Minecraft version strings from getting truncated in narrow launcher cards and mobile layouts.

### 4. One Active Release Per Version Policy
- Established formal repository standards in `docs/PUBLICATION.md`: for every supported Minecraft version, there must be strictly **ONE active release** (the latest mod version).
- Historical patch versions (`1.0.0`, `1.0.1`, `1.0.2`) and retired transitional branches are archived on the CurseForge author dashboard, shrinking the public file list from 58+ clutter entries down to the 10 canonical LTS targets.

### 5. Platform Adapters & Staging Hygiene
- Synchronized all internal mod-version fallbacks, fingerprint captures, and unit test assertions from `1.1.0` to `1.1.1` across `FabricPlatformAdapter`, `ForgePlatformAdapter`, and `NeoForgePlatformAdapter`.
- Updated `tools/publish-release.ps1` to automatically purge stale build artifacts from `dist/production/` before staging release binaries.
