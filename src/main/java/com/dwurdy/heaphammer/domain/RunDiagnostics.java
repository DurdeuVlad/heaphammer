package com.dwurdy.heaphammer.domain;

import com.github.bsideup.jabel.Desugar;

import com.dwurdy.heaphammer.diagnostics.ClassHistogram;
import com.dwurdy.heaphammer.diagnostics.EventMetricsSnapshot;
import com.dwurdy.heaphammer.diagnostics.HistogramDiff;
import com.dwurdy.heaphammer.diagnostics.RetentionSnapshot;
import com.dwurdy.heaphammer.diagnostics.WorldStoreSnapshot;

import java.util.Collections;
import java.util.List;

/** Structured optional evidence embedded in an experiment report. */
@Desugar
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
            null, null, null, null, null, com.dwurdy.heaphammer.infrastructure.LegacyCollections.list(), null, null, null, null, com.dwurdy.heaphammer.infrastructure.LegacyCollections.list());

    public RunDiagnostics {
        retentionSeries = retentionSeries == null
                ? com.dwurdy.heaphammer.infrastructure.LegacyCollections.list() : Collections.unmodifiableList(com.dwurdy.heaphammer.infrastructure.LegacyCollections.copy(retentionSeries));
        warnings = warnings == null ? com.dwurdy.heaphammer.infrastructure.LegacyCollections.list() : Collections.unmodifiableList(com.dwurdy.heaphammer.infrastructure.LegacyCollections.copy(warnings));
    }
}
