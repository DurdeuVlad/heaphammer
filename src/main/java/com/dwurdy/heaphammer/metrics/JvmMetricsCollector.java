package com.dwurdy.heaphammer.metrics;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

/**
 * Collects lightweight JVM memory and garbage collection metrics via standard MXBeans.
 */
public class JvmMetricsCollector {
    private final MemoryMXBean memoryMXBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    private final java.util.function.LongSupplier heapUsedSupplier;

    public JvmMetricsCollector() {
        this(ManagementFactory.getMemoryMXBean(), ManagementFactory.getGarbageCollectorMXBeans(), null);
    }

    public JvmMetricsCollector(MemoryMXBean memoryMXBean, List<GarbageCollectorMXBean> gcBeans) {
        this(memoryMXBean, gcBeans, null);
    }

    public JvmMetricsCollector(java.util.function.LongSupplier heapUsedSupplier) {
        this(ManagementFactory.getMemoryMXBean(), ManagementFactory.getGarbageCollectorMXBeans(), heapUsedSupplier);
    }

    public JvmMetricsCollector(MemoryMXBean memoryMXBean, List<GarbageCollectorMXBean> gcBeans, java.util.function.LongSupplier heapUsedSupplier) {
        this.memoryMXBean = memoryMXBean;
        this.gcBeans = gcBeans;
        this.heapUsedSupplier = heapUsedSupplier;
    }

    public long getHeapUsedBytes() {
        if (heapUsedSupplier != null) {
            return heapUsedSupplier.getAsLong();
        }
        MemoryUsage usage = memoryMXBean.getHeapMemoryUsage();
        return usage != null ? usage.getUsed() : 0L;
    }

    public long getHeapCommittedBytes() {
        MemoryUsage usage = memoryMXBean.getHeapMemoryUsage();
        return usage != null ? usage.getCommitted() : 0L;
    }

    public long getHeapMaxBytes() {
        MemoryUsage usage = memoryMXBean.getHeapMemoryUsage();
        return usage != null ? usage.getMax() : 0L;
    }

    public long getNonHeapUsedBytes() {
        MemoryUsage usage = memoryMXBean.getNonHeapMemoryUsage();
        return usage != null ? usage.getUsed() : 0L;
    }

    public long getTotalGcCount() {
        long count = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            long c = gc.getCollectionCount();
            if (c > 0) count += c;
        }
        return count;
    }

    public long getTotalGcTimeMs() {
        long time = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            long t = gc.getCollectionTime();
            if (t > 0) time += t;
        }
        return time;
    }
}
