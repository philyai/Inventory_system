package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class ItemResponse {

    @SerializedName("message")
    private String message;

    @SerializedName("item_id")
    private int itemId;

    @SerializedName("item_code")
    private String itemCode;

    @SerializedName("remark_issue")
    private ItemRemarkIssue remarkIssue;

    public String getMessage() {
        return message;
    }

    public int getItemId() {
        return itemId;
    }

    public String getItemCode() {
        return itemCode;
    }

    public ItemRemarkIssue getRemarkIssue() {
        return remarkIssue;
    }
}