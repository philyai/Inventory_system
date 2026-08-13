package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class LocationModel {
    @SerializedName("location_id") private int locationId;
    @SerializedName("location_name") private String locationName;

    public LocationModel() {}
    public LocationModel(int locationId, String locationName) { this.locationId = locationId; this.locationName = locationName; }
    public int getLocationId() { return locationId; }
    public String getLocationName() { return locationName; }
}