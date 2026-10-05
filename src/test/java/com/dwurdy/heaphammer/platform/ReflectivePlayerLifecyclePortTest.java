package com.dwurdy.heaphammer.platform;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Object player = new Object();
        listener.player = player;
        networkConnection.packetListener = listener;
        networkConnection.disconnectListener = listener;

        Method release = ReflectivePlayerLifecyclePort.class
                .getDeclaredMethod("releaseDisconnectedReferences", Object.class);
        release.setAccessible(true);
        release.invoke(null, listener);

        // Entity trackers keep Connection records that dereference
        // listener.player on every later entity removal; severing it
        // corrupts unrelated despawns (ServerEntity.removePairing NPE).
        assertSame(player, listener.player);
        assertNull(networkConnection.packetListener);
        assertNull(networkConnection.disconnectListener);
    }

    @Test
    void housekeepingTickDrainsQueuedOutboundMessages() {
        FakePlayer player = new FakePlayer(UUID.randomUUID());
        FakeListener listener = new FakeListener();
        FakeNetworkConnection networkConnection = new FakeNetworkConnection();
        FakeChannel channel = new FakeChannel();
        player.connection = listener;
        listener.connection = networkConnection;
        networkConnection.channel = channel;
        channel.outbound.add(new Object());
        channel.outbound.add(new Object());
        channel.outbound.add(new Object());

        ReflectivePlayerLifecyclePort port = new ReflectivePlayerLifecyclePort(
                () -> new FakeServer(new FakePlayerList(player)));
        port.housekeepingTick();

        assertTrue(channel.outbound.isEmpty());
    }

    @Test
    void lookAtPointsViewVectorAtTarget() throws Exception {
        FakePlayer player = new FakePlayer(UUID.randomUUID());
        player.x = 0.0;
        player.y = 64.0;
        player.z = 0.0;

        Method lookAt = ReflectivePlayerLifecyclePort.class
                .getDeclaredMethod("lookAt", Object.class, double.class, double.class, double.class);
        lookAt.setAccessible(true);
        Object result = lookAt.invoke(null, player, 0.0, 75.6, 3.0);

        assertEquals(Boolean.TRUE, result);
        assertTrue(player.rotCalled, "setRot must be invoked on the player");
        assertEquals(0.0F, player.yaw, 0.01F);
        assertTrue(player.pitch < 0.0F, "looking up at a higher target must pitch upward");
        assertEquals(player.yaw, player.yRotO, 0.001F);
        assertEquals(player.pitch, player.xRotO, 0.001F);
    }

    private static final class FakeServer {
        private final Object playerList;

        private FakeServer(Object playerList) {
            this.playerList = playerList;
        }

        public Object getPlayerList() {
            return playerList;
        }
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
        private Object connection;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
        private float yRotO;
        private float xRotO;
        private float yHeadRotO;
        private float yBodyRotO;
        private boolean rotCalled;

        private FakePlayer(UUID playerId) {
            this.playerId = playerId;
        }

        public FakeProfile getGameProfile() {
            return new FakeProfile(playerId);
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public double getEyeY() {
            return y + 1.6;
        }

        public double getEyeHeight() {
            return 1.6;
        }

        public void setRot(float yaw, float pitch) {
            this.rotCalled = true;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public void setYHeadRot(float ignored) {
        }

        public void setYBodyRot(float ignored) {
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

        public String getName() {
            return "hh_test_" + playerId.toString().replace("-", "").substring(0, 12);
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
        private Object channel;
    }

    private static final class FakeChannel {
        private final Deque<Object> outbound = new ArrayDeque<>();

        public Object readOutbound() {
            return outbound.poll();
        }

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
