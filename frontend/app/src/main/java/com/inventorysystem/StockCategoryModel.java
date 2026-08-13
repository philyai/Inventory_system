package com.inventorysystem;
import com.google.gson.annotations.SerializedName;
public class StockCategoryModel {

    @SerializedName("category_id")
    private int categoryId;

    @SerializedName("item_count")
    private int itemCount;

    @SerializedName("total_quantity")
    private int totalQuantity;

    // Category object
    @SerializedName("Category")
    private Category category;

    public static class Category {
        @SerializedName("category_name")
        private String categoryName;

        public String getCategoryName() {
            return categoryName;
        }
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public int getItemCount() {
        return itemCount;
    }

    public Category getCategory() {
        return category;
    }
}
