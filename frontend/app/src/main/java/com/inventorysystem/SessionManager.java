package com.inventorysystem;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    public static final String PREF_NAME = "InventorySession";
    public static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USER_ID = "users_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE = "role";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_REMEMBER_ME = "remember_me";

    private final SharedPreferences prefs;
    private final SecureSessionStorage secureStorage;
    private static String sessionToken;
    private static String sessionUsername;
    private static String sessionRole;
    private static String sessionEmail;
    private static int sessionUserId;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        secureStorage = new SecureSessionStorage(prefs);

        // Restore a session after process/app restart only when the user
        // explicitly selected Remember Me.
        if (sessionToken == null && prefs.getBoolean(KEY_REMEMBER_ME, false)) {
            sessionToken = secureStorage.getString(KEY_TOKEN);
            sessionUserId = parseUserId(secureStorage.getString(KEY_USER_ID));
            sessionUsername = secureStorage.getString(KEY_USERNAME);
            sessionRole = secureStorage.getString(KEY_ROLE);
            sessionEmail = secureStorage.getString(KEY_EMAIL);
        }
    }

    public void saveSession(String token, int userId, String username, String role,
                            boolean rememberMe) {
        saveSession(token, userId, username, null, role, rememberMe);
    }

    public void saveSession(String token, int userId, String username, String email,
                            String role, boolean rememberMe) {
        sessionToken = token;
        sessionUserId = userId;
        sessionUsername = username;
        sessionRole = role;
        sessionEmail = email;

        prefs.edit().clear().putBoolean(KEY_REMEMBER_ME, rememberMe).commit();
        if (rememberMe) {
            secureStorage.putString(KEY_TOKEN, token);
            secureStorage.putString(KEY_USER_ID, String.valueOf(userId));
            secureStorage.putString(KEY_USERNAME, username);
            secureStorage.putString(KEY_ROLE, role);
            secureStorage.putString(KEY_EMAIL, email);
        }
    }

    public int getUserId() {
        return sessionUserId;
    }

    public String getToken() {
        return sessionToken;
    }

    public String getUsername() {
        return sessionUsername;
    }

    public String getRole() {
        return sessionRole;
    }

    public String getEmail() {
        return sessionEmail;
    }

    public boolean isRememberMeEnabled() {
        return prefs.getBoolean(KEY_REMEMBER_ME, false);
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    public void clearSession() {
        sessionToken = null;
        sessionUserId = 0;
        sessionUsername = null;
        sessionRole = null;
        sessionEmail = null;
        prefs.edit().clear().apply();
    }

    private int parseUserId(String value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
