package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class UserProfile {
    @SerializedName(value = "users_id", alternate = {"user_id", "id"})
    private int userId;
    @SerializedName("username")
    private String username;
    @SerializedName("role")
    private String role;
    @SerializedName("email")
    private String email;

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getEmail() { return email; }
}
