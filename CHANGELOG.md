# Changelog

All notable changes to HeapHammer will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.1] - 2026-09-30

### Distribution & Platform Presentation
- **Scoping to Canonical LTS Targets**: Active production distribution across CurseForge, Modrinth, and GitHub is now strictly scoped to the 6 major Minecraft LTS lines (`1.21.1`, `1.20.1`, `1.18.2`, `1.16.5`, `1.12.2`, `1.7.10`), eliminating multi-version clutter and notification fatigue.
- **Deterministic 2-Phase Deployment**: Overhauled `.github/workflows/release.yml` with a two-phase publishing architecture. Phase 1 uploads secondary LTS targets and loaders in parallel; Phase 2 uploads modern `1.21.1 Fabric` sequentially with a settling buffer, ensuring the CurseForge main overview "Download" button serves the modern standard.
- **Bracketed High-Visibility Display Names**: Standardized platform display names as `[<Loader> <MC>] HeapHammer <ModVer>` (e.g. `[Fabric 1.21.1] HeapHammer 1.1.1`), preventing critical loader/version information from truncating in launcher cards and mobile views.
- **One Active Release Per Version Policy**: Established standard in `docs/PUBLICATION.md` that each supported Minecraft version maintains strictly one active mod release on CurseForge and Modrinth, archiving past versions.

### Fixes & Build Hygiene
- **Platform Adapter Version Fallbacks**: Synchronized fallback version strings and fingerprint captures to `1.1.1` across `FabricPlatformAdapter`, `ForgePlatformAdapter`, and `NeoForgePlatformAdapter`.
- **Stale Binary Purging**: Updated `tools/publish-release.ps1` to automatically purge stale binaries from `dist/production/` before staging.

---

## [1.1.0] - 2026-09-28

### Added
- **Player-Session Diagnostic Fixture**: Added synthetic leak testing fixture for test-player login/logout, respawn, teleport, and dimension-change actions (`testmod-leak-playersession`).
- **Persistent-Entity Diagnostic Fixture**: Added synthetic entity persistence and retention testmod (`testmod-leak-persistententity`).
- **Advanced Diagnostic Diagnostics**: Added off-thread diagnostic census options (`--diagnostics=histogram,retention,world-store,event-metrics`).
- **Run Comparison Surface**: Added `/hh compare <run-a> <run-b>` to evaluate retention deltas, R², histogram growth, and event counters across runs.

---

## [1.0.2] - 2026-09-27

### Fixed
- **Forge Chunk Ticket Lifecycle**: Fixed unforce ticket handling on emergency abort during settling phase.
- **Protocol Key Resolution**: Fixed historical protocol attribute keys on legacy Forge loaders.

---

## [1.0.1] - 2026-09-26

### Fixed
- **Nested Loader Packaging**: Standardized independent Gradle wrappers for nested loader projects.
- **OLS Regression Stability**: Handled edge-case flatlines in ordinary least squares slope calculation.

---

## [1.0.0] - 2026-09-25

### Added
- Initial public release of HeapHammer: deterministic server workload and retained-memory regression framework for modded Minecraft across 14 supported Minecraft versions.
- Hexagonal architecture (Ports & Adapters) isolating pure-Java domain from Minecraft internals.
- Dedicated chunk ticket manager and tick-budgeted workload scheduler.
- Ordinary Least Squares (OLS) regression slope and plateau detection engine.
