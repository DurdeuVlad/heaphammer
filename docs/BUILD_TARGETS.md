# HeapHammer Build Targets: Version × Loader Matrix

This document is the canonical reference for every Minecraft version and mod loader HeapHammer ships, how each artifact is built, and how to add a new target.

---

## 1. Mental Model

One branch per Minecraft version. One Gradle build per (branch, loader).

```text
<version branch>/
├── build.gradle + settings.gradle + gradle.properties   # ROOT build → primary loader jar
├── src/                                                 # shared domain + primary-loader code
└── loaders/
    ├── neoforge/                                        # independent Gradle build → NeoForge jar
    │   ├── settings.gradle / build.gradle / gradle.properties
    │   └── src/ (entrypoint + real-API adapters + neoforge.mods.toml)
    └── forge/                                           # independent Gradle build → Forge jar
        ├── settings.gradle / build.gradle / gradle.properties
        └── src/ (entrypoint + real-API adapters + mods.toml)
```

- The **root build** is unchanged per branch (Fabric Loom on modern branches, RetroFuturaGradle on the two classic Forge branches).
- Each `loaders/<loader>/` directory is a **self-contained Gradle project**: it reuses the root `gradlew` wrapper, reads `mod_version`/`maven_group`/`archives_base_name` from the root `gradle.properties`, and compiles `../src/main/java` minus the foreign-loader packages plus its own `src/` tree.
- **Invariant**: `src/main/java` must never contain `net.neoforged.*` or `net.minecraftforge.*` imports. Loader-specific code lives exclusively under `loaders/<loader>/src`. This keeps the root Loom build compiling cleanly and lets `loaders/` directories ride through `master` → `ver/*` sync merges as ordinary additive files.
- Loader/version pins live in `loaders/<loader>/gradle.properties` per branch; the sync workflow preserves branch-local values.

Local build of a nested loader:

```powershell
cd loaders/neoforge
..\..\gradlew.bat build test
```

---

## 2. Toolchain Map

| Toolchain | Used for | Plugin ID |
|---|---|---|
| **Fabric Loom** | Root build on all modern branches (1.14.4 → 1.21.4) | `net.fabricmc.fabric-loom-remap` |
| **ModDevGradle (MDG)** | `loaders/neoforge` on all NeoForge-capable branches (MC ≥ 1.20.2) | `net.neoforged.moddev` |
| **MDG legacyforge** | `loaders/forge` on MC 1.17.1 → 1.20.1 (also covers NeoForge 1.20.1) | `net.neoforged.moddev.legacyforge` |
| **ForgeGradle 5/6** | `loaders/forge` on MC 1.16.5 (legacyforge floor is 1.17) | `net.minecraftforge.gradle` |
| **RetroFuturaGradle + Jabel** | Root build on `ver/1.12.2-forge`, `ver/1.7.10-forge` | RFG 1.4.9 |

---

## 3. Canonical Target Matrix

`root` = produced by the branch's root build · `loaders/` = produced by a nested build · ✅ shipped · 🛠 in progress · ○ stretch

