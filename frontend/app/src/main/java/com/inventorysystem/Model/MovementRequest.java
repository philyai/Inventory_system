package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class MovementRequest {
    @SerializedName("item_id") private final int itemId;
    @SerializedName("movement_type") private final String movementType;
    @SerializedName("quantity_change") private final int quantityChange;
    @SerializedName("source_destination") private final String sourceDestination;
    @SerializedName("remarks") private final String remarks;

    public MovementRequest(int itemId, String movementType, int quantityChange,
                           String sourceDestination, String remarks) {
        if (sourceDestination == null || sourceDestination.trim().isEmpty()) {
            throw new IllegalArgumentException("Source/destination is required");
        }
        if ("In".equals(movementType) && quantityChange <= 0) {
            throw new IllegalArgumentException("In movements require a positive quantity");
        }
        if ("Out".equals(movementType) && quantityChange >= 0) {
            throw new IllegalArgumentException("Out movements require a negative quantity");
        }
        if ("Adjustment".equals(movementType) && quantityChange == 0) {
            throw new IllegalArgumentException("Adjustment cannot be zero");
        }
        this.itemId = itemId;
        this.movementType = movementType;
        this.quantityChange = quantityChange;
        this.sourceDestination = sourceDestination.trim();
        this.remarks = remarks;
    }
}
