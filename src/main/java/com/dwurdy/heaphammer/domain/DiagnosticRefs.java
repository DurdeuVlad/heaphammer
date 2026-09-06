package com.dwurdy.heaphammer.domain;

import java.util.Collections;
import java.util.List;

/**
 * Diagnostic artifact references associated with an experiment run (Section 14.1).
 */
public record DiagnosticRefs(
        List<String> histogramRefs,
        List<String> heapDumpRefs,
        List<String> jfrRefs
) {
    public static final DiagnosticRefs EMPTY = new DiagnosticRefs(List.of(), List.of(), List.of());

    public DiagnosticRefs {
        histogramRefs = (histogramRefs == null) ? List.of() : Collections.unmodifiableList(List.copyOf(histogramRefs));
        heapDumpRefs = (heapDumpRefs == null) ? List.of() : Collections.unmodifiableList(List.copyOf(heapDumpRefs));
        jfrRefs = (jfrRefs == null) ? List.of() : Collections.unmodifiableList(List.copyOf(jfrRefs));
    }
}
