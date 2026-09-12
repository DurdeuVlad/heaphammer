package com.dwurdy.heaphammer.domain;

import com.github.bsideup.jabel.Desugar;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Immutable identifier for an experiment run or plan.
 */
@Desugar
public record ExperimentId(String value) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private static final java.util.regex.Pattern SAFE_ID_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    public ExperimentId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (!SAFE_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid experiment ID '" + value + "'. Must be 1-64 alphanumeric characters, underscores, or hyphens.");
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
