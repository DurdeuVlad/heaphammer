package com.dwurdy.heaphammer.platform;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectivePlayerLifecyclePortTest {
    @Test
    void playerListScanConfirmsPlayerWhenUuidLookupIsNotVisibleYet() {
        UUID playerId = UUID.randomUUID();
        Object listedPlayer = new FakePlayer(playerId);

        assertTrue(ReflectivePlayerLifecyclePort.isPlayerTracked(
                new FakePlayerList(listedPlayer), new FakePlayer(playerId), playerId));
    }

    private static final class FakePlayerList {
        private final Object listedPlayer;

        private FakePlayerList(Object listedPlayer) {
            this.listedPlayer = listedPlayer;
        }

        public Object getPlayer(UUID ignored) {
            return null;
        }

        public List<Object> getPlayers() {
            return Collections.singletonList(listedPlayer);
        }
    }

    private static final class FakePlayer {
        private final UUID playerId;

        private FakePlayer(UUID playerId) {
            this.playerId = playerId;
        }

        public FakeProfile getGameProfile() {
            return new FakeProfile(playerId);
        }
    }

    private static final class FakeProfile {
        private final UUID playerId;

        private FakeProfile(UUID playerId) {
            this.playerId = playerId;
        }

        public UUID getId() {
            return playerId;
        }
    }
}
