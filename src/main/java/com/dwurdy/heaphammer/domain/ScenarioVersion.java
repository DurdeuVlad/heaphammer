package com.dwurdy.heaphammer.domain;

/**
 * Scenario schema version.
 */
public record ScenarioVersion(int major, int minor) {
    public static final ScenarioVersion V1_0 = new ScenarioVersion(1, 0);

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
