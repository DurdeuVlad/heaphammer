package com.dwurdy.testmod.playersession;

/** Small uniquely named object retained with each intentionally leaked session. */
public final class SessionLeakPayload {
    private final String playerName;
    private final long sessionNumber;

    public SessionLeakPayload(String playerName, long sessionNumber) {
        this.playerName = playerName;
        this.sessionNumber = sessionNumber;
    }

    public String playerName() {
        return playerName;
    }

    public long sessionNumber() {
        return sessionNumber;
    }
}
