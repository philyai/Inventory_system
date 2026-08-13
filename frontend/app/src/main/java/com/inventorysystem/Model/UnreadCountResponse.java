package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class UnreadCountResponse {
    @SerializedName("unread_count") private int unreadCount;
    public int getUnreadCount() { return unreadCount; }
}
