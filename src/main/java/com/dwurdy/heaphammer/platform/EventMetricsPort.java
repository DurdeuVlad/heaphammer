package com.dwurdy.heaphammer.platform;

import com.dwurdy.heaphammer.diagnostics.EventMetricsSnapshot;

/** Optional loader event-bus counter source. */
public interface EventMetricsPort {
    EventMetricsSnapshot snapshot();
}
