package com.dwurdy.testmod.crossmod.core;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Encapsulates an active event subscription held in the core event bus.
 */
public class EventSubscription {
    private final String channel;
    private final Consumer<Object> listener;
    private final long subscribedAt;

    public EventSubscription(String channel, Consumer<Object> listener) {
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.subscribedAt = System.currentTimeMillis();
    }

    public String getChannel() {
        return channel;
    }

    public Consumer<Object> getListener() {
        return listener;
    }

    public long getSubscribedAt() {
        return subscribedAt;
    }
}
