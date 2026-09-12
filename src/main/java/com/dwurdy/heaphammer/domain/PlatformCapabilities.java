package com.dwurdy.heaphammer.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable capability matrix reported by a platform adapter. */
public final class PlatformCapabilities {
    private final Map<PlatformCapability, CapabilityStatus> statuses;

    private PlatformCapabilities(Map<PlatformCapability, CapabilityStatus> statuses) {
        EnumMap<PlatformCapability, CapabilityStatus> copy = new EnumMap<>(PlatformCapability.class);
        for (PlatformCapability capability : PlatformCapability.values()) {
            copy.put(capability, statuses.getOrDefault(capability,
                    CapabilityStatus.disabled("not provided by platform adapter")));
        }
        this.statuses = Collections.unmodifiableMap(copy);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PlatformCapabilities none() {
        return builder().build();
    }

    public CapabilityStatus status(PlatformCapability capability) {
        return statuses.get(Objects.requireNonNull(capability, "capability must not be null"));
    }

    public boolean supports(PlatformCapability capability) {
        return status(capability).supported();
    }

    public Map<PlatformCapability, CapabilityStatus> statuses() {
        return statuses;
    }

    public static final class Builder {
        private final EnumMap<PlatformCapability, CapabilityStatus> statuses = new EnumMap<>(PlatformCapability.class);

        public Builder supported(PlatformCapability capability) {
            statuses.put(Objects.requireNonNull(capability, "capability must not be null"), CapabilityStatus.enabled());
            return this;
        }

        public Builder unsupported(PlatformCapability capability, String reason) {
            statuses.put(Objects.requireNonNull(capability, "capability must not be null"),
                    CapabilityStatus.disabled(reason));
            return this;
        }

        public PlatformCapabilities build() {
            return new PlatformCapabilities(statuses);
        }
    }
}
