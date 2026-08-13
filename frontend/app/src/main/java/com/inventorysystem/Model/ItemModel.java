package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class ItemModel {

    @SerializedName("item_id")
    private int itemId;

    @SerializedName("item_code")
    private String itemCode;

    @SerializedName("item_name")
    private String itemName;

    @SerializedName("brand")
    private String brand;

    @SerializedName("model")
    private String model;

    @SerializedName("serial_number")
    private String serialNumber;

    @SerializedName("category_id")
    private int categoryId;

    @SerializedName("quantity")
    private int quantity;

    @SerializedName("reorder_level")
    private int reorderLevel;

    @SerializedName("unit_cost")
    private double unitCost;

    @SerializedName("total_value")
    private double totalValue;

    @SerializedName("location")
    private String location;

    @SerializedName("location_id")
    private int locationId;

    @SerializedName("ItemLocation")
    private ItemLocation itemLocation;

    @SerializedName("date_added")
    private String dateAdded;

    @SerializedName("status")
    private String status;

    @SerializedName("image_url")
    private String image;

    @SerializedName("Category")
    private Category category;

    @SerializedName("active_disposal")
    private ActiveDisposal activeDisposal;

    @SerializedName("remark_issue")
    private ItemRemarkIssue remarkIssue;

    public int getItemId() { return itemId; }
    public String getItemCode() { return itemCode; }
    public String getItemName() { return itemName; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public String getSerialNumber() { return serialNumber; }
    public int getCategoryId() { return categoryId; }
    public int getQuantity() { return quantity; }
    public int getReorderLevel() { return reorderLevel; }
    public double getUnitCost() { return unitCost; }
    public double getTotalValue() { return totalValue; }
    public String getLocation() {
        return location != null ? location
                : (itemLocation != null ? itemLocation.locationName : null);
    }
    public int getLocationId() {
        return locationId != 0 ? locationId
                : (itemLocation != null ? itemLocation.locationId : 0);
    }
    public String getDateAdded() { return dateAdded; }
    public String getStatus() { return status; }
    public String getImage() { return image; }
    public Category getCategory() { return category; }
    public ActiveDisposal getActiveDisposal() { return activeDisposal; }
    public ItemRemarkIssue getRemarkIssue() { return remarkIssue; }

    public static class ActiveDisposal {
        @SerializedName("disposal_id")
        private int disposalId;

        @SerializedName("disposal_status")
        private String disposalStatus;

        @SerializedName("reason")
        private String reason;

        public int getDisposalId() { return disposalId; }
        public String getDisposalStatus() { return disposalStatus; }
        public String getReason() { return reason; }
    }

    public static class Category {
        @SerializedName("category_name")
        private String categoryName;

        public String getCategoryName() { return categoryName; }
    }

    public static class ItemLocation {
        @SerializedName("location_id")
        private int locationId;

        @SerializedName("location_name")
        private String locationName;
    }
}
