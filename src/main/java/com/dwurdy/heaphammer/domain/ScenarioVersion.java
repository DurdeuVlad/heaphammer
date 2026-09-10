package com.dwurdy.heaphammer.domain;

import com.github.bsideup.jabel.Desugar;

/**
 * Scenario schema version.
 */
@Desugar
public record ScenarioVersion(int major, int minor) {
    public static final ScenarioVersion V1_0 = new ScenarioVersion(1, 0);

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
