# HeapHammer 1.1.0

HeapHammer 1.1.0 adds evidence-backed workload coverage for the RusticCraft-style retained-memory investigations.

## New operator surfaces

- `/hh plan|run players` executes deterministic test-player login/logout, respawn, teleport, and dimension-change actions through a live server login path where supported.
- `/hh plan|run entities --profile=persistent|unticked_ring` makes entity persistence intent explicit. `unticked_ring` is rejected on platforms without a real unload/reload implementation.
- `/hh compare <run-a> <run-b>` compares heap trends, R², histogram growth, weak-reference retention, world-store deltas, and event counters.
- `--duration` and `--interval` enable bounded soak scheduling.
- `--diagnostics=histogram,retention,world-store,event-metrics` opts into expensive evidence collectors; collectors run off the server tick thread.
- `--track-classes=a.b.Target,c.d.Other` scopes the weak-reference census.

## Evidence and safety

Reports embed optional baseline/final histogram diffs, retention snapshots, persisted entity-store metrics, and loader event counters. Capability matrices report unsupported behavior explicitly. Test-player UUIDs, entity UUIDs, and block positions are journaled for crash recovery; live Minecraft objects are never retained by the core domain or recovery journal.

Fabric 1.21.1 and NeoForge 1.21.1 expose the live player, persistent-entity, world-store, retention, and event-counter ports. Unticked-chunk behavior remains explicitly unsupported until a loader-safe implementation is available.
