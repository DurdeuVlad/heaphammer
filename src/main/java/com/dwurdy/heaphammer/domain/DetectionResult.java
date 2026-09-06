package com.dwurdy.heaphammer.domain;

import java.util.Objects;

/**
 * Result produced by the detection analysis engine.
 */
public record DetectionResult(
        DetectionClassification classification,
        double confidence,
        double slopeBytesPerCycle,
        double rSquared,
        long netDeltaBytes,
        String rationale
) {
    public DetectionResult {
        Objects.requireNonNull(classification, "classification must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
    }

    public double slopeMbPerCycle() {
        return slopeBytesPerCycle / (1024.0 * 1024.0);
    }

    public double netDeltaMb() {
        return netDeltaBytes / (1024.0 * 1024.0);
    }
}
