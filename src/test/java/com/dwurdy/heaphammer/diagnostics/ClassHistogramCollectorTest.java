package com.dwurdy.heaphammer.diagnostics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ClassHistogramCollectorTest {

    private static final String SAMPLE_HISTOGRAM = """
         num     #instances         #bytes  class name (module)
        -------------------------------------------------------
           1:        100000        5000000  java.lang.String (java.base@21.0.3)
           2:         50000        3000000  java.util.HashMap$Node (java.base@21.0.3)
           3:         20000        1000000  [B (java.base@21.0.3)
        Total        170000        9000000
        """;

    private static final String SAMPLE_HISTOGRAM_GROWTH = """
         num     #instances         #bytes  class name (module)
        -------------------------------------------------------
           1:        150000        7500000  java.lang.String (java.base@21.0.3)
           2:         50000        3000000  java.util.HashMap$Node (java.base@21.0.3)
           3:         30000        2000000  [B (java.base@21.0.3)
        Total        230000       12500000
        """;

    @Test
    @DisplayName("ClassHistogramCollector accurately parses jcmd output")
    void testParse() {
        ClassHistogram hist = ClassHistogramCollector.parse(SAMPLE_HISTOGRAM, 10);

        assertNotNull(hist);
        assertEquals(3, hist.entries().size());
        assertEquals(170000L, hist.totalInstances());
        assertEquals(9000000L, hist.totalBytes());

        ClassHistogramEntry first = hist.entries().get(0);
        assertEquals(1, first.rank());
        assertEquals("java.lang.String", first.className());
        assertEquals(100000L, first.instances());
        assertEquals(5000000L, first.bytes());
    }

    @Test
    @DisplayName("ClassHistogram diff correctly identifies top growing classes")
    void testDiff() {
        ClassHistogram base = ClassHistogramCollector.parse(SAMPLE_HISTOGRAM, 10);
        ClassHistogram current = ClassHistogramCollector.parse(SAMPLE_HISTOGRAM_GROWTH, 10);

        HistogramDiff diff = current.diff(base, 5);

        assertNotNull(diff);
        assertEquals(60000L, diff.totalDeltaInstances());
        assertEquals(3500000L, diff.totalDeltaBytes());

        List<HistogramDiffEntry> topGrowing = diff.topGrowingClasses();
        assertEquals(2, topGrowing.size()); // String and [B grew; HashMap$Node was unchanged

        HistogramDiffEntry top1 = topGrowing.get(0);
        assertEquals("java.lang.String", top1.className());
        assertEquals(50000L, top1.deltaInstances());
        assertEquals(2500000L, top1.deltaBytes());

        HistogramDiffEntry top2 = topGrowing.get(1);
        assertEquals("[B", top2.className());
        assertEquals(10000L, top2.deltaInstances());
        assertEquals(1000000L, top2.deltaBytes());
    }

    @Test
    @DisplayName("Live histogram capture runs cleanly if MBean is present")
    void testLiveCapture() {
        ClassHistogramCollector collector = new ClassHistogramCollector();
        if (collector.isSupported()) {
            Optional<ClassHistogram> capture = collector.capture(20);
            assertTrue(capture.isPresent());
            assertFalse(capture.get().entries().isEmpty());
            assertTrue(capture.get().totalInstances() > 0);
        }
    }
}