| MC | Branch | Fabric | NeoForge | Forge | Build JDK |
|---|---|---|---|---|---|
| 1.21.4 | `ver/1.21.4` | ✅ root (Loom) | 🛠 `loaders/neoforge` (NF 21.4.x) | ○ | 21 |
| 1.21.1 | `master` | ✅ root (Loom) | ✅ `loaders/neoforge` (NF 21.1.x) | ○ LexForge 51.x | 21 |
| 1.20.6 | `ver/1.20.6` | ✅ root (Loom) | 🛠 `loaders/neoforge` (NF 20.6.x) | — | 21 |
| 1.20.4 | `ver/1.20.4` | ✅ root (Loom) | 🛠 `loaders/neoforge` (NF 20.4.x) | — | 21 |
| 1.20.1 | `ver/1.20.1` | ✅ root (Loom) | ○ NF 47.1.x via legacyforge | 🛠 `loaders/forge` (47.x) | 21 |
| 1.19.4 | `ver/1.19.4` | ✅ root (Loom) | — | 🛠 `loaders/forge` (45.x) | 21 |
| 1.19.2 | `ver/1.19.2` | ✅ root (Loom) | — | 🛠 `loaders/forge` (43.x) | 21 |
| 1.18.2 | `ver/1.18.2` | ✅ root (Loom) | — | 🛠 `loaders/forge` (40.x) | 21 |
| 1.17.1 | `ver/1.17.1` | ✅ root (Loom) | — | 🛠 `loaders/forge` (37.x) | 21 |
| 1.16.5 | `ver/1.16.5` | ✅ root (Loom) | — | 🛠 `loaders/forge` (ForgeGradle, 36.2.x) | 21 |
| 1.15.2 | `ver/1.15.2` | ✅ root (Loom) | — | ○ (ForgeGradle) | 21 |
| 1.14.4 | `ver/1.14.4` | ✅ root (Loom) | — | ○ (ForgeGradle) | 21 |
| 1.12.2 | `ver/1.12.2-forge` | — | — | ✅ root (RFG) | 17 |
| 1.7.10 | `ver/1.7.10-forge` | — | — | ✅ root (RFG) | 17 |

> Note on `master`'s `platform/neoforge` package: it is a pure-Java, reflection-decoupled **simulation** used by the unit suite. The production NeoForge wiring (`HeapHammerNeoForge` entrypoint, `DirectNeoForgePlatformAdapter`, `DirectNeoForgeTicketBridge`) lives in `loaders/neoforge/src` and is the only code path a NeoForge server ever executes.

---

## 4. How a Release Flows (no manual steps)

1. Write code on `master` → open PR → merge. `ci.yml` builds root + all `loaders/*/` builds with tests.
2. Push to `master` triggers `sync-version-branches.yml`, which merges `master` into every `ver/*` branch, runs the root suite **plus every nested loader suite**, and either pushes or opens a `needs-version-adaptation` PR.
3. Tag `v*.*.*` → `release.yml` checks out all 14 branch targets in parallel, runs `gradlew build` (tests included) for the root build and every nested loader build, stages `heaphammer-<mc>-<loader>-<ver>.jar` files, generates the release-notes table **from the staged files**, then publishes via `mc-publish` to GitHub Releases, Modrinth, and CurseForge.

If any version's tests fail, that matrix job fails and the publish job never runs.

---

## 5. Adding a New Loader to a Branch

1. Create `loaders/<name>/` with `settings.gradle` (plugin repository), `gradle.properties` (loader/toolchain pins), `build.gradle` (plugin + sourceSets pattern below), `src/main/java/.../HeapHammer<Name>.java` entrypoint, `src/main/resources` mod metadata.
2. Reuse the shared-tree sourceSet pattern:

```groovy
sourceSets {
    main {
        java {
            srcDir 'src/main/java'
            srcDir '../../src/main/java'                        // repo-root shared tree
            exclude 'com/dwurdy/heaphammer/HeapHammer.java'     // Fabric entrypoint
            exclude 'com/dwurdy/heaphammer/mixin/**'
            exclude 'com/dwurdy/heaphammer/platform/fabric/**'
        }
        resources {
            srcDir 'src/main/resources'
            srcDir '../../src/main/resources'
            exclude 'fabric.mod.json'
            exclude 'heaphammer.mixins.json'
        }
    }
}
```

3. Keep real loader classes in `loaders/<name>/src/main/java/com/dwurdy/heaphammer/platform/<name>/` — same package names as the shared pure-Java counterparts is fine (same-package access still works across source roots).
4. Wire CI automatically: `ci.yml`, `sync-version-branches.yml`, `release.yml`, and `tools/*.ps1` all discover `loaders/*/settings.gradle` dynamically — **no workflow edits needed**.
5. On branches where the loader cannot exist (e.g. NeoForge on 1.19.2), delete the `loaders/<name>/` directory in the adaptation PR the sync opens — it stays deleted permanently.
