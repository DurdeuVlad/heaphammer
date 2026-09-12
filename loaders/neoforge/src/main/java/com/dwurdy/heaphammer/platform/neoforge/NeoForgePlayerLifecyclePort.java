package com.dwurdy.heaphammer.platform.neoforge;

import com.dwurdy.heaphammer.domain.PlayerAction;
import com.dwurdy.heaphammer.platform.PlayerLifecyclePort;
import com.dwurdy.heaphammer.platform.ReflectivePlayerLifecyclePort;

import java.util.UUID;
import java.util.function.Supplier;

/** NeoForge entry point for the shared cross-version player lifecycle bridge. */
public final class NeoForgePlayerLifecyclePort implements PlayerLifecyclePort {
    private final ReflectivePlayerLifecyclePort delegate;

    public NeoForgePlayerLifecyclePort(Supplier<?> serverSupplier) {
        this.delegate = new ReflectivePlayerLifecyclePort(serverSupplier);
    }

    public void attach(com.dwurdy.heaphammer.diagnostics.RetentionTracker tracker) {
        delegate.attach(tracker);
    }

    @Override
    public UUID join(String dimension, String profileName, UUID profileId, double x, double y, double z) {
        return delegate.join(dimension, profileName, profileId, x, y, z);
    }

    @Override
    public boolean perform(String dimension, UUID playerId, PlayerAction action, String targetDimension,
                           double x, double y, double z) {
        return delegate.perform(dimension, playerId, action, targetDimension, x, y, z);
    }

    @Override
    public boolean quit(String dimension, UUID playerId) {
        return delegate.quit(dimension, playerId);
    }

    @Override
    public int activeTestPlayerCount() {
        return delegate.activeTestPlayerCount();
    }

    @Override
    public int cleanupTestPlayers() {
        return delegate.cleanupTestPlayers();
    }
}
