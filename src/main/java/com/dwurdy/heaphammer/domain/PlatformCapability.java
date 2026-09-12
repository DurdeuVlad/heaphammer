package com.dwurdy.heaphammer.domain;

/** Runtime capability exposed by a loader adapter. */
public enum PlatformCapability {
    PLAYER_LIFECYCLE,
    PERSISTENT_ENTITIES,
    UNTICKED_CHUNKS,
    HISTOGRAM,
    RETENTION,
    WORLD_STORE,
    EVENT_METRICS,
    SOAK
}
