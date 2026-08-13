package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class CategoryModel {
    @SerializedName("category_id") private int categoryId;
    @SerializedName("category_name") private String categoryName;

    public CategoryModel() {}
    public CategoryModel(int categoryId, String categoryName) { this.categoryId = categoryId; this.categoryName = categoryName; }
    public int getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
}