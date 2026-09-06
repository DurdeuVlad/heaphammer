package com.dwurdy.heaphammer.domain;

/**
 * Phase or boundary at which a checkpoint was recorded.
 */
public enum CheckpointPhase {
    BASELINE,
    WARMUP,
    ITERATION_CLEANUP,
    FINAL_CLEANUP,
    MANUAL
}
