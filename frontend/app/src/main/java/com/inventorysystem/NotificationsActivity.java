package com.inventorysystem;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.inventorysystem.ConnectivityandService.ApiErrorHandler;
import com.inventorysystem.ConnectivityandService.ApiService;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.MarkAllReadResponse;
import com.inventorysystem.Model.NotificationModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationsActivity extends SwipeNavigationActivity {
    private static final int PAGE_SIZE = 20;
    private NotificationAdapter adapter;
    private ProgressBar progress;
    private TextView state;
    private Button markAll;
    private ApiService api;
    private RecyclerView recycler;
    private final List<NotificationModel> loadedNotifications = new ArrayList<>();
    private final Set<Integer> loadedNotificationIds = new HashSet<>();
    private int nextNotificationPage = 1;
    private int notificationRequestVersion;
    private boolean loadingNotifications;
    private boolean hasMoreNotifications = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        api = RetrofitClient.getApiService();
        progress = findViewById(R.id.progressNotifications);
        state = findViewById(R.id.txtNotificationState);
        markAll = findViewById(R.id.btnMarkAllRead);
        recycler = findViewById(R.id.recyclerNotifications);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(this::openNotification);
        recycler.setAdapter(adapter);
        setupNotificationPagination();
        setupBottomNavigation();
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        state.setOnClickListener(v -> loadNotifications());
        markAll.setOnClickListener(v -> markAllRead());
        loadNotifications();
    }

    private void setupBottomNavigation() {
        View navDashboard = findViewById(R.id.navDashboard);
        View navItems = findViewById(R.id.navItems);
        View navReports = findViewById(R.id.navReports);
        View navNotifications = findViewById(R.id.navNotifications);
        View navDisposal = findViewById(R.id.navDisposal);

        NavigationUi.selectBottomItem(navNotifications, navDashboard, navItems,
                navReports, navNotifications, navDisposal);
        navDashboard.setOnClickListener(v -> openTab(DashboardActivity.class));
        navItems.setOnClickListener(v -> openTab(ItemsActivity.class));
        navReports.setOnClickListener(v -> openTab(ReportsActivity.class));
        navNotifications.setOnClickListener(v -> { });
        navDisposal.setOnClickListener(v -> openTab(DisposalActivity.class));
    }

    private void openTab(Class<?> destination) {
        Intent intent = new Intent(this, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void loadNotifications() {
        int requestVersion = ++notificationRequestVersion;
        nextNotificationPage = 1;
        loadingNotifications = false;
        hasMoreNotifications = true;
        loadedNotifications.clear();
        loadedNotificationIds.clear();
        adapter.submitList(new ArrayList<>());
        loadNotificationPage(requestVersion, true);
    }

    private void setupNotificationPagination() {
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loadingNotifications || !hasMoreNotifications) return;
                LinearLayoutManager layoutManager =
                        (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;
                if (layoutManager.findLastVisibleItemPosition()
                        >= adapter.getItemCount() - 5) {
                    loadNotificationPage(notificationRequestVersion, false);
                }
            }
        });
    }

    private void loadNotificationPage(int requestVersion, boolean firstPage) {
        if (loadingNotifications || !hasMoreNotifications
                || requestVersion != notificationRequestVersion) return;
        loadingNotifications = true;
        if (firstPage) showLoading(true);

        final int requestedPage = nextNotificationPage;
        api.getNotifications(false, requestedPage, PAGE_SIZE)
                .enqueue(new Callback<List<NotificationModel>>() {
            @Override
            public void onResponse(Call<List<NotificationModel>> call,
                                   Response<List<NotificationModel>> response) {
                if (requestVersion != notificationRequestVersion) return;
                loadingNotifications = false;
                if (firstPage) showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    List<NotificationModel> pageNotifications = response.body();
                    for (NotificationModel notification : pageNotifications) {
                        if (loadedNotificationIds.add(notification.getNotificationId())) {
                            loadedNotifications.add(notification);
                        }
                    }
                    hasMoreNotifications =
                            responseHasMore(response, pageNotifications.size());
                    nextNotificationPage = requestedPage + 1;
                    adapter.submitList(new ArrayList<>(loadedNotifications));
                    state.setText("No notifications");
                    state.setVisibility(loadedNotifications.isEmpty()
                            ? View.VISIBLE : View.GONE);
                    markAll.setEnabled(!loadedNotifications.isEmpty());
                } else if (firstPage) {
                    showError(ApiErrorHandler.message(response));
                } else {
                    Toast.makeText(NotificationsActivity.this,
                            ApiErrorHandler.message(response),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<List<NotificationModel>> call, Throwable t) {
                if (requestVersion != notificationRequestVersion) return;
                loadingNotifications = false;
                if (firstPage) {
                    showLoading(false);
                    showError("Cannot load notifications. Tap to retry.");
                } else {
                    Toast.makeText(NotificationsActivity.this,
                            "Cannot load more notifications",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private boolean responseHasMore(Response<?> response, int returnedCount) {
        String header = response.headers().get("X-Has-More");
        return header != null ? Boolean.parseBoolean(header) : returnedCount == PAGE_SIZE;
    }

    private void openNotification(NotificationModel notification) {
        if (notification.isRead()) {
            navigate(notification);
            return;
        }
        api.markNotificationRead(notification.getNotificationId())
                .enqueue(new Callback<NotificationModel>() {
                    @Override
                    public void onResponse(Call<NotificationModel> call,
                                           Response<NotificationModel> response) {
                        if (response.isSuccessful()) {
                            notification.setRead(true);
                            adapter.notifyDataSetChanged();
                            navigate(notification);
                        } else {
                            Toast.makeText(NotificationsActivity.this,
                                    ApiErrorHandler.message(response),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<NotificationModel> call, Throwable t) {
                        Toast.makeText(NotificationsActivity.this,
                                "Network error. Please retry.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void navigate(NotificationModel notification) {
        Intent intent = new Intent(this, DisposalActivity.class);
        String type = notification.getType();
        if ("disposal_requested".equals(type)) {
            intent.putExtra("status", "Pending Approval");
        } else if ("disposal_approved".equals(type)) {
            intent.putExtra("status", "For Disposal");
        } else if ("disposal_rejected".equals(type)) {
            intent.putExtra("status", "Rejected");
        } else {
            intent.putExtra("status", "Disposed");
        }
        startActivity(intent);
    }

    private void markAllRead() {
        markAll.setEnabled(false);
        api.markAllNotificationsRead().enqueue(new Callback<MarkAllReadResponse>() {
            @Override
            public void onResponse(Call<MarkAllReadResponse> call,
                                   Response<MarkAllReadResponse> response) {
                if (response.isSuccessful()) {
                    for (NotificationModel item : adapter.getEntries()) item.setRead(true);
                    adapter.notifyDataSetChanged();
                } else {
                    markAll.setEnabled(true);
                    Toast.makeText(NotificationsActivity.this,
                            ApiErrorHandler.message(response), Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onFailure(Call<MarkAllReadResponse> call, Throwable t) {
                markAll.setEnabled(true);
                Toast.makeText(NotificationsActivity.this,
                        "Network error. Please retry.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) state.setVisibility(View.GONE);
    }

    private void showError(String message) {
        state.setText(message);
        state.setVisibility(View.VISIBLE);
    }
}
