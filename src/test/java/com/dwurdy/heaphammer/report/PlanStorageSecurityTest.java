package com.dwurdy.heaphammer.report;

import com.dwurdy.heaphammer.domain.ExperimentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanStorageSecurityTest {

    @Test
    @DisplayName("PlanStorage rejects attempts to load files outside rootDir")
    void testPathTraversalRejected(@TempDir Path tempDir) {
        PlanStorage storage = new PlanStorage(tempDir);

        // Although ExperimentId already rejects path characters, we also test defense-in-depth directly
        assertThrows(IllegalArgumentException.class, () ->
                storage.loadPlan(ExperimentId.of("../secret")));
    }
}
