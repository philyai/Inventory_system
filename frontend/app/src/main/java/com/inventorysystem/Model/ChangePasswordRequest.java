package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class ChangePasswordRequest {
    @SerializedName("current_password") private final String currentPassword;
    @SerializedName("new_password") private final String newPassword;
    @SerializedName("confirm_password") private final String confirmPassword;

    public ChangePasswordRequest(String currentPassword, String newPassword,
                                 String confirmPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }
}
