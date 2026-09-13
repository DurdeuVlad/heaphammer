package com.dwurdy.heaphammer.platform;

import com.dwurdy.heaphammer.diagnostics.RetentionTracker;

/** Loader hook that registers lifecycle objects without retaining them strongly. */
public interface RetentionObservationPort {
    void attach(RetentionTracker tracker);
}
