package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class OfflineItemRequest {
    @SerializedName("item_name") public String itemName;
    @SerializedName("brand") public String brand;
    @SerializedName("model") public String model;
    @SerializedName("serial_number") public String serialNumber;
    @SerializedName("category_id") public Integer categoryId;
    @SerializedName("category_name") public String categoryName;
    @SerializedName("location_id") public int locationId;
    @SerializedName("quantity") public int quantity;
    @SerializedName("reorder_level") public int reorderLevel;
    @SerializedName("unit_cost") public String unitCost;
    @SerializedName("remarks") public String remarks;
}