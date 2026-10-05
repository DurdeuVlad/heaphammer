package com.dwurdy.testmod.gazetrack;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Per-observation record retaining the looked-at player plus its serialized
 * NBT payload — the shape of Paper #14088 (map viewers retaining departed
 * ServerPlayers) and GTNH #16112 (per-tick serialization of the looked-at
 * player's data).
 */
public final class GazeTargetRecord {
    private final ServerPlayer target;
    private final UUID targetId;
    private final String targetName;
    private final UUID observerId;
    private final long observationNumber;
    private final byte[] serializedTarget;

    public GazeTargetRecord(UUID observerId, ServerPlayer target, byte[] serializedTarget, long observationNumber) {
        this.observerId = observerId;
        this.target = target;
        this.targetId = target.getUUID();
        this.targetName = target.getGameProfile().getName();
        this.observationNumber = observationNumber;
        this.serializedTarget = serializedTarget;
    }

    public ServerPlayer target() {
        return target;
    }

    public UUID targetId() {
        return targetId;
    }

    public String targetName() {
        return targetName;
    }

    public UUID observerId() {
        return observerId;
    }

    public long observationNumber() {
        return observationNumber;
    }

    public int payloadBytes() {
        return serializedTarget.length;
    }
}
