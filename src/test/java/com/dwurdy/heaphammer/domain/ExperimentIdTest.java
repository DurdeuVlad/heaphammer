package com.dwurdy.heaphammer.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentIdTest {

    @Test
    @DisplayName("Valid experiment IDs are accepted")
    void testValidIds() {
        ExperimentId generated = ExperimentId.generate();
        assertNotNull(generated.value());
        assertTrue(generated.value().startsWith("hh-"));

        ExperimentId custom = ExperimentId.of("custom-experiment_123");
        assertEquals("custom-experiment_123", custom.value());
    }

    @Test
    @DisplayName("Path traversal and invalid characters are rejected")
    void testPathTraversalRejected() {
        assertThrows(IllegalArgumentException.class, () -> ExperimentId.of("../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> ExperimentId.of("..\\windows\\system32"));
        assertThrows(IllegalArgumentException.class, () -> ExperimentId.of("run/../secrets"));
        assertThrows(IllegalArgumentException.class, () -> ExperimentId.of("run; rm -rf /"));
        assertThrows(IllegalArgumentException.class, () -> ExperimentId.of("id with spaces"));
        assertThrows(IllegalArgumentException.class, () -> ExperimentId.of(""));
        assertThrows(IllegalArgumentException.class, () -> ExperimentId.of("   "));
        assertThrows(IllegalArgumentException.class, () -> ExperimentId.of("invalid!char"));
    }
}
