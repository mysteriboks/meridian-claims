package com.meridian.claims.service;

/**
 * Immutable snapshot of system health, returned by {@link HealthService} and
 * rendered by the /health endpoint. Plain POJO with explicit getters — no
 * Lombok, per project style.
 */
public class HealthStatus {

    private final boolean databaseUp;
    private final String databaseMessage;
    private final int poolActive;
    private final int poolIdle;
    private final int poolMaxTotal;

    public HealthStatus(boolean databaseUp, String databaseMessage,
                        int poolActive, int poolIdle, int poolMaxTotal) {
        this.databaseUp = databaseUp;
        this.databaseMessage = databaseMessage;
        this.poolActive = poolActive;
        this.poolIdle = poolIdle;
        this.poolMaxTotal = poolMaxTotal;
    }

    /** Overall status string for display / monitoring scrape. */
    public String getStatus() {
        return databaseUp ? "UP" : "DOWN";
    }

    public boolean isDatabaseUp() {
        return databaseUp;
    }

    public String getDatabaseMessage() {
        return databaseMessage;
    }

    public int getPoolActive() {
        return poolActive;
    }

    public int getPoolIdle() {
        return poolIdle;
    }

    public int getPoolMaxTotal() {
        return poolMaxTotal;
    }
}
