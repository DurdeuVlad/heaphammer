## Description
<!-- Provide a clear, concise summary of the changes introduced by this pull request. -->

## Strategic Intent & Milestone Placement
<!-- Why does this task exist in the overall system outcome? Link to related issue(s). -->
Closes #

## Architecture & Boundary Invariants
Please verify that your change adheres to HeapHammer's core architectural constraints:
- [ ] **Zero Minecraft Imports in Domain**: Code in `com.dwurdy.heaphammer.domain`, `scenario`, `detection`, `reporting`, `storage`, and `infrastructure` does NOT import `net.minecraft.*` or `net.fabricmc.*`.
- [ ] **Zero Leaked References**: No persistent references to `LevelChunk`, `ServerLevel`, `Entity`, or `BlockEntity` exist in state machines, plans, or reports.
- [ ] **Deterministic Behavior**: All scenario generation is seeded and reproducible.
- [ ] **Production Tick Budgeting**: Workload operations remain throttled to tick budgets and respect `maxMillisPerTick`.

## Verification & Test Evidence
<!-- Provide the exact verification commands executed and attach output logs or report diffs. -->

```bash
# Automated tests executed
./gradlew test

# Full assembly verification
./gradlew build buildTestmods
```

### Empirical Test Evidence (if applicable)
<!-- Paste relevant excerpts from /hh report show, /hh report diff, or dedicated server matrix runs. -->

## Checklist
- [ ] My code follows the repository's coding style and conventions.
- [ ] I have added/updated unit tests covering my changes.
- [ ] I have updated documentation in `README.md` or `docs/` where relevant.
- [ ] All commit messages follow [Conventional Commits](https://www.conventionalcommits.org/).
- [ ] I have tested these changes on a live dedicated server or test suite.
