package com.inventorysystem.offline;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "pending_items", indices = {@Index(value = {"clientRequestId"}, unique = true), @Index(value = {"ownerUserId", "syncStatus"})})
public class PendingItemEntity {
    @PrimaryKey @NonNull public String localId;
    @NonNull public String clientRequestId;
    public int ownerUserId;
    @NonNull public String itemName;
    public String brand;
    public String model;
    public String serialNumber;
    public Integer categoryId;
    public String categoryName;
    public int locationId;
    public int quantity;
    public int reorderLevel;
    @NonNull public String unitCost;
    public String remarks;
    public Integer issueId;
    public String issueCode;
    public Integer serverItemId;
    public String serverItemCode;
    public String localImagePath;
    public String imageMimeType;
    @NonNull public String syncStatus;
    public int retryCount;
    public String lastError;
    public long createdAt;
    public Long lastAttemptAt;
    public Long retryAfterAt;
}