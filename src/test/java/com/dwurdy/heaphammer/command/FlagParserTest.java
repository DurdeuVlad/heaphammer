package com.dwurdy.heaphammer.command;

import com.dwurdy.heaphammer.command.argument.FlagParser;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlagParserTest {

    @Test
    @DisplayName("FlagParser parses raw flags accurately")
    void testRawFlags() {
        String[] args = {"--seed=12345", "--radius=12", "--strategy=ring", "ignoreMe"};
        Map<String, String> flags = FlagParser.parseRawFlags(args, 0);

        assertEquals("12345", flags.get("seed"));
        assertEquals("12", flags.get("radius"));
        assertEquals("ring", flags.get("strategy"));
        assertFalse(flags.containsKey("ignoreMe"));
    }

    @Test
    @DisplayName("FlagParser builds ExperimentSpec with overrides and defaults")
    void testSpecParsing() {
        String[] args = {"--seed=999", "--center=10,-20", "--radius=7", "--iterations=15", "--batch=8", "--explicit-gc=true"};
        ExperimentSpec spec = FlagParser.parseSpec(args, 0, 0, 0);

        assertEquals(999L, spec.seed());
        assertEquals(10, spec.centerX());
        assertEquals(-20, spec.centerZ());
        assertEquals(7, spec.radius());
        assertEquals(15, spec.iterations());
        assertEquals(8, spec.batchSize());
        assertTrue(spec.explicitGc());
    }
}
