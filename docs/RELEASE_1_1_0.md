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

## Synthetic diagnostic fixtures

The modern Fabric builds for 1.21.1, 1.21.4, 1.20.6, 1.20.4, and 1.20.1 package two additional opt-in fixtures for validating the new evidence paths:

- `testmod-leak-playersession` targets only `hh_test_` players. Use `/playersessionleak mode clean` to remove records on disconnect or `/playersessionleak mode leak` to retain `PlayerSessionRecord` objects and the live player reference. `/playersessionleak reset` clears all fixture state.
- `testmod-leak-persistententity` targets only HeapHammer-tagged persistent mobs. Use `/persistententityleak mode clean` for unload cleanup or `/persistententityleak mode leak` to retain `PersistentEntityRecord` objects. The fixture never writes Anvil files directly; world-store evidence must come from Minecraft's normal persistence path.

Recommended verification commands:

```text
playersessionleak mode leak
hh run players --iterations=6 --logins-per-cycle=2 --actions=join,quit --explicit-gc=true --diagnostics=retention,histogram,event-metrics

persistententityleak mode leak
hh run entities --profile=persistent --iterations=6 --batch=15 --hold=5 --settle=10 --explicit-gc=true --diagnostics=retention,histogram,world-store,event-metrics
```

The live matrix requires both fixture jars on all five modern Fabric targets and exercises the leak and control modes there. These fixtures are intentionally not claimed for older Fabric branches, NeoForge, or Forge; each of those targets needs a separate loader/version adaptation before equivalent coverage can be advertised.
