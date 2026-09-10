package com.dwurdy.heaphammer.domain;

import com.github.bsideup.jabel.Desugar;

import java.util.ArrayList;

import java.util.Collections;
import java.util.List;

/**
 * Diagnostic artifact references associated with an experiment run (Section 14.1).
 */
@Desugar
public record DiagnosticRefs(
        List<String> histogramRefs,
        List<String> heapDumpRefs,
        List<String> jfrRefs
) {
    public static final DiagnosticRefs EMPTY = new DiagnosticRefs(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

    public DiagnosticRefs {
        histogramRefs = (histogramRefs == null) ? Collections.emptyList() : Collections.unmodifiableList(Collections.unmodifiableList(new ArrayList<>(histogramRefs)));
        heapDumpRefs = (heapDumpRefs == null) ? Collections.emptyList() : Collections.unmodifiableList(Collections.unmodifiableList(new ArrayList<>(heapDumpRefs)));
        jfrRefs = (jfrRefs == null) ? Collections.emptyList() : Collections.unmodifiableList(Collections.unmodifiableList(new ArrayList<>(jfrRefs)));
    }
}
