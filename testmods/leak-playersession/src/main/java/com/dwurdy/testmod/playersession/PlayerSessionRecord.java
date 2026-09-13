package com.dwurdy.testmod.playersession;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Strong-reference session record used only by the intentional test fixture. */
public final class PlayerSessionRecord {
    private final ServerPlayer player;
    private final UUID playerId;
    private final String playerName;
    private final long joinNumber;
    private final byte[] diagnosticPayload = new byte[256 * 1024];

    public PlayerSessionRecord(ServerPlayer player, long joinNumber) {
        this.player = player;
        this.playerId = player.getUUID();
        this.playerName = player.getGameProfile().getName();
        this.joinNumber = joinNumber;
        diagnosticPayload[0] = (byte) (joinNumber & 0x7f);
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

    public long joinNumber() {
        return joinNumber;
    }
}
