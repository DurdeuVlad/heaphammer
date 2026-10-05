package com.dwurdy.testmod.cachelist;

/**
 * Audit entry appended on every cache rebuild. In LEAK mode these accumulate
 * without bound — modelling a mod whose recovery path allocates bookkeeping
 * (listeners, audit rows, diagnostics) every time its cache is found empty.
 */
public final class RebuildAuditRecord {
    private final long rebuildNumber;
    private final String reason;
    private final long timestamp;
    private final byte[] auditPayload = new byte[32 * 1024];

    public RebuildAuditRecord(long rebuildNumber, String reason) {
        this.rebuildNumber = rebuildNumber;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
        auditPayload[0] = (byte) (rebuildNumber & 0x7f);
    }

    public long rebuildNumber() {
        return rebuildNumber;
    }

    public String reason() {
        return reason;
    }

    public long timestamp() {
        return timestamp;
    }
}
