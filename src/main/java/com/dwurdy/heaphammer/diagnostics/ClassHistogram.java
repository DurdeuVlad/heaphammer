package com.dwurdy.heaphammer.diagnostics;

import com.github.bsideup.jabel.Desugar;

import java.util.*;

/**
 * An immutable snapshot of JVM class instances and memory footprint.
 */
@Desugar
public record ClassHistogram(
        long timestamp,
        long totalInstances,
        long totalBytes,
        List<ClassHistogramEntry> entries
) {
    public ClassHistogram {
        Objects.requireNonNull(entries, "entries must not be null");
        entries = Collections.unmodifiableList(entries);
    }

    /**
     * Computes the differential growth comparing this target histogram against a baseline.
     *
     * @param baseline the earlier baseline histogram to compare against
     * @param limit    the maximum number of top growing classes to return
     * @return a HistogramDiff showing net positive growth
     */
    public HistogramDiff diff(ClassHistogram baseline, int limit) {
        if (baseline == null) {
            List<HistogramDiffEntry> diffs = entries.stream()
                    .limit(limit)
                    .map(e -> new HistogramDiffEntry(e.className(), e.instances(), e.bytes()))
                    .collect(java.util.stream.Collectors.toList());
            return new HistogramDiff(diffs, totalInstances, totalBytes);
        }

        Map<String, ClassHistogramEntry> baseMap = new HashMap<>();
        for (ClassHistogramEntry entry : baseline.entries()) {
            baseMap.put(entry.className(), entry);
        }

        List<HistogramDiffEntry> diffEntries = new ArrayList<>();
        for (ClassHistogramEntry current : entries) {
            ClassHistogramEntry base = baseMap.get(current.className());
            long baseInstances = (base != null) ? base.instances() : 0L;
            long baseBytes = (base != null) ? base.bytes() : 0L;

            long deltaInstances = current.instances() - baseInstances;
            long deltaBytes = current.bytes() - baseBytes;

            if (deltaBytes > 0 || deltaInstances > 0) {
                diffEntries.add(new HistogramDiffEntry(current.className(), deltaInstances, deltaBytes));
            }
        }

        // Sort descending by delta bytes
        diffEntries.sort(Comparator.comparingLong(HistogramDiffEntry::deltaBytes).reversed());

        List<HistogramDiffEntry> topDiffs = diffEntries.stream()
                .limit(limit > 0 ? limit : 20)
                .collect(java.util.stream.Collectors.toList());

        long netInstances = this.totalInstances - baseline.totalInstances();
        long netBytes = this.totalBytes - baseline.totalBytes();

        return new HistogramDiff(topDiffs, netInstances, netBytes);
    }
}
