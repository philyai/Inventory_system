package com.inventorysystem.ConnectivityandService;

import android.content.Context;

import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.inventorysystem.SessionManager;

public final class AuthenticatedImageUrl {
    private AuthenticatedImageUrl() {
    }

    public static Object from(Context context, String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return imageUrl;
        }

        String token = new SessionManager(context).getToken();
        if (token == null || token.trim().isEmpty()) {
            return imageUrl;
        }

        LazyHeaders headers = new LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer " + token)
                .build();
        return new GlideUrl(imageUrl, headers);
    }
}
