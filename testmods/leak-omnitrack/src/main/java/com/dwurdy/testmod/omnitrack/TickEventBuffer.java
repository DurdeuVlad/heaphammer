package com.dwurdy.testmod.omnitrack;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Unbounded event buffer retaining server tick timestamps and diagnostic metrics.
 */
public class TickEventBuffer {
    public static class TickEventEntry {
        private final long tickNumber;
        private final long epochMillis;
        private final long freeMemory;
        private final byte[] padding;

        public TickEventEntry(long tickNumber, long epochMillis, long freeMemory) {
            this.tickNumber = tickNumber;
            this.epochMillis = epochMillis;
            this.freeMemory = freeMemory;
            // 2KB per tick entry to accelerate retained heap growth if unpurged
            this.padding = new byte[2048];
        }

        public long getTickNumber() {
            return tickNumber;
        }

        public long getEpochMillis() {
            return epochMillis;
        }

        public long getFreeMemory() {
            return freeMemory;
        }
    }

    private final Queue<TickEventEntry> buffer = new ConcurrentLinkedQueue<>();

    public void recordTick(long tickNumber) {
        buffer.add(new TickEventEntry(tickNumber, System.currentTimeMillis(), Runtime.getRuntime().freeMemory()));
    }

    public int size() {
        return buffer.size();
    }

    public void clear() {
        buffer.clear();
    }
}
