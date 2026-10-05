package com.dwurdy.testmod.persistententity;

import net.minecraft.world.entity.Mob;

import java.util.UUID;

/** Strong-reference entity record used only by the intentional test fixture. */
public final class PersistentEntityRecord {
    private final Mob entity;
    private final UUID entityId;
    private final String dimension;
    private final long loadNumber;
    private final byte[] diagnosticPayload = new byte[256 * 1024];

    public PersistentEntityRecord(Mob entity, String dimension, long loadNumber) {
        this.entity = entity;
        this.entityId = entity.getUUID();
        this.dimension = dimension;
        this.loadNumber = loadNumber;
        diagnosticPayload[0] = (byte) (loadNumber & 0x7f);
    }

    public Mob entity() {
        return entity;
    }

    public UUID entityId() {
        return entityId;
    }

    public String dimension() {
        return dimension;
    }

    public long loadNumber() {
        return loadNumber;
    }
}
