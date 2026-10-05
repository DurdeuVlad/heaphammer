package com.dwurdy.testmod.fakeplayerfactory;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Strong-reference record retaining a machine-constructed ServerPlayer.
 * Mirrors NeoForge issue #1487 and Fabric #3974/#4487: every FakePlayer or
 * raw ServerPlayer built with a fresh UUID permanently registers advancement
 * criterion listeners, so machinery that deploys an operator per operation
 * accumulates PlayerAdvancements/CriteriaTrigger nodes indefinitely.
 */
public final class FactoryPlayerRecord {
    private final ServerPlayer player;
    private final UUID operatorId;
    private final String operatorName;
    private final long operationNumber;
    private final byte[] operatorPayload = new byte[64 * 1024];

    public FactoryPlayerRecord(ServerPlayer player, long operationNumber) {
        this.player = player;
        this.operatorId = player.getUUID();
        this.operatorName = player.getGameProfile().getName();
        this.operationNumber = operationNumber;
        operatorPayload[0] = (byte) (operationNumber & 0x7f);
    }

    public ServerPlayer player() {
        return player;
    }

    public UUID operatorId() {
        return operatorId;
    }

    public String operatorName() {
        return operatorName;
    }

    public long operationNumber() {
        return operationNumber;
    }
}
