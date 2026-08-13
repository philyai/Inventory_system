package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

/**
 * A single low-stock alert row returned by GET reports/low-stock.
 *
 * "status" is expected to be one of: "Critical", "Low" (used to color the
 * badge in item_low_stock.xml).
 */
public class LowStockModel {

    @SerializedName("item_id")
    private int itemId;

    @SerializedName("item_name")
    private String itemName;

    @SerializedName(value = "item_code", alternate = {"sku"})
    private String sku;

    @SerializedName("quantity")
    private int currentQty;

    @SerializedName("reorder_level")
    private int minQty;

    @SerializedName("image_url")
    private String imageUrl;

    private String status;      // "Critical" or "Low"

    @SerializedName("Category")
    private Category category;

    @SerializedName("ItemLocation")
    private ItemLocation itemLocation;

    public LowStockModel() { }

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getSku() {
        return sku != null ? sku : getCategoryName();
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getCurrentQty() {
        return currentQty;
    }

    public void setCurrentQty(int currentQty) {
        this.currentQty = currentQty;
    }

    public int getMinQty() {
        return minQty;
    }

    public void setMinQty(int minQty) {
        this.minQty = minQty;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getStatus() {
        return status != null ? status : "Low";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocation() {
        return itemLocation != null ? itemLocation.locationName : null;
    }

    public void setLocation(String location) {
        if (itemLocation == null) itemLocation = new ItemLocation();
        itemLocation.locationName = location;
    }

    public String getCategoryName() {
        return category != null ? category.categoryName : null;
    }

    private static class Category {
        @SerializedName("category_name")
        private String categoryName;
    }

    private static class ItemLocation {
        @SerializedName("location_name")
        private String locationName;
    }
}
