package com.dwurdy.heaphammer.platform.fabric;

import com.dwurdy.heaphammer.domain.PlayerAction;
import com.dwurdy.heaphammer.diagnostics.RetentionTracker;
import com.dwurdy.heaphammer.platform.PlayerLifecyclePort;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Fabric 1.21.1 player lifecycle port. Login goes through PlayerList.placeNewPlayer
 * with a real in-memory server connection, so login/logout events and player-list
 * bookkeeping execute exactly as they do for a network client.
 */
public final class FabricPlayerLifecyclePort implements PlayerLifecyclePort {
    private static final String TEST_NAME_PREFIX = "hh_test_";
    private final Supplier<MinecraftServer> serverSupplier;
    private volatile RetentionTracker retentionTracker;

    public FabricPlayerLifecyclePort(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = serverSupplier;
    }

    @Override
    public UUID join(String dimension, String profileName, UUID playerId, double x, double y, double z) {
        MinecraftServer server = serverSupplier.get();
        if (server == null || server.getPlayerList().getPlayer(playerId) != null) return null;
        ServerLevel level = getLevel(server, dimension);
        if (level == null) return null;

        GameProfile profile = new GameProfile(playerId, profileName);
        if (server.getPlayerList().canPlayerLogin(new InetSocketAddress("127.0.0.1", 0), profile) != null) {
            return null;
        }
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, level, profile, cookie.clientInformation());
        player.setPos(x, y, z);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        RetentionTracker tracker = retentionTracker;
        if (tracker != null) tracker.observe(player.getClass().getName(), player);
        return server.getPlayerList().getPlayer(playerId) == null ? null : playerId;
    }

    public void attach(RetentionTracker tracker) {
        retentionTracker = tracker;
    }

    @Override
    public boolean perform(String dimension, UUID playerId, PlayerAction action, String targetDimension,
                           double x, double y, double z) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return false;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return false;
        if (action == PlayerAction.TELEPORT) {
            player.teleportTo(x, y, z);
            return true;
        }
        if (action == PlayerAction.DIMCHANGE) {
            ServerLevel target = getLevel(server, targetDimension);
            if (target == null) return false;
            return player.teleportTo(target, x, y, z, Collections.emptySet(), player.getYRot(), player.getXRot());
        }
        if (action == PlayerAction.RESPAWN) {
            return server.getPlayerList().respawn(player, false, Entity.RemovalReason.DISCARDED) != null;
        }
        return true;
    }

    @Override
    public boolean quit(String dimension, UUID playerId) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return false;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return false;
        player.connection.onDisconnect(new DisconnectionDetails(Component.literal("HeapHammer lifecycle cycle complete")));
        return server.getPlayerList().getPlayer(playerId) == null;
    }

    @Override
    public int activeTestPlayerCount() {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return 0;
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isTestPlayer(player)) count++;
        }
        return count;
    }

    @Override
    public int cleanupTestPlayers() {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return 0;
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers().toArray(new ServerPlayer[0])) {
            if (isTestPlayer(player)) {
                player.connection.onDisconnect(new DisconnectionDetails(Component.literal("HeapHammer cleanup")));
                count++;
            }
        }
        return count;
    }

    private static boolean isTestPlayer(ServerPlayer player) {
        return player.getGameProfile().getName() != null
                && player.getGameProfile().getName().startsWith(TEST_NAME_PREFIX);
    }

    private static ServerLevel getLevel(MinecraftServer server, String dimension) {
        ResourceLocation location = ResourceLocation.tryParse(dimension);
        if (location == null) return null;
        return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, location));
    }
}
