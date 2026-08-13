package com.inventorysystem.Model;

public class CreateAccountRequest {
    private final String username;
    private final String email;
    private final String password;
    private final String role;

    public CreateAccountRequest(String username, String email, String password, String role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
    }
}
