package com.dwurdy.heaphammer.application;

import com.github.bsideup.jabel.Desugar;

import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.infrastructure.FileStorage;
import com.dwurdy.heaphammer.infrastructure.json.GsonCodec;
import com.dwurdy.heaphammer.platform.PlatformAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persistent journal tracking active experiment assets (placed block entities, entities, tickets)
 * to ensure deterministic cleanup and recovery if the server crashes or halts during execution.
 */
public class CrashRecoveryJournal {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-recovery");
    public static final String DEFAULT_JOURNAL_PATH = "heaphammer/active_run_journal.json";

    @Desugar
public record BlockPosRecord(int x, int y, int z) {}

    public static class JournalState {
        public String runId;
        public String scenarioId;
        public String dimension;
        public long startedTimestamp;
        public Set<String> activeEntityUuids = Collections.newSetFromMap(new ConcurrentHashMap<>());
        public Set<String> activePlayerUuids = Collections.newSetFromMap(new ConcurrentHashMap<>());
        public Set<BlockPosRecord> activeBlockPositions = Collections.newSetFromMap(new ConcurrentHashMap<>());

        public JournalState() {}

        public JournalState(String runId, String scenarioId, String dimension, long startedTimestamp) {
            this.runId = runId;
            this.scenarioId = scenarioId;
            this.dimension = dimension;
            this.startedTimestamp = startedTimestamp;
        }
    }

    // Cross-instance coordination for one journal file: every async write is
    // enqueued with a global monotonic sequence, and lifecycle boundaries
    // (recordStart / recordFinish on ANY session targeting the same path)
    // publish a floor under the shared path lock. A queued write whose sequence
    // predates the floor is stale and must be skipped, otherwise a persist from
    // a previous session could recreate the journal after it was deleted.
    private static final ConcurrentHashMap<Path, Object> JOURNAL_PATH_LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Path, Long> JOURNAL_FLOOR_SEQUENCE = new ConcurrentHashMap<>();
    private static final AtomicLong PERSIST_SEQUENCE = new AtomicLong();

