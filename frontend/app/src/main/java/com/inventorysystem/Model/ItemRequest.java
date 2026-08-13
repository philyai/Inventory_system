package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class ItemRequest {

    @SerializedName("category_id")
    private int categoryId;

    @SerializedName("location_id")
    private int locationId;

    @SerializedName("item_name")
    private String itemName;

    @SerializedName("brand")
    private String brand;

    @SerializedName("model")
    private String model;

    @SerializedName("serial_number")
    private String serialNumber;

    @SerializedName("quantity")
    private int quantity;

    @SerializedName("unit_cost")
    private double unitCost;

    public ItemRequest(int categoryId, int locationId, String itemName, String brand, String model,
                       String serialNumber, int quantity, double unitCost) {
        this.categoryId = categoryId;
        this.locationId = locationId;
        this.itemName = itemName;
        this.brand = brand;
        this.model = model;
        this.serialNumber = serialNumber;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }
}