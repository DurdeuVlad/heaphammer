package com.dwurdy.heaphammer.platform;

import com.dwurdy.heaphammer.diagnostics.WorldStoreSnapshot;

/** Resolves world roots for an asynchronous, read-only entity-store scan. */
public interface WorldStoreMetricsPort {
    WorldStoreSnapshot capture();
}