    private final Path journalPath;
    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "heaphammer-recovery-journal");
        thread.setDaemon(true);
        return thread;
    });
    private final Path lockKey;
    private final Object pathLock;
    private final AtomicLong persistenceGeneration = new AtomicLong();
    private volatile JournalState currentState = null;

    public CrashRecoveryJournal() {
        this(Paths.get(DEFAULT_JOURNAL_PATH));
    }

    public CrashRecoveryJournal(Path journalPath) {
        this.journalPath = Objects.requireNonNull(journalPath, "journalPath must not be null");
        this.lockKey = journalPath.toAbsolutePath().normalize();
        this.pathLock = JOURNAL_PATH_LOCKS.computeIfAbsent(lockKey, key -> new Object());
    }

    public synchronized void recordStart(ExperimentPlan plan) {
        JournalState state = new JournalState(
                plan.id().value(),
                plan.spec().scenarioId().value(),
                plan.spec().dimension(),
                System.currentTimeMillis()
        );
        this.currentState = state;
        if (plan.playerOperations() != null) {
            plan.playerOperations().forEach(operation -> {
                if (operation.action() == com.dwurdy.heaphammer.domain.PlayerAction.JOIN) {
                    state.activePlayerUuids.add(operation.playerId().toString());
                }
            });
        }
        persistInitial();
    }

    public synchronized void recordEntitySpawned(UUID uuid) {
        if (currentState != null && uuid != null) {
            currentState.activeEntityUuids.add(uuid.toString());
            persist();
        }
    }

    public synchronized void recordEntityRemoved(UUID uuid) {
        if (currentState != null && uuid != null) {
            currentState.activeEntityUuids.remove(uuid.toString());
            persist();
        }
    }

    public synchronized void recordPlayerJoined(UUID uuid) {
        if (currentState != null && uuid != null) {
            currentState.activePlayerUuids.add(uuid.toString());
            persist();
        }
    }

    public synchronized void recordPlayerRemoved(UUID uuid) {
        if (currentState != null && uuid != null) {
            currentState.activePlayerUuids.remove(uuid.toString());
            persist();
        }
    }

    public synchronized void recordBlockPlaced(int x, int y, int z) {
        if (currentState != null) {
            currentState.activeBlockPositions.add(new BlockPosRecord(x, y, z));
            persist();
        }
    }

    public synchronized void recordBlockRemoved(int x, int y, int z) {
        if (currentState != null) {
            currentState.activeBlockPositions.remove(new BlockPosRecord(x, y, z));
            persist();
        }
    }

    public synchronized void recordFinish() {
        this.currentState = null;
        persistenceGeneration.incrementAndGet();
        synchronized (pathLock) {
            JOURNAL_FLOOR_SEQUENCE.put(lockKey, PERSIST_SEQUENCE.incrementAndGet());
            try {
                Files.deleteIfExists(journalPath);
            } catch (IOException e) {
                LOGGER.warn("Failed to delete journal file {}: {}", journalPath, e.getMessage());
            }
        }
    }

    public synchronized boolean hasInterruptedRun() {
        return Files.exists(journalPath);
    }

    public synchronized int recoverIfInterrupted(PlatformAdapter platform) {
        int cleanedCount = 0;
        if (!hasInterruptedRun()) {
            if (platform != null) {
                cleanedCount += platform.cleanupOrphanedState();
            }
            return cleanedCount;
        }

        LOGGER.warn("Found active run journal from previous session at {}. Possible server crash detected! Initiating automated recovery...", journalPath);
        try {
            String json = FileStorage.readString(journalPath);
            JournalState state = GsonCodec.fromJson(json, JournalState.class);
            if (state != null && platform != null) {
                if (state.activeBlockPositions != null) {
                    for (BlockPosRecord pos : state.activeBlockPositions) {
                        if (platform.removeBlockEntity(state.dimension, pos.x(), pos.y(), pos.z())) {
                            cleanedCount++;
                        }
                    }
                }
                if (state.activeEntityUuids != null) {
                    for (String uuidStr : state.activeEntityUuids) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            if (platform.removeEntity(state.dimension, uuid, "DISCARD")) {
                                cleanedCount++;
                            }
                        } catch (Exception ignored) {}
                    }
                }
                if (state.activePlayerUuids != null) {
                    for (String uuidStr : state.activePlayerUuids) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            if (platform.getPlayerLifecyclePort().map(port -> port.quit(state.dimension, uuid)).orElse(false)) {
                                cleanedCount++;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse journal file for recovery: {}", e.getMessage(), e);
        }

        if (platform != null) {
            cleanedCount += platform.cleanupOrphanedState();
        }

        recordFinish();
        LOGGER.info("Automated crash recovery completed: purged {} leftover items and restored clean state.", cleanedCount);
        return cleanedCount;
    }

    private synchronized void persist() {
        if (currentState == null) return;
        long generation = persistenceGeneration.incrementAndGet();
        long sequence = PERSIST_SEQUENCE.incrementAndGet();
        JournalState snapshot = copyState(currentState);
        persistenceExecutor.execute(() -> {
            try {
                String json = GsonCodec.toJson(snapshot);
                synchronized (pathLock) {
                    // Serialize the staleness checks with lifecycle writes on the
                    // same journal path. The generation bump drops writes this
                    // instance superseded or finished; the floor sequence drops
                    // writes enqueued before another session's lifecycle event
                    // on the same file.
                    if (generation == persistenceGeneration.get()
                            && sequence > JOURNAL_FLOOR_SEQUENCE.getOrDefault(lockKey, 0L)) {
                        writeJournal(json);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to persist crash recovery journal: {}", e.getMessage());
            }
        });
    }

    protected void writeJournal(String json) throws IOException {
        FileStorage.writeStringAtomic(journalPath, json);
    }

    private synchronized void persistInitial() {
        if (currentState == null) return;
        persistenceGeneration.incrementAndGet();
        synchronized (pathLock) {
            // The new run owns the journal from here on: writes still queued by
            // a previous session on this path must not overwrite fresh state.
            JOURNAL_FLOOR_SEQUENCE.put(lockKey, PERSIST_SEQUENCE.incrementAndGet());
            try {
                FileStorage.writeStringAtomic(journalPath, GsonCodec.toJson(copyState(currentState)));
            } catch (IOException e) {
                LOGGER.warn("Failed to persist crash recovery journal: {}", e.getMessage());
            }
        }
    }

    private static JournalState copyState(JournalState source) {
        JournalState copy = new JournalState(source.runId, source.scenarioId, source.dimension, source.startedTimestamp);
        if (source.activeEntityUuids != null) copy.activeEntityUuids.addAll(source.activeEntityUuids);
        if (source.activePlayerUuids != null) copy.activePlayerUuids.addAll(source.activePlayerUuids);
        if (source.activeBlockPositions != null) copy.activeBlockPositions.addAll(source.activeBlockPositions);
        return copy;
    }

    public JournalState getCurrentState() {
        return currentState;
    }

    public Path getJournalPath() {
        return journalPath;
    }
}
