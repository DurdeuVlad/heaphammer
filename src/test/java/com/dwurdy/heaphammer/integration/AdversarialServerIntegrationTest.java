package com.dwurdy.heaphammer.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Professional, automated integration test executing against a real Minecraft dedicated server.
 * Tests adversarial scenarios:
 * 1. Overzealous admin input flooding and resource safety clamping.
 * 2. Path traversal attack injection into experiment IDs and replay commands.
 * 3. Configuration live inspection and hot-reload.
 * 4. Simulated sudden server crash mid-run and automated crash recovery journal sweep on server startup.
 */
@Tag("integration")
public class AdversarialServerIntegrationTest {

    private static final Duration SERVER_BOOT_TIMEOUT = Duration.ofSeconds(180);
    private static final Duration COMMAND_RESPONSE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SCENARIO_COMPLETION_TIMEOUT = Duration.ofSeconds(60);

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void executeAdversarialServerTestSuite() throws Exception {
        MinecraftServerProcess server = MinecraftServerProcess.forProjectRoot();
        File projectRoot = new File(System.getProperty("user.dir"));
        Path activeJournalPath = projectRoot.toPath().resolve("run").resolve("heaphammer").resolve("active_run_journal.json");
        Path configPath = projectRoot.toPath().resolve("run").resolve("config").resolve("heaphammer.json");

        try {
            System.out.println("=================================================================");
            System.out.println("  Starting Dedicated Server for Adversarial Security Suite");
            System.out.println("=================================================================");

            server.start(SERVER_BOOT_TIMEOUT);
            Assertions.assertTrue(server.isAlive(), "Server process should be alive after boot");

            // -------------------------------------------------------------
            // Phase 1: Adversarial Parameter Flood (Overzealous Admin Attack)
            // -------------------------------------------------------------
            System.out.println("\n[Adversarial Test 1] Testing Overzealous Admin Parameter Clamping...");
            // Issue extreme values that exceed default safety ceilings (radius=1000, batch=50000, iterations=1000)
            // We use small hold/settle to verify clamping without waiting for hundreds of ticks
            server.sendCommand("hh run chunks --radius=1000 --batch=50000 --iterations=1000 --hold=2 --settle=2");

            String radiusClampLog = server.waitForLineContaining("Parameter clamping: radius 1000 -> 32", COMMAND_RESPONSE_TIMEOUT);
            Assertions.assertNotNull(radiusClampLog, "Server must log radius clamping");

            String batchClampLog = server.waitForLineContaining("Parameter clamping: batch 50000 -> 128", COMMAND_RESPONSE_TIMEOUT);
            Assertions.assertNotNull(batchClampLog, "Server must log batch clamping");

            String iterClampLog = server.waitForLineContaining("Parameter clamping: iterations 1000 -> 50", COMMAND_RESPONSE_TIMEOUT);
            Assertions.assertNotNull(iterClampLog, "Server must log iterations clamping");

            server.waitForLineContaining("Started Experiment:", COMMAND_RESPONSE_TIMEOUT);

            // Cancel the experiment cleanly so the server is ready for subsequent commands
            server.sendCommand("hh abort");
            server.waitForLineContaining("Experiment cancelled", COMMAND_RESPONSE_TIMEOUT);
            Thread.sleep(2000);

            // -------------------------------------------------------------
            // Phase 2: Malicious Path Traversal Attacks
            // -------------------------------------------------------------
            System.out.println("\n[Adversarial Test 2] Testing Path Traversal Defense...");

            server.sendCommand("hh report show ../../secret");
            String pathTraversalLog1 = server.waitForLineContaining("Invalid experiment ID '../../secret'", COMMAND_RESPONSE_TIMEOUT);
            Assertions.assertNotNull(pathTraversalLog1, "Server must reject directory traversal in report show");

            server.sendCommand("hh replay ../../secret");
            String pathTraversalLog2 = server.waitForLineContaining("Invalid experiment ID '../../secret'", COMMAND_RESPONSE_TIMEOUT);
            Assertions.assertNotNull(pathTraversalLog2, "Server must reject directory traversal in replay");

            Assertions.assertTrue(server.isAlive(), "Server must not crash or hang from path traversal attempts");

            // -------------------------------------------------------------
            // Phase 3: Config Inspection and Hot Reload
            // -------------------------------------------------------------
            System.out.println("\n[Adversarial Test 3] Testing Configuration Inspection and Hot Reload...");

            server.sendCommand("hh config show");
            server.waitForLineContaining("HeapHammer Configuration", COMMAND_RESPONSE_TIMEOUT);
            server.waitForLineContaining("Max Iterations: 50", COMMAND_RESPONSE_TIMEOUT);

            // Back up existing config if present
            String originalConfig = Files.exists(configPath) ? Files.readString(configPath) : null;
            try {
                // Write modified config on disk with maxIterations: 15
                String modifiedConfig = """
                        {
                          "maxIterations": 15,
                          "maxRadius": 16,
                          "maxBatchSize": 25,
                          "maxHoldTicks": 200,
                          "maxSettleTicks": 200,
                          "minFreeMemoryMb": 128,
                          "maxServerTickPauseMs": 3000,
                          "autoCleanupOnStartup": true
                        }
                        """;
                Files.createDirectories(configPath.getParent());
                Files.writeString(configPath, modifiedConfig);

                server.sendCommand("hh config reload");
                server.waitForLineContaining("HeapHammer configuration reloaded successfully", COMMAND_RESPONSE_TIMEOUT);

                server.sendCommand("hh config show");
                server.waitForLineContaining("Max Iterations: 15", COMMAND_RESPONSE_TIMEOUT);
                server.waitForLineContaining("Max Radius: 16", COMMAND_RESPONSE_TIMEOUT);
            } finally {
                if (originalConfig != null) {
                    Files.writeString(configPath, originalConfig);
                    server.sendCommand("hh config reload");
                    server.waitForLineContaining("HeapHammer configuration reloaded successfully", COMMAND_RESPONSE_TIMEOUT);
                }
            }

            // -------------------------------------------------------------
            // Phase 4: Sudden Crash mid-run & Automatic Startup Recovery Sweep
            // -------------------------------------------------------------
            System.out.println("\n[Adversarial Test 4] Testing Hard Server Crash and Startup Recovery Sweep...");

            // Start an entity workload with long hold ticks so it is actively running
            server.sendCommand("hh run entities --iterations=5 --batch=20 --hold=200");
            server.waitForLineContaining("Started Entity Experiment:", COMMAND_RESPONSE_TIMEOUT);

            // Wait for entities to spawn and register into the crash journal
            Thread.sleep(1500);
            Assertions.assertTrue(Files.exists(activeJournalPath),
                    "Active run journal must exist on disk during execution at: " + activeJournalPath);

            System.out.println("  Active run journal confirmed on disk. Simulating abrupt server crash (killHard)...");
            server.killHard();
            Assertions.assertFalse(server.isAlive(), "Server process must be terminated");

            // Confirm the crash left the active journal intact on disk
            Assertions.assertTrue(Files.exists(activeJournalPath),
                    "Active run journal must persist after abrupt crash");

            System.out.println("  Relaunching dedicated server to test automated startup sweep...");
            // Restart the server
            server.start(SERVER_BOOT_TIMEOUT);
            Assertions.assertTrue(server.isAlive(), "Server should reboot successfully after crash");

            // Verify startup recovery swept the orphaned state
            String recoveryLog = server.waitForLineContaining("Automated crash recovery completed", COMMAND_RESPONSE_TIMEOUT);
            Assertions.assertNotNull(recoveryLog, "Server must execute automated recovery on startup");
            System.out.println("  Observed: " + recoveryLog);

            // Assert active journal was deleted
            Assertions.assertFalse(Files.exists(activeJournalPath),
                    "Active run journal must be deleted after recovery sweep");

            System.out.println("\n[Adversarial Test Suite] All adversarial checks PASSED! Stopping server cleanly...");
            server.stopCleanly(COMMAND_RESPONSE_TIMEOUT);
            Assertions.assertFalse(server.isAlive(), "Server should terminate cleanly");

        } finally {
            if (server.isAlive()) {
                server.killHard();
            }
        }
    }
}
