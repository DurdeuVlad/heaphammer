package com.dwurdy.heaphammer.platform;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Optional callbacks for integrations that need to observe player joins
 * without importing a Minecraft or loader-specific player type.
 */
public final class PlayerLifecycleObservers {
    private static final CopyOnWriteArrayList<Consumer<Object>> JOIN_OBSERVERS = new CopyOnWriteArrayList<>();

    private PlayerLifecycleObservers() {
    }

    public static void registerJoinObserver(Consumer<Object> observer) {
        if (observer != null) {
            JOIN_OBSERVERS.addIfAbsent(observer);
        }
    }

    public static void unregisterJoinObserver(Consumer<Object> observer) {
        JOIN_OBSERVERS.remove(observer);
    }

    public static void notifyJoined(Object player) {
        for (Consumer<Object> observer : JOIN_OBSERVERS) {
            try {
                observer.accept(player);
            } catch (RuntimeException ignored) {
                // Optional observers must not interfere with player login.
            }
        }
    }
}
