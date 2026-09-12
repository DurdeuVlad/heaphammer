package com.dwurdy.heaphammer.domain;

import com.dwurdy.heaphammer.diagnostics.ClassHistogram;
import com.dwurdy.heaphammer.diagnostics.EventMetricsSnapshot;
import com.dwurdy.heaphammer.diagnostics.HistogramDiff;
import com.dwurdy.heaphammer.diagnostics.RetentionSnapshot;
import com.dwurdy.heaphammer.diagnostics.WorldStoreSnapshot;

import java.util.Collections;
import java.util.List;

/** Structured optional evidence embedded in an experiment report. */
public record RunDiagnostics(
        ClassHistogram baselineHistogram,
        ClassHistogram finalHistogram,
        HistogramDiff histogramDiff,
        RetentionSnapshot baselineRetention,
        RetentionSnapshot finalRetention,
        List<RetentionSnapshot> retentionSeries,
        WorldStoreSnapshot baselineWorldStore,
        WorldStoreSnapshot finalWorldStore,
        EventMetricsSnapshot baselineEvents,
        EventMetricsSnapshot finalEvents,
        List<String> warnings
) {
    public static final RunDiagnostics EMPTY = new RunDiagnostics(
            null, null, null, null, null, List.of(), null, null, null, null, List.of());

    public RunDiagnostics {
        retentionSeries = retentionSeries == null
                ? List.of() : Collections.unmodifiableList(List.copyOf(retentionSeries));
        warnings = warnings == null ? List.of() : Collections.unmodifiableList(List.copyOf(warnings));
    }
}
