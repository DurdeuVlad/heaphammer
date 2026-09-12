package com.dwurdy.heaphammer.domain;

import com.github.bsideup.jabel.Desugar;

import java.util.Objects;

/** Explicit support result for one platform capability. */
@Desugar
public record CapabilityStatus(boolean supported, String reason) {
    public CapabilityStatus {
        reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public static CapabilityStatus enabled() {
        return new CapabilityStatus(true, "supported");
    }

    public static CapabilityStatus disabled(String reason) {
        return new CapabilityStatus(false, reason);
    }
}
