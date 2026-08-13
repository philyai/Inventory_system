package com.inventorysystem;

import com.google.gson.annotations.SerializedName;

public class DashboardModel {

    @SerializedName("total_items")
    private int totalItems;

    @SerializedName("total_value")
    private double totalValue;

    @SerializedName("items_in_stock")
    private int itemsInStock;

    @SerializedName("low_stock")
    private int lowStock;

    @SerializedName("for_disposal")
    private int forDisposal;

    @SerializedName("reserved")
    private int reserved;

    public int getTotalItems() {
        return totalItems;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public int getItemsInStock() {
        return itemsInStock;
    }

    public int getLowStock() {
        return lowStock;
    }

    public int getForDisposal() {
        return forDisposal;
    }

    public int getReserved() {
        return reserved;
    }
}