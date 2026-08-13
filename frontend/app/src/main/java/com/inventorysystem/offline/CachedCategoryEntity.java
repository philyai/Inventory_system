package com.inventorysystem.offline;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cached_categories")
public class CachedCategoryEntity {
    @PrimaryKey public int categoryId;
    public String categoryName;

    public CachedCategoryEntity(int categoryId, String categoryName) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }
}