# Branch Policy & Repository Alignment

This scratchpad records the unified branch policy across both Mod Release Versions and Minecraft Platform Versions. (Refreshed for the 1.0.2 nested-loader build model.)

## 1. Mod Release Version Branch Policy
- **`master`**: Always contains the **latest production version of HeapHammer**.
- **Historical Mod Releases**: We maintain branches *only* for historical mod versions that are officially designated **LTS** or that we actively commit to supporting with backported security/critical fixes. Non-supported or EOL mod versions do not retain active branches—they are permanently preserved via Git tags (`vX.Y.Z`).

## 2. Minecraft Platform Version Branch Policy
- **`master`**: Serves as the primary production trunk targeting the **latest production Minecraft version** (`1.21.1`, Fabric + NeoForge, Java 21).
- **Version Branches (`ver/*`)**: One branch per supported Minecraft version — currently 13:
  `ver/1.21.4`, `ver/1.20.6`, `ver/1.20.4`, `ver/1.20.1`, `ver/1.19.4`, `ver/1.19.2`,
  `ver/1.18.2`, `ver/1.17.1`, `ver/1.16.5`, `ver/1.15.2`, `ver/1.14.4`,
  `ver/1.12.2-forge`, `ver/1.7.10-forge`.
- **Loaders are not branches**: Extra loaders are `loaders/<loader>/` nested Gradle builds inside each version branch (see `docs/BUILD_TARGETS.md`). No `*-neoforge` / `*-forge` branch proliferation beyond the two classic Forge branches whose *root* build is Forge.
- **Sync**: `sync-version-branches.yml` merges `master` into every `ver/*` branch on each master push, runs the root test suite plus every nested loader suite, pushes on green, and opens a `needs-version-adaptation` PR otherwise. Branches where a propagated loader cannot exist delete its `loaders/<loader>/` dir once via that PR.

## 3. Release & Deployment Policy
- **Tag-triggered**: `git tag v*.*.* && git push` → `release.yml` builds **and tests** all 14 branch targets (root + nested loaders), then publishes to GitHub Releases, Modrinth, and CurseForge via `mc-publish`. No manual building or uploading.
- **Naming**: `heaphammer-<mc>-<loader>-<modver>.jar`; mod version forced uniform via `-Pmod_version=<tag>`.
- **Zero Alpha Policy** remains in force (see `docs/PUBLICATION.md`).
- Jenkins pipeline retired as of 1.0.2 — GitHub Actions is the sole CI/CD path.
