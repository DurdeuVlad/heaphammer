package com.dwurdy.heaphammer.domain;

import com.github.bsideup.jabel.Desugar;

/** Bounded wall-clock schedule for repeating lightweight workload bursts. */
@Desugar
public record SoakSchedule(long durationSeconds, long intervalSeconds) {
    public SoakSchedule {
        if (durationSeconds <= 0L) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        if (intervalSeconds <= 0L || intervalSeconds > durationSeconds) {
            throw new IllegalArgumentException("intervalSeconds must be positive and no greater than durationSeconds");
        }
    }

    public long maxCheckpoints() {
        return Math.max(1L, (durationSeconds + intervalSeconds - 1L) / intervalSeconds);
    }
}
