package com.dwurdy.heaphammer.fixture;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Controlled synthetic leak fixtures for validating HeapHammer detection accuracy (Sections 23.3, 31).
 */
public final class SyntheticLeakFixture {
    public enum Mode {
        OFF,
        LEAK,
        CLEAN,
        BOUNDED
    }

    private static volatile Mode currentMode = Mode.OFF;
    private static final List<byte[]> LEAK_STORAGE = new CopyOnWriteArrayList<>();
    private static final int BOUNDED_MAX_ELEMENTS = 3;
    private static final Deque<byte[]> BOUNDED_STORAGE = new ArrayDeque<>();

    private SyntheticLeakFixture() {}

    public static Mode getMode() {
        return currentMode;
    }

    public static void setMode(Mode mode) {
        currentMode = Objects.requireNonNull(mode, "mode must not be null");
    }

    public static void onCycle(int iteration, int sizeMb) {
        int bytes = Math.max(1, sizeMb) * 1024 * 1024;
        switch (currentMode) {
            case LEAK -> {
                byte[] block = new byte[bytes];
                Arrays.fill(block, (byte) (iteration & 0xFF));
                LEAK_STORAGE.add(block);
            }
            case BOUNDED -> {
                synchronized (BOUNDED_STORAGE) {
                    byte[] block = new byte[bytes];
                    Arrays.fill(block, (byte) (iteration & 0xFF));
                    BOUNDED_STORAGE.addLast(block);
                    while (BOUNDED_STORAGE.size() > BOUNDED_MAX_ELEMENTS) {
                        BOUNDED_STORAGE.removeFirst();
                    }
                }
            }
            case CLEAN -> {
                byte[] ephemeral = new byte[bytes];
                Arrays.fill(ephemeral, (byte) 1);
                // Not stored; eligible for immediate GC
            }
            case OFF -> {}
        }
    }

    public static int getRetainedCount() {
        return LEAK_STORAGE.size() + BOUNDED_STORAGE.size();
    }

    public static long getRetainedBytes() {
        long total = 0;
        for (byte[] b : LEAK_STORAGE) {
            total += b.length;
        }
        synchronized (BOUNDED_STORAGE) {
            for (byte[] b : BOUNDED_STORAGE) {
                total += b.length;
            }
        }
        return total;
    }

    public static void reset() {
        currentMode = Mode.OFF;
        LEAK_STORAGE.clear();
        synchronized (BOUNDED_STORAGE) {
            BOUNDED_STORAGE.clear();
        }
    }
}
