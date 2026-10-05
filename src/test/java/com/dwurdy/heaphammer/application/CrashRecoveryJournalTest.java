package com.dwurdy.heaphammer.application;

import com.dwurdy.heaphammer.domain.ExperimentId;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.platform.MockPlatformAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
    @DisplayName("CrashRecoveryJournal serializes finish with queued persistence")
    void testFinishSerializesWithQueuedPersistence(@TempDir Path tempDir) throws Exception {
        Path journalFile = tempDir.resolve("active_run.json");
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch allowWrite = new CountDownLatch(1);
        CrashRecoveryJournal journal = new CrashRecoveryJournal(journalFile) {
            @Override
            protected void writeJournal(String json) throws IOException {
                writeStarted.countDown();
                try {
                    if (!allowWrite.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting to release test journal write");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting to release test journal write", e);
                }
                super.writeJournal(json);
            }
        };
        ExperimentPlan plan = new ExperimentPlan(
                ExperimentId.of("hh-race-test"),
                System.currentTimeMillis(),
                ExperimentSpec.builder().build(),
                List.of(),
                100,
                5
        );

        journal.recordStart(plan);
        journal.recordEntitySpawned(UUID.randomUUID());
        assertTrue(writeStarted.await(5, TimeUnit.SECONDS));

        Thread finishThread = new Thread(journal::recordFinish, "hh-journal-finish-test");
        finishThread.start();
        Thread.sleep(100);
        assertTrue(finishThread.isAlive(), "finish must wait for an in-flight journal write");

        allowWrite.countDown();
        finishThread.join(5_000);
        assertFalse(finishThread.isAlive());
        assertFalse(journal.hasInterruptedRun());
        assertFalse(Files.exists(journalFile));
    }

    @Test
    @DisplayName("Queued persist from a prior session must not resurrect a deleted journal")
    void testQueuedWriteFromPriorSessionCannotResurrectJournal(@TempDir Path tempDir) throws Exception {
        Path journalFile = tempDir.resolve("active_run.json");
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch allowWrite = new CountDownLatch(1);
        CrashRecoveryJournal crashedSession = new CrashRecoveryJournal(journalFile) {
            @Override
            protected void writeJournal(String json) throws IOException {
                writeStarted.countDown();
                try {
                    if (!allowWrite.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting to release test journal write");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting to release test journal write", e);
                }
                super.writeJournal(json);
            }
        };
        ExperimentPlan plan = new ExperimentPlan(
                ExperimentId.of("hh-race-cross-session"),
                System.currentTimeMillis(),
                ExperimentSpec.builder().build(),
                List.of(),
                100,
                5
        );

        crashedSession.recordStart(plan);
        assertTrue(Files.exists(journalFile));

        // An async persist is in flight (blocked inside writeJournal) and a
        // second one is queued behind it on the session's executor.
        crashedSession.recordEntitySpawned(UUID.randomUUID());
        assertTrue(writeStarted.await(5, TimeUnit.SECONDS));
        crashedSession.recordEntitySpawned(UUID.randomUUID());

        // A new session recovers and deletes the journal while the old
        // session's writes are still pending.
        CrashRecoveryJournal recoverySession = new CrashRecoveryJournal(journalFile);
        MockPlatformAdapter platform = new MockPlatformAdapter();
        Thread recoverThread = new Thread(() -> recoverySession.recoverIfInterrupted(platform),
                "hh-journal-recover-test");
        recoverThread.start();
        Thread.sleep(150);

        allowWrite.countDown();
        recoverThread.join(10_000);
        assertFalse(recoverThread.isAlive(), "recovery must finish after the in-flight write drains");

        // Give the old session's queued write a chance to run: it must not
        // recreate the file the recovery session already deleted.
        long deadline = System.currentTimeMillis() + 1_500;
        while (System.currentTimeMillis() < deadline) {
            assertFalse(Files.exists(journalFile),
                    "stale queued write resurrected the journal after recovery deleted it");
            Thread.sleep(25);
        }
        assertFalse(recoverySession.hasInterruptedRun());
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
