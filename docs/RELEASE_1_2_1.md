# HeapHammer 1.2.1

HeapHammer 1.2.1 is a bugfix release on top of 1.2.0 that eliminates a cross-session race in crash-recovery journal persistence.

## Fixes

- **Stale queued persist could resurrect a deleted journal** (`CrashRecoveryJournal`). Async journal writes are serialized per session by a generation counter, but the guard was per-instance: a write queued by a previous session could still execute after a new session's `recoverIfInterrupted()`/`recordFinish()` had already deleted `active_run_journal.json`, silently recreating it. The next boot would then see a phantom interrupted run. Persist tasks now carry a global monotonic sequence taken at enqueue time, and the staleness check plus write run under a per-path lock shared by all journal instances; `recordStart()` and `recordFinish()` publish a floor sequence for the path, so any write enqueued before a lifecycle boundary is dropped. Includes a deterministic regression test that reproduces the interleaving.

## Compatibility

No behavior or API changes beyond the journal ordering fix. All workload flags, diagnostics, and fixtures from 1.2.0 are unchanged. Safe upgrade for every supported target: Fabric 1.14.4–1.21.1, Forge 1.7.10/1.12.2/1.16.5/1.18.2/1.20.1, NeoForge 1.21.1.
