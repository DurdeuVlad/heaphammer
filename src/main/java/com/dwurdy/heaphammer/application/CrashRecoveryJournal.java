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

    private final Path journalPath;
    private volatile JournalState currentState = null;

    public CrashRecoveryJournal() {
        this(Paths.get(DEFAULT_JOURNAL_PATH));
    }

    public CrashRecoveryJournal(Path journalPath) {
        this.journalPath = Objects.requireNonNull(journalPath, "journalPath must not be null");
    }

    public synchronized void recordStart(ExperimentPlan plan) {
        JournalState state = new JournalState(
                plan.id().value(),
                plan.spec().scenarioId().value(),
                plan.spec().dimension(),
                System.currentTimeMillis()
        );
        this.currentState = state;
        persist();
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
        try {
            Files.deleteIfExists(journalPath);
        } catch (IOException e) {
            LOGGER.warn("Failed to delete journal file {}: {}", journalPath, e.getMessage());
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
                            if (platform.getPlayerLifecyclePort().isPresent()
                                    && platform.getPlayerLifecyclePort().get().quit(state.dimension, uuid)) {
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

    private void persist() {
        if (currentState == null) return;
        try {
            String json = GsonCodec.toJson(currentState);
            FileStorage.writeStringAtomic(journalPath, json);
        } catch (IOException e) {
            LOGGER.warn("Failed to persist crash recovery journal: {}", e.getMessage());
        }
    }

    public JournalState getCurrentState() {
        return currentState;
    }

    public Path getJournalPath() {
        return journalPath;
    }
}
