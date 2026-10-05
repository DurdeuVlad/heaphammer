# HeapHammer 1.2.0

HeapHammer 1.2.0 extends the 1.1.0 player-lifecycle surface with concurrent cohort workloads and gaze orientation control, widens world-store evidence to every persisted dimension store, and ships a second wave of synthetic leak fixtures modelled on real AllTheLeaks/NeoForge/Fabric/GTNH/Paper incidents.

## New operator surfaces

- `/hh plan|run players --cohort=true` keeps every login of an iteration online simultaneously: all JOINs run first, middle actions execute while the full cohort is present, then all QUITs. Members stand on a deterministic ring that auto-scales for large cohorts.
- `--actions=...,lookat,...` rotates a test player so its server-side view vector points at the planned coordinate; in cohort mode each member is aimed at its ring-neighbor's eye position, which is what makes per-viewer observation fixtures fire at all.
- `--hold=N` now also acts as the cohort dwell: once only QUITs remain for an iteration, the aimed cohort stays online for N ticks so per-tick observers see real concurrent state.
- World-store snapshots (`--diagnostics=world-store`, `hh compare`) now cover SavedData `data/*.dat` payloads and chunk-region `region/*.mca` bytes alongside entity regions; `totalPersistedBytes` feeds the world-growth evidence diff.

## Synthetic diagnostic fixtures (modern Fabric only)

Five additional opt-in fixtures build on 1.21.1, 1.21.4, 1.20.6, 1.20.4, and 1.20.1, each defaulting to `OFF` with `clean`, `leak`, and `reset` modes:

- `testmod-leak-clonecache` hooks the player clone boundary (respawn, end-return). `leak` retains each pre-clone `ServerPlayer` with a 256 KB record; `clean` re-keys by the surviving UUID.
- `testmod-leak-fakeplayerfactory` constructs a fresh `ServerPlayer` operator per HeapHammer-tagged entity load — the machinery/archetype whose advancement listeners pin the operator forever in `leak` mode.
- `testmod-leak-gazetrack` raycasts each test player's view every tick and serializes any looked-at player's NBT. `leak` keeps every observation; `clean` keeps only the latest per observer:target pair.
- `testmod-leak-cachelist` + `testmod-antag-optimizer` form the intrusive-optimizer pair: the victim keeps a lazily rebuilt chunk-metadata cache and a bounded rebuild audit; the antagonist sweeps the victim's cache every N ticks (`antagoptimizer on|once|interval <ticks>`), turning each rebuild into unbounded audit growth. Each mod alone is a stable plateau.

Recommended verification commands:

```text
clonecacheleak mode leak
fakeplayerfactory mode leak
gazetrackleak mode leak
cachelistleak mode leak
antagoptimizer interval 20
antagoptimizer on
hh run players --iterations=2 --logins-per-cycle=3 --cohort=true --actions=join,lookat,respawn,quit --explicit-gc=true --diagnostics=retention,histogram,event-metrics
hh run players --iterations=3 --logins-per-cycle=4 --cohort=true --actions=join,lookat,lookat,quit --explicit-gc=true --diagnostics=retention,histogram,event-metrics
hh run entities --profile=persistent --iterations=5 --batch=15 --hold=5 --settle=10 --explicit-gc=true --diagnostics=retention,histogram,event-metrics,world-store
```

## Evidence and safety

Synthetic `EmbeddedChannel` connections now drain their outbound queues on every executor tick (`PlayerLifecyclePort.housekeepingTick`), after joins, after every `perform` action, and on disconnect — held-online cohorts no longer queue server packets for the session lifetime. `placeNewPlayer` spawn relocation is corrected so cohort ring geometry survives the authoritative join boundary, and disconnect no longer severs `listener.player`, matching vanilla's tracker-visible lifecycle. All changes remain reflection-only in the shared port, so the domain stays pure Java across every version branch.

The live dedicated-server matrix requires all seven fixture jars on the five modern Fabric targets and verifies each fixture's OFF/LEAK counters plus report evidence there. These fixtures are intentionally not claimed for older Fabric branches, NeoForge, or Forge; each of those targets needs a separate loader/version adaptation before equivalent coverage can be advertised.
