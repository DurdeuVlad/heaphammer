package com.dwurdy.heaphammer.domain;

/** Entity lifecycle profile selected by the entities scenario. */
public enum EntityWorkloadProfile {
    TRANSIENT,
    PERSISTENT,
    UNTICKED_RING
}
