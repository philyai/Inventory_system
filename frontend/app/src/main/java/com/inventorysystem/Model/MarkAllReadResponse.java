package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class MarkAllReadResponse {
    @SerializedName("message") private String message;
    @SerializedName("updated_count") private int updatedCount;
    public String getMessage() { return message; }
    public int getUpdatedCount() { return updatedCount; }
}
