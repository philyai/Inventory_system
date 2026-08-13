package com.inventorysystem;

import android.app.Activity;
import android.content.Intent;

import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.UserProfile;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class ProfileStore {
    public interface Listener {
        void onProfile(UserProfile profile);
    }

    private static UserProfile profile;
    private static boolean loading;
    private static final List<Listener> listeners = new ArrayList<>();

    private ProfileStore() { }

    public static UserProfile getProfile() {
        return profile;
    }

    public static void loadOnce(Activity activity, Listener listener) {
        if (profile != null) {
            listener.onProfile(profile);
            return;
        }
        listeners.add(listener);
        if (loading) return;
        loading = true;
        SessionManager session = new SessionManager(activity);
        RetrofitClient.getApiService().getProfile("Bearer " + session.getToken())
                .enqueue(new Callback<UserProfile>() {
                    @Override
                    public void onResponse(Call<UserProfile> call, Response<UserProfile> response) {
                        loading = false;
                        if (response.code() == 401) {
                            clearAndSignIn(activity);
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null) {
                            profile = response.body();
                            for (Listener pending : new ArrayList<>(listeners)) {
                                pending.onProfile(profile);
                            }
                        }
                        listeners.clear();
                    }

                    @Override
                    public void onFailure(Call<UserProfile> call, Throwable t) {
                        loading = false;
                        listeners.clear();
                    }
                });
    }

    public static void clear() {
        profile = null;
        loading = false;
        listeners.clear();
    }

    public static void clearAndSignIn(Activity activity) {
        clear();
        new SessionManager(activity).clearSession();
        Intent intent = new Intent(activity, LoginPage.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
