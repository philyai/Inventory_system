package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

/**
 * A single stock movement record (In / Out / Adjustment) returned by
 * GET reports/stock-movement.
 *
 * "type" is expected to be one of: "in", "out", "adjust" (lowercase),
 * matching the android:tag values used to filter rows in ReportsActivity.
 *
 * "timestamp" should be an ISO-8601 string (e.g. "2026-07-23T10:15:00Z")
 * so the client can group rows into "Today" / "Yesterday" / etc.
 */
public class StockMovementModel {

    @SerializedName(value = "id", alternate = {"movement_id"})
    private int id;

    @SerializedName("item_name")
    private String itemName;

    @SerializedName(value = "movement_type", alternate = {"type"})
    private String type;        // "in", "out", "adjust"

    @SerializedName(value = "quantity_change", alternate = {"quantity"})
    private int quantity;

    @SerializedName(value = "timestamp", alternate = {"created_at", "movement_date"})
    private String timestamp;   // ISO-8601

    @SerializedName(value = "source_destination", alternate = {"note", "remarks", "reason", "reference"})
    private String note;        // e.g. "Received from Supplier A" / "Issued to IT Dept"

    @SerializedName("Item")
    private Item item;

    public StockMovementModel() { }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getItemName() {
        return itemName != null ? itemName : (item != null ? item.itemName : null);
    }

    public String getItemCode() {
        return item != null ? item.itemCode : null;
    }

    public String getImageUrl() {
        return item != null ? item.imageUrl : null;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    private static class Item {
        @SerializedName("item_name")
        private String itemName;

        @SerializedName("item_code")
        private String itemCode;

        @SerializedName("image_url")
        private String imageUrl;
    }
}
