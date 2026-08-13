package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

/**
 * A single category's stock breakdown row, returned by
 * GET reports/category-breakdown.
 *
 * "percentage" (0-100) drives the fill width of the progress bar in
 * item_category_breakdown.xml.
 */
public class CategoryBreakdownModel {

    @SerializedName("category_id")
    private int categoryId;

    @SerializedName("item_count")
    private int itemCount;

    @SerializedName("total_quantity")
    private int totalQuantity;

    @SerializedName("total_value")
    private double totalValue;

    @SerializedName("Category")
    private Category category;

    private int percentage;

    public CategoryBreakdownModel() { }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return category != null && category.categoryName != null
                ? category.categoryName
                : "Unknown Category";
    }

    public void setCategoryName(String categoryName) {
        if (category == null) category = new Category();
        category.categoryName = categoryName;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public int getPercentage() {
        return percentage;
    }

    public void setPercentage(int percentage) {
        this.percentage = percentage;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public double getTotalValue() {
        return totalValue;
    }

    private static class Category {
        @SerializedName("category_name")
        private String categoryName;
    }
}
