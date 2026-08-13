package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * A single location's item summary, returned by
 * GET reports/location-breakdown.
 *
 * "details" is the list of lines shown when the card is expanded
 * (e.g. one line per sub-category: "Monitors - 42 units").
 */
public class LocationBreakdownModel {

    @SerializedName("location_id")
    private int locationId;

    @SerializedName("item_count")
    private int itemCount;

    @SerializedName("total_quantity")
    private int totalQuantity;

    @SerializedName("total_value")
    private double totalValue;

    @SerializedName("ItemLocation")
    private ItemLocation itemLocation;

    private transient List<ItemModel> items = new ArrayList<>();

    public LocationBreakdownModel() { }

    public int getLocationId() {
        return locationId;
    }

    public void setLocationId(int locationId) {
        this.locationId = locationId;
    }

    public String getLocationName() {
        return itemLocation != null && itemLocation.locationName != null
                ? itemLocation.locationName
                : "Unknown Location";
    }

    public void setLocationName(String locationName) {
        if (itemLocation == null) itemLocation = new ItemLocation();
        itemLocation.locationName = locationName;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public String getDescription() {
        return itemLocation != null ? itemLocation.description : null;
    }

    public List<ItemModel> getItems() {
        return items;
    }

    public void setItems(List<ItemModel> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    private static class ItemLocation {
        @SerializedName("location_name")
        private String locationName;

        private String description;
    }
}
