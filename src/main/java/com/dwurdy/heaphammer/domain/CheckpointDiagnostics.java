package com.dwurdy.heaphammer.domain;

import com.dwurdy.heaphammer.diagnostics.EventMetricsSnapshot;
import com.dwurdy.heaphammer.diagnostics.RetentionSnapshot;
import com.dwurdy.heaphammer.diagnostics.WorldStoreSnapshot;

/** Optional lightweight evidence captured alongside one checkpoint. */
public record CheckpointDiagnostics(
        RetentionSnapshot retention,
        WorldStoreSnapshot worldStore,
        EventMetricsSnapshot events
) {
    public static final CheckpointDiagnostics EMPTY = new CheckpointDiagnostics(null, null, null);
}
