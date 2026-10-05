package com.dwurdy.heaphammer.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlayerLifecycleObserversTest {
    @Test
    @DisplayName("notifies and unregisters loader-neutral join observers")
    void notifiesAndUnregistersJoinObserver() {
        AtomicReference<Object> observed = new AtomicReference<>();
        Consumer<Object> observer = observed::set;
        Object player = new Object();

        PlayerLifecycleObservers.registerJoinObserver(observer);
        try {
            PlayerLifecycleObservers.notifyJoined(player);
            assertSame(player, observed.get());
            observed.set(null);
            PlayerLifecycleObservers.unregisterJoinObserver(observer);
            PlayerLifecycleObservers.notifyJoined(new Object());
            assertNull(observed.get());
        } finally {
            PlayerLifecycleObservers.unregisterJoinObserver(observer);
        }
    }
}
