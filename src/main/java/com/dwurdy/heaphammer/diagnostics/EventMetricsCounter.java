package com.dwurdy.heaphammer.diagnostics;

import com.dwurdy.heaphammer.platform.EventMetricsPort;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Thread-safe counter sink for loader event hooks; it stores names and counts only. */
public final class EventMetricsCounter implements EventMetricsPort {
    private final Map<String, LongAdder> dispatchCounts = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> registrationCounts = new ConcurrentHashMap<>();

    public void recordDispatch(String eventName) {
        dispatchCounts.computeIfAbsent(normalize(eventName), ignored -> new LongAdder()).increment();
    }

    public void recordRegistration(String eventName) {
        registrationCounts.computeIfAbsent(normalize(eventName), ignored -> new LongAdder()).increment();
    }

    @Override
    public EventMetricsSnapshot snapshot() {
        return new EventMetricsSnapshot(System.currentTimeMillis(), copy(dispatchCounts), copy(registrationCounts));
    }

    public void clear() {
        dispatchCounts.clear();
        registrationCounts.clear();
    }

    private static Map<String, Long> copy(Map<String, LongAdder> source) {
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, value.sum()));
        return result;
    }

    private static String normalize(String eventName) {
        if (eventName == null || eventName.trim().isEmpty()) throw new IllegalArgumentException("eventName must not be blank");
        return eventName.trim();
    }
}
