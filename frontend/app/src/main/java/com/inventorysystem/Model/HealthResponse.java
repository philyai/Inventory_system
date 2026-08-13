package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class HealthResponse {
    @SerializedName("status")
    private String status;

    @SerializedName("database")
    private String database;

    public String getStatus() { return status; }
    public String getDatabase() { return database; }

    public boolean isHealthy() {
        return "ok".equalsIgnoreCase(status)
                && "connected".equalsIgnoreCase(database);
    }
}
