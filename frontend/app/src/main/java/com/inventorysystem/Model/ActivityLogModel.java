package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class ActivityLogModel {
    @SerializedName("login_session_id") private long loginSessionId;
    @SerializedName("login_time") private String loginTime;
    @SerializedName("logout_time") private String logoutTime;
    @SerializedName("device_info") private String deviceInfo;
    @SerializedName("ip_address") private String ipAddress;
    private String status;

    public long getLoginSessionId() { return loginSessionId; }
    public String getLoginTime() { return loginTime; }
    public String getLogoutTime() { return logoutTime; }
    public String getDeviceInfo() { return deviceInfo; }
    public String getIpAddress() { return ipAddress; }
    public String getStatus() { return status; }
}
