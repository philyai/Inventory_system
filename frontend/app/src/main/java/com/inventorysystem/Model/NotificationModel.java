package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class NotificationModel {
    @SerializedName("notification_id") private int notificationId;
    @SerializedName("user_id") private int userId;
    @SerializedName("message") private String message;
    @SerializedName("type") private String type;
    @SerializedName("is_read") private boolean read;
    @SerializedName("created_at") private String createdAt;
    @SerializedName("item_id") private int itemId;
    @SerializedName("item_name") private String itemName;
    @SerializedName("image_url") private String imageUrl;

    public int getNotificationId() { return notificationId; }
    public int getUserId() { return userId; }
    public String getMessage() { return message; }
    public String getType() { return type; }
    public boolean isRead() { return read; }
    public String getCreatedAt() { return createdAt; }
    public int getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public String getImageUrl() { return imageUrl; }
    public void setRead(boolean read) { this.read = read; }
}
