package com.dwurdy.heaphammer.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Immutable identifier for an experiment run or plan.
 */
public record ExperimentId(String value) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    public ExperimentId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static ExperimentId generate() {
        String timestamp = FORMATTER.format(Instant.now());
        int randomSuffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return new ExperimentId("hh-" + timestamp + "-" + randomSuffix);
    }

    public static ExperimentId of(String value) {
        return new ExperimentId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
