package com.dwurdy.heaphammer.diagnostics;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weak-reference-only retention tracker. The tracker deliberately never stores a
 * strong reference to an observed object.
 */
public final class RetentionTracker {
    private final Map<String, List<WeakReference<Object>>> references = new ConcurrentHashMap<>();
    private volatile Set<String> trackedClasses = Collections.emptySet();

    public void setTrackedClasses(java.util.Collection<String> classNames) {
        if (classNames == null || classNames.isEmpty()) {
            trackedClasses = Collections.emptySet();
        } else {
            trackedClasses = Collections.unmodifiableSet(new java.util.HashSet<>(classNames));
        }
        references.clear();
    }

    public void observe(String className, Object value) {
        Objects.requireNonNull(className, "className must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (!trackedClasses.isEmpty() && !trackedClasses.contains(className)) return;
        references.computeIfAbsent(className, ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(new WeakReference<>(value));
    }

    public RetentionSnapshot snapshot() {
        Map<String, RetentionClassEntry> entries = new LinkedHashMap<>();
        for (Map.Entry<String, List<WeakReference<Object>>> entry : references.entrySet()) {
            long live = 0L;
            List<WeakReference<Object>> refs = entry.getValue();
            synchronized (refs) {
                for (WeakReference<Object> reference : refs) {
                    if (reference.get() != null) {
                        live++;
                    }
                }
            }
            entries.put(entry.getKey(), new RetentionClassEntry(entry.getKey(), refs.size(), live));
        }
        return new RetentionSnapshot(System.currentTimeMillis(), entries);
    }

    public int trackedReferenceCount() {
        int count = 0;
        for (List<WeakReference<Object>> refs : references.values()) {
            count += refs.size();
        }
        return count;
    }

    public void clear() {
        references.clear();
    }
}
