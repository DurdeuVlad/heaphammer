package com.dwurdy.heaphammer.platform;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectivePlayerLifecyclePortTest {
    @Test
    void playerListScanConfirmsPlayerWhenUuidLookupIsNotVisibleYet() {
        UUID playerId = UUID.randomUUID();
        Object listedPlayer = new FakePlayer(playerId);

        assertTrue(ReflectivePlayerLifecyclePort.isPlayerTracked(
                new FakePlayerList(listedPlayer), new FakePlayer(playerId), playerId));
    }

    @Test
    void configuresSyntheticConnectionProtocolBeforePlacement() {
        FakeConnection connection = new FakeConnection();
        Object channel = new FakeChannel();

        assertTrue(ReflectivePlayerLifecyclePort.configureConnectionChannel(connection, channel));
        assertSame(channel, connection.channel);
        assertSame(channel, FakeConnection.configuredChannel);
    }

    @Test
    void releasesSyntheticDisconnectReferenceGraph() throws Exception {
        FakeListener listener = new FakeListener();
        FakeNetworkConnection networkConnection = new FakeNetworkConnection();
        listener.connection = networkConnection;
        listener.player = new Object();
        networkConnection.packetListener = listener;
        networkConnection.disconnectListener = listener;

        Method release = ReflectivePlayerLifecyclePort.class
                .getDeclaredMethod("releaseDisconnectedReferences", Object.class);
        release.setAccessible(true);
        release.invoke(null, listener);

        assertNull(listener.player);
        assertNull(networkConnection.packetListener);
        assertNull(networkConnection.disconnectListener);
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

    private static final class FakeConnection {
        private static Object configuredChannel;
        private Object channel;

        public static void setInitialProtocolAttributes(Object channel) {
            configuredChannel = channel;
        }

        public static Object getProtocolKey(Object flow) {
            return flow;
        }
    }

    private static final class FakeListener {
        private Object connection;
        private Object player;
    }

    private static final class FakeNetworkConnection {
        private Object packetListener;
        private Object disconnectListener;
    }

    private static final class FakeChannel {
        public FakeAttribute attr(Object key) {
            return new FakeAttribute();
        }
    }

    private static final class FakeAttribute {
        public FakeAttribute set(Object value) {
            return this;
        }
    }
}
