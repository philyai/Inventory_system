package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class SystemInformationModel {
    @SerializedName("system_name") private String systemName;
    @SerializedName("application_version") private String applicationVersion;
    @SerializedName("firmware_version") private String firmwareVersion;
    @SerializedName("api_version") private String apiVersion;
    @SerializedName("node_version") private String nodeVersion;
    private String platform;
    @SerializedName("operating_system") private String operatingSystem;
    private String architecture;
    private DatabaseInformation database;
    @SerializedName("server_uptime_seconds") private long serverUptimeSeconds;

    public String getSystemName() { return systemName; }
    public String getApplicationVersion() { return applicationVersion; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public String getApiVersion() { return apiVersion; }
    public String getNodeVersion() { return nodeVersion; }
    public String getPlatform() { return platform; }
    public String getOperatingSystem() { return operatingSystem; }
    public String getArchitecture() { return architecture; }
    public DatabaseInformation getDatabase() { return database; }
    public long getServerUptimeSeconds() { return serverUptimeSeconds; }

    public static class DatabaseInformation {
        private String dialect;
        private String status;
        public String getDialect() { return dialect; }
        public String getStatus() { return status; }
    }
}
