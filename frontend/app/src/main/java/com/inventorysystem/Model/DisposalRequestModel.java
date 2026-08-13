package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

/**
 * A single disposal request, used both by the read-only "Disposal" tab in
 * ReportsActivity and by the actionable list in DisposalApprovalActivity.
 *
 * "status" is expected to be one of: "Pending", "Approved", "Rejected".
 */
public class DisposalRequestModel {

    @SerializedName(value = "id", alternate = {"disposal_id", "disposal_request_id"})
    private int id;

    @SerializedName("item_code")
    private String itemCode;     // e.g. "PRN-0887"

    @SerializedName("item_name")
    private String itemName;     // e.g. "HP LaserJet 1102 Printer"

    @SerializedName("image_url")
    private String imageUrl;

    @SerializedName("Item")
    private Item item;

    @SerializedName(value = "disposal_quantity", alternate = {"quantity"})
    private int disposalQuantity;
    @SerializedName(value = "status", alternate = {"disposal_status", "approval_status"})
    private String status;       // "Pending" / "Approved" / "Rejected" / "Disposed"

    @SerializedName(value = "request_date", alternate = {"date_requested", "created_at"})
    private String dateRequested; // ISO-8601 or already-formatted display date
    @SerializedName("approved_date")
    private String approvedDate;
    @SerializedName("item_id")
    private int itemId;
    @SerializedName("requested_by")
    private int requestedBy;
    private String reason;       // note / description shown under the row

    public DisposalRequestModel() { }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getItemCode() {
        return itemCode != null ? itemCode : (item != null ? item.itemCode : null);
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemName() {
        return itemName != null ? itemName : (item != null ? item.itemName : null);
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public int getQuantity() {
        return disposalQuantity;
    }

    public void setQuantity(int quantity) {
        this.disposalQuantity = quantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDateRequested() {
        return dateRequested;
    }

    public String getApprovedDate() {
        return approvedDate;
    }

    public void setDateRequested(String dateRequested) {
        this.dateRequested = dateRequested;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getImageUrl() {
        return imageUrl != null ? imageUrl : (item != null ? item.imageUrl : null);
    }

    public int getItemId() { return itemId; }
    public int getRequestedBy() { return requestedBy; }

    private static class Item {
        @SerializedName("item_code")
        private String itemCode;

        @SerializedName("item_name")
        private String itemName;

        @SerializedName("image_url")
        private String imageUrl;

        @SerializedName("quantity")
        private int quantity;
    }
}
