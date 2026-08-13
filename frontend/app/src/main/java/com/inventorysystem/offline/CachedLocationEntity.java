package com.inventorysystem.offline;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cached_locations")
public class CachedLocationEntity {
    @PrimaryKey public int locationId;
    public String locationName;

    public CachedLocationEntity(int locationId, String locationName) {
        this.locationId = locationId;
        this.locationName = locationName;
    }
}