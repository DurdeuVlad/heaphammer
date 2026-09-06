package com.dwurdy.heaphammer.domain;

/**
 * Diagnostic verdict classifications adhering to BR-004.
 */
public enum DetectionClassification {
    PASS,
    SUSPICIOUS,
    INCONCLUSIVE,
    WORKLOAD_FAILED,
    CLEANUP_FAILED,
    ENVIRONMENT_MISMATCH
}
