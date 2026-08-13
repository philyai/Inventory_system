package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class ChangePasswordResponse {
    @SerializedName("message")
    private String message;

    @SerializedName("requires_reauthentication")
    private boolean requiresReauthentication;

    public String getMessage() { return message; }
    public boolean requiresReauthentication() { return requiresReauthentication; }
}
