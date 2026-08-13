package com.inventorysystem.ConnectivityandService;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.inventorysystem.LoginPage;
import com.inventorysystem.SessionManager;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class AuthInterceptor implements Interceptor {
    private final Context context;
    private static final AtomicBoolean loginRedirectStarted = new AtomicBoolean(false);

    public AuthInterceptor(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        String path = original.url().encodedPath();
        boolean publicEndpoint = "/health".equals(path) || "/auth/signin".equals(path);

        Request.Builder request = original.newBuilder()
                .header("Accept", "application/json");
        String token = new SessionManager(context).getToken();
        if (!publicEndpoint && token != null && !token.isEmpty()) {
            request.header("Authorization", "Bearer " + token);
        }

        Response response = chain.proceed(request.build());
        boolean passwordEndpoint = "/profile/change-password".equals(path);
        if (response.code() == 401 && !publicEndpoint && !passwordEndpoint
                && loginRedirectStarted.compareAndSet(false, true)) {
            new SessionManager(context).clearSession();
            new Handler(Looper.getMainLooper()).post(() -> {
                Intent login = new Intent(context, LoginPage.class)
                        .putExtra("session_expired", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(login);
            });
        } else if (response.isSuccessful()) {
            loginRedirectStarted.set(false);
        }
        return response;
    }
}
