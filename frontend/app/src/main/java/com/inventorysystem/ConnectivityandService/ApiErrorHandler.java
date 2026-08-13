package com.inventorysystem.ConnectivityandService;

import org.json.JSONObject;

import retrofit2.Response;

public final class ApiErrorHandler {
    private ApiErrorHandler() { }

    public static String message(Response<?> response) {
        String backendMessage = null;
        try {
            if (response.errorBody() != null) {
                backendMessage = new JSONObject(response.errorBody().string())
                        .optString("message", null);
            }
        } catch (Exception ignored) { }

        if (backendMessage != null && !backendMessage.trim().isEmpty()) {
            return backendMessage;
        }
        switch (response.code()) {
            case 400: return "Invalid request.";
            case 401: return "Your session has expired. Please sign in again.";
            case 403: return "You do not have permission.";
            case 409: return "This request conflicts with an existing operation.";
            case 500: return "Inventory Server encountered an error.";
            case 503: return "Database temporarily unavailable.";
            default: return "Request failed (" + response.code() + ").";
        }
    }
}
