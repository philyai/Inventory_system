package com.inventorysystem.Model;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {

    @SerializedName("token")
    private String token;

    @SerializedName("login_session_id")
    private long loginSessionId;

    @SerializedName("user")
    private UserData user;

    public String getToken() {
        return token;
    }

    public UserData getUser() {
        return user;
    }

    public long getLoginSessionId() {
        return loginSessionId;
    }

    public static class UserData {
        @SerializedName("users_id")
        private int usersId;

        @SerializedName("username")
        private String username;

        @SerializedName("role")
        private String role;

        @SerializedName("email")
        private String email;

        public int getUsersId() {
            return usersId;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }

        public String getEmail() {
            return email;
        }
    }
}
