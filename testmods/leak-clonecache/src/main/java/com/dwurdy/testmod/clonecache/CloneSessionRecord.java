package com.dwurdy.testmod.clonecache;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Strong-reference clone record used only by the intentional test fixture.
 * In LEAK mode this record retains the pre-clone ServerPlayer, mirroring the
 * clone-boundary retention bugs catalogued across ~15 mods in AllTheLeaks
 * (ae2wtlib, architectury, beansbackpacks, curios, and friends).
 */
public final class CloneSessionRecord {
    private final ServerPlayer player;
    private final UUID playerId;
    private final String playerName;
    private final long cloneNumber;
    private final byte[] snapshotPayload = new byte[256 * 1024];

    public CloneSessionRecord(ServerPlayer player, long cloneNumber) {
        this.player = player;
        this.playerId = player.getUUID();
        this.playerName = player.getGameProfile().getName();
        this.cloneNumber = cloneNumber;
        snapshotPayload[0] = (byte) (cloneNumber & 0x7f);
    }

    public ServerPlayer player() {
        return player;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public long cloneNumber() {
        return cloneNumber;
    }
}
