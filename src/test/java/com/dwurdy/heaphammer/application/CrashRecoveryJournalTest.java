package com.dwurdy.heaphammer.application;

import com.dwurdy.heaphammer.domain.ExperimentId;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.platform.MockPlatformAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrashRecoveryJournalTest {

    @Test
    @DisplayName("CrashRecoveryJournal records start and removes journal on finish")
    void testRecordStartAndFinish(@TempDir Path tempDir) {
        Path journalFile = tempDir.resolve("active_run.json");
        CrashRecoveryJournal journal = new CrashRecoveryJournal(journalFile);

        assertFalse(journal.hasInterruptedRun());

        ExperimentSpec spec = ExperimentSpec.builder().build();
        ExperimentPlan plan = new ExperimentPlan(
                ExperimentId.of("hh-test-123"),
                System.currentTimeMillis(),
                spec,
                List.of(),
                100,
                5
        );

        journal.recordStart(plan);
        assertTrue(journal.hasInterruptedRun());
        assertTrue(Files.exists(journalFile));

        journal.recordFinish();
        assertFalse(journal.hasInterruptedRun());
        assertFalse(Files.exists(journalFile));
    }

    @Test
    @DisplayName("CrashRecoveryJournal recovers orphaned entities and blocks after simulated crash")
    void testCrashRecovery(@TempDir Path tempDir) {
        Path journalFile = tempDir.resolve("active_run.json");
        CrashRecoveryJournal journal = new CrashRecoveryJournal(journalFile);

        MockPlatformAdapter platform = new MockPlatformAdapter();

        ExperimentSpec spec = ExperimentSpec.builder().build();
        ExperimentPlan plan = new ExperimentPlan(
                ExperimentId.of("hh-crashed-run"),
                System.currentTimeMillis(),
                spec,
                List.of(),
                100,
                5
        );

        // 1. Start experiment & simulate spawning entities and placing blocks
        journal.recordStart(plan);

        UUID entity1 = platform.spawnEntity("minecraft:overworld", "minecraft:zombie", 0, 64, 0);
        UUID entity2 = platform.spawnEntity("minecraft:overworld", "minecraft:skeleton", 5, 64, 5);
        journal.recordEntitySpawned(entity1);
        journal.recordEntitySpawned(entity2);

        platform.placeBlockEntity("minecraft:overworld", "minecraft:chest", 10, 64, 10);
        journal.recordBlockPlaced(10, 64, 10);

        // Verify assets exist in platform
        assertEquals(22, platform.getActiveEntityCount("minecraft:overworld")); // 20 default + 2 test

        // 2. Simulate server crash (journal remains on disk, new session begins)
        CrashRecoveryJournal recoverySession = new CrashRecoveryJournal(journalFile);
        assertTrue(recoverySession.hasInterruptedRun());

        // 3. Perform recovery
        int cleaned = recoverySession.recoverIfInterrupted(platform);
        assertTrue(cleaned >= 3); // 2 entities + 1 block
        assertFalse(recoverySession.hasInterruptedRun());
        assertFalse(Files.exists(journalFile));
    }
}
