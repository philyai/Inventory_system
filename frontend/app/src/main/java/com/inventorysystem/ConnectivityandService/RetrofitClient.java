package com.inventorysystem.ConnectivityandService;

import android.content.Context;

import com.inventorysystem.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.HttpUrl;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static String baseUrl;
    private static Retrofit retrofit;
    private static Context applicationContext;

    private RetrofitClient() {
        // Prevent instantiation
    }

    public static synchronized Retrofit getClient() {
        if (baseUrl == null) {
            throw new IllegalStateException("No backend server has been selected");
        }
        if (applicationContext == null) {
            throw new IllegalStateException("Retrofit has not been initialized with a context");
        }

        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(new AuthInterceptor(applicationContext))
                    .build();
            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofit;
    }

    public static synchronized void configureBaseUrl(Context context, String newBaseUrl) {
        if (newBaseUrl == null || newBaseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL must not be empty");
        }

        String normalized = newBaseUrl.trim();
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        HttpUrl parsed;
        try {
            parsed = HttpUrl.get(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Base URL is invalid", exception);
        }
        if (!BuildConfig.DEBUG && !"https".equalsIgnoreCase(parsed.scheme())) {
            throw new IllegalArgumentException("Release builds require an HTTPS backend URL");
        }

        applicationContext = context.getApplicationContext();
        baseUrl = normalized;
        retrofit = null;
    }

    public static synchronized String getBaseUrl() {
        return baseUrl;
    }

    public static ApiService getApiService() {
        return getClient().create(ApiService.class);
    }

    public static String resolveImageUrl(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return null;
        }

        if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            return imagePath;
        }

        String relativePath = imagePath.startsWith("/")
                ? imagePath.substring(1)
                : imagePath;
        return getBaseUrl() + relativePath;
    }
}
