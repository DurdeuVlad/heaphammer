package com.dwurdy.testmod.crossmod.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Static central event bus shared across mods.
 * Intended to demonstrate how uncleaned listener registrations retain closures across mod boundaries.
 */
public final class CrossModEventBus {
    private static final List<EventSubscription> SUBSCRIPTIONS = new CopyOnWriteArrayList<>();

    private CrossModEventBus() {}

    public static EventSubscription subscribe(String channel, Consumer<Object> listener) {
        EventSubscription sub = new EventSubscription(channel, listener);
        SUBSCRIPTIONS.add(sub);
        return sub;
    }

    public static boolean unsubscribe(EventSubscription subscription) {
        return SUBSCRIPTIONS.remove(subscription);
    }

    public static void publish(String channel, Object event) {
        for (EventSubscription sub : SUBSCRIPTIONS) {
            if (sub.getChannel().equals(channel)) {
                try {
                    sub.getListener().accept(event);
                } catch (Exception ignored) {}
            }
        }
    }

    public static int getSubscriptionCount() {
        return SUBSCRIPTIONS.size();
    }

    public static void clearAll() {
        SUBSCRIPTIONS.clear();
    }
}
