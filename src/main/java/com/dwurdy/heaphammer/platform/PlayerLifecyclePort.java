package com.dwurdy.heaphammer.platform;

import com.dwurdy.heaphammer.domain.PlayerAction;

import java.util.UUID;

/** Loader-neutral contract for authentic server-side player lifecycle churn. */
public interface PlayerLifecyclePort {
    UUID join(String dimension, String profileName, UUID profileId, double x, double y, double z);

    boolean perform(String dimension, UUID playerId, PlayerAction action, String targetDimension,
                   double x, double y, double z);

    boolean quit(String dimension, UUID playerId);

    int activeTestPlayerCount();

    int cleanupTestPlayers();
}
