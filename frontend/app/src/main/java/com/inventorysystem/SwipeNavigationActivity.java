package com.inventorysystem;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.UnreadCountResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public abstract class SwipeNavigationActivity extends AppCompatActivity {
    private TextView notificationBadge;
    private Call<UnreadCountResponse> notificationBadgeCall;
    private final Handler notificationBadgeHandler = new Handler(Looper.getMainLooper());
    private final Runnable notificationBadgeRefresh = new Runnable() {
        @Override
        public void run() {
            refreshNotificationBadge();
            notificationBadgeHandler.postDelayed(this, 30_000L);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        notificationBadge = findViewById(R.id.txtNotificationBadge);
        if (notificationBadge != null) {
            notificationBadgeHandler.removeCallbacks(notificationBadgeRefresh);
            notificationBadgeHandler.post(notificationBadgeRefresh);
        }
    }

    @Override
    protected void onStop() {
        notificationBadgeHandler.removeCallbacks(notificationBadgeRefresh);
        if (notificationBadgeCall != null) {
            notificationBadgeCall.cancel();
            notificationBadgeCall = null;
        }
        super.onStop();
    }

    private void refreshNotificationBadge() {
        if (notificationBadge == null || !new SessionManager(this).isLoggedIn()) return;
        notificationBadgeCall = RetrofitClient.getApiService().getUnreadCount();
        notificationBadgeCall.enqueue(new Callback<UnreadCountResponse>() {
                    @Override
                    public void onResponse(Call<UnreadCountResponse> call,
                                           Response<UnreadCountResponse> response) {
                        if (notificationBadgeCall == call) notificationBadgeCall = null;
                        if (isFinishing() || isDestroyed()) return;
                        if (!response.isSuccessful() || response.body() == null) return;
                        int count = response.body().getUnreadCount();
                        if (count > 0) {
                            notificationBadge.setText(count > 99
                                    ? "99+" : String.valueOf(count));
                            notificationBadge.setTextColor(getColor(R.color.red));
                            notificationBadge.setVisibility(View.VISIBLE);
                        } else {
                            notificationBadge.setText("");
                            notificationBadge.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onFailure(Call<UnreadCountResponse> call, Throwable error) {
                        if (notificationBadgeCall == call) notificationBadgeCall = null;
                        // Keep the last known count; the scheduled refresh will retry.
                    }
                });
    }

}
