package com.dwurdy.heaphammer.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects and parses JVM class histograms via the platform DiagnosticCommand MBean (Section 15.6).
 */
public class ClassHistogramCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClassHistogramCollector.class);

    private static final Pattern ENTRY_PATTERN = Pattern.compile("^\\s*(\\d+):\\s+(\\d+)\\s+(\\d+)\\s+(\\S+)");
    private static final Pattern TOTAL_PATTERN = Pattern.compile("^Total\\s+(\\d+)\\s+(\\d+)");

    private final MBeanServer mBeanServer;
    private final ObjectName diagnosticCommandName;

    public ClassHistogramCollector() {
        this(ManagementFactory.getPlatformMBeanServer());
    }

    public ClassHistogramCollector(MBeanServer mBeanServer) {
        this.mBeanServer = mBeanServer;
        ObjectName name = null;
        try {
            name = new ObjectName("com.sun.management:type=DiagnosticCommand");
        } catch (Exception e) {
            LOGGER.warn("Failed to create DiagnosticCommand ObjectName: {}", e.getMessage());
        }
        this.diagnosticCommandName = name;
    }

    public boolean isSupported() {
        if (mBeanServer == null || diagnosticCommandName == null) {
            return false;
        }
        try {
            return mBeanServer.isRegistered(diagnosticCommandName);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Captures a live JVM class histogram.
     *
     * @param limit maximum entries to retain in the histogram
     * @return Optional containing the ClassHistogram if successful, or empty if unsupported
     */
    public Optional<ClassHistogram> capture(int limit) {
        if (!isSupported()) {
            return Optional.empty();
        }

        try {
            Object result = mBeanServer.invoke(
                    diagnosticCommandName,
                    "gcClassHistogram",
                    new Object[]{new String[]{"-all"}},
                    new String[]{"[Ljava.lang.String;"}
            );

            if (result instanceof String output) {
                return Optional.of(parse(output, limit));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to capture class histogram via DiagnosticCommand MBean: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Parses the standard jcmd / gcClassHistogram text output into a ClassHistogram snapshot.
     *
     * @param rawText the raw stdout text from jcmd GC.class_histogram
     * @param limit   maximum number of entries to retain
     * @return parsed ClassHistogram
     */
    public static ClassHistogram parse(String rawText, int limit) {
        if (rawText == null || rawText.isBlank()) {
            return new ClassHistogram(System.currentTimeMillis(), 0L, 0L, List.of());
        }

        List<ClassHistogramEntry> entries = new ArrayList<>();
        long totalInstances = 0L;
        long totalBytes = 0L;

        String[] lines = rawText.split("\\R");
        for (String line : lines) {
            Matcher entryMatcher = ENTRY_PATTERN.matcher(line);
            if (entryMatcher.find()) {
                if (limit <= 0 || entries.size() < limit) {
                    int rank = Integer.parseInt(entryMatcher.group(1));
                    long instances = Long.parseLong(entryMatcher.group(2));
                    long bytes = Long.parseLong(entryMatcher.group(3));
                    String className = entryMatcher.group(4);
                    entries.add(new ClassHistogramEntry(rank, className, instances, bytes));
                }
                continue;
            }

            Matcher totalMatcher = TOTAL_PATTERN.matcher(line);
            if (totalMatcher.find()) {
                totalInstances = Long.parseLong(totalMatcher.group(1));
                totalBytes = Long.parseLong(totalMatcher.group(2));
            }
        }

        // Fallback totals if total line was omitted
        if (totalInstances == 0L && !entries.isEmpty()) {
            for (ClassHistogramEntry e : entries) {
                totalInstances += e.instances();
                totalBytes += e.bytes();
            }
        }

        return new ClassHistogram(System.currentTimeMillis(), totalInstances, totalBytes, entries);
    }
}
