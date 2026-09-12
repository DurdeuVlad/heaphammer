package com.dwurdy.heaphammer.report;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Differential attribution evidence between two run reports (B minus A). */
public record ReportEvidenceDiff(
        Map<String, Long> classGrowthBytesDifference,
        Map<String, Long> retainedLiveDifference,
        long worldStoreBytesDifference,
        long worldStoreEntitiesDifference,
        Map<String, Long> eventDispatchDifference
) {
    public ReportEvidenceDiff {
        classGrowthBytesDifference = immutable(classGrowthBytesDifference);
        retainedLiveDifference = immutable(retainedLiveDifference);
        eventDispatchDifference = immutable(eventDispatchDifference);
    }

    public static ReportEvidenceDiff empty() {
        return new ReportEvidenceDiff(Collections.emptyMap(), Collections.emptyMap(), 0L, 0L, Collections.emptyMap());
    }

    private static Map<String, Long> immutable(Map<String, Long> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Collections.emptyMap() : values));
    }
}
