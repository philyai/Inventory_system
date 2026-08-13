package com.inventorysystem.offline;

import android.content.Context;

import com.inventorysystem.BuildConfig;

import okhttp3.HttpUrl;

public final class BackendPreferences {
    private static final String PREFS = "BackendPreferences";
    private static final String KEY_URL = "healthy_base_url";
    private BackendPreferences() {}
    public static void save(Context context, String url) {
        if (isAllowed(url))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_URL, url).apply();
    }
    public static String get(Context context) {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_URL, null);
        return isAllowed(value) ? value : null;
    }
    private static boolean isAllowed(String url) {
        if (url == null) return false;
        try {
            HttpUrl parsed = HttpUrl.get(url);
            return BuildConfig.DEBUG || "https".equalsIgnoreCase(parsed.scheme());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
