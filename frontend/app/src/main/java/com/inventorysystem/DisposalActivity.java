package com.inventorysystem;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.ConnectivityandService.ApiErrorHandler;
import com.inventorysystem.Model.DisposalRequestModel;
import com.inventorysystem.Model.GenericResponse;

import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DisposalActivity extends SwipeNavigationActivity
        implements DisposalRequestAdapter.OnActionListener {
    private static final int PAGE_SIZE = 20;
    private DisposalRequestAdapter adapter;
    private SessionManager sessionManager;
    private boolean processing;
    private String currentStatusFilter = "Pending Approval";
    private ProgressBar progress;
    private TextView state;
    private RecyclerView recycler;
    private final List<DisposalRequestModel> loadedDisposals = new ArrayList<>();
    private final Set<Integer> loadedDisposalIds = new HashSet<>();
    private int nextDisposalPage = 1;
    private int disposalRequestVersion;
    private boolean loadingDisposals;
    private boolean hasMoreDisposals = true;
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disposal_approval);
        sessionManager = new SessionManager(this);
        String requestedStatus = getIntent().getStringExtra("status");
        if (requestedStatus != null && !requestedStatus.trim().isEmpty()) {
            currentStatusFilter = requestedStatus;
        }
        String role = sessionManager.getRole();
        String username = sessionManager.getUsername();
        TextView greetingName = findViewById(R.id.tvUserRole);
        TextView greetingRole = findViewById(R.id.tvGreetingRole);
        if (username != null && !username.trim().isEmpty()) {
            greetingName.setText(username.trim());
        } else if (role != null && !role.trim().isEmpty()) {
            greetingName.setText(role.trim());
        }
        if (role == null || role.trim().isEmpty()) {
            greetingRole.setVisibility(View.GONE);
        } else {
            greetingRole.setText(role.trim());
        }
        boolean canReview = "Purchasing".equals(role) || "Admin IT".equals(role);
        boolean canFinalize = "IT".equals(role) || "Admin IT".equals(role);

        adapter = new DisposalRequestAdapter(true, canReview, canFinalize, this);
        recycler = findViewById(R.id.recyclerDisposalRequests);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        setupDisposalPagination();
        progress = findViewById(R.id.progressDisposals);
        state = findViewById(R.id.txtDisposalState);
        state.setOnClickListener(v -> loadDisposals());

        NavigationUi.attachDrawer(this, findViewById(R.id.bttnBack));
        NavigationUi.selectBottomItem(findViewById(R.id.navDisposal),
                findViewById(R.id.navDashboard), findViewById(R.id.navItems),
                findViewById(R.id.navReports), findViewById(R.id.navNotifications),
                findViewById(R.id.navDisposal));
        View tabPending = findViewById(R.id.tabPendingApproval);
        View tabForDisposal = findViewById(R.id.tabApproved);
        View tabRejected = findViewById(R.id.tabRejected);
        View tabApprovedHistory = findViewById(R.id.tabApprovedHistory);
        tabPending.setOnClickListener(v -> {
            NavigationUi.selectChoice(tabPending, tabPending, tabForDisposal,
                    tabRejected, tabApprovedHistory);
            currentStatusFilter = "Pending Approval";
            loadDisposals();
        });
        tabForDisposal.setOnClickListener(v -> {
            NavigationUi.selectChoice(tabForDisposal, tabPending, tabForDisposal,
                    tabRejected, tabApprovedHistory);
            currentStatusFilter = "For Disposal";
            loadDisposals();
        });
        tabRejected.setOnClickListener(v -> {
            NavigationUi.selectChoice(tabRejected, tabPending, tabForDisposal,
                    tabRejected, tabApprovedHistory);
            currentStatusFilter = "Rejected";
            loadDisposals();
        });
        tabApprovedHistory.setOnClickListener(v -> {
            NavigationUi.selectChoice(tabApprovedHistory, tabPending, tabForDisposal,
                    tabRejected, tabApprovedHistory);
            currentStatusFilter = "Disposed";
            loadDisposals();
        });
        View initiallySelected = "For Disposal".equals(currentStatusFilter) ? tabForDisposal
                : "Rejected".equals(currentStatusFilter) ? tabRejected
                : "Disposed".equals(currentStatusFilter) ? tabApprovedHistory : tabPending;
        NavigationUi.selectChoice(initiallySelected, tabPending, tabForDisposal,
                tabRejected, tabApprovedHistory);
        findViewById(R.id.navNotificationBell).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
        findViewById(R.id.navNotifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
        findViewById(R.id.navDisposal).setOnClickListener(v -> loadDisposals());
        findViewById(R.id.navItems).setOnClickListener(v -> {
            startActivity(new Intent(this, ItemsActivity.class));
            finish();
        });
        findViewById(R.id.navDashboard).setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        });
        findViewById(R.id.navReports).setOnClickListener(v -> {
            startActivity(new Intent(this, ReportsActivity.class));
            finish();
        });
        loadDisposals();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
        } else {
            loadDisposals();
        }
    }

    private String authorization() {
        return "Bearer " + sessionManager.getToken();
    }

    private void loadDisposals() {
        loadDisposals(currentStatusFilter);
    }

    private void loadDisposals(String status) {
        int requestVersion = ++disposalRequestVersion;
        nextDisposalPage = 1;
        loadingDisposals = false;
        hasMoreDisposals = true;
        loadedDisposals.clear();
        loadedDisposalIds.clear();
        adapter.submitList(new ArrayList<>());
        loadDisposalPage(status, requestVersion, true);
    }

    private void setupDisposalPagination() {
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loadingDisposals || !hasMoreDisposals) return;
                LinearLayoutManager layoutManager =
                        (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;
                if (layoutManager.findLastVisibleItemPosition()
                        >= adapter.getItemCount() - 5) {
                    loadDisposalPage(
                            currentStatusFilter, disposalRequestVersion, false);
                }
            }
        });
    }

    private void loadDisposalPage(String status, int requestVersion, boolean firstPage) {
        if (loadingDisposals || !hasMoreDisposals
                || requestVersion != disposalRequestVersion) return;
        loadingDisposals = true;
        if (firstPage) {
            progress.setVisibility(View.VISIBLE);
            state.setVisibility(View.GONE);
        }

        final int requestedPage = nextDisposalPage;
        RetrofitClient.getApiService().getDisposals(
                authorization(), status, requestedPage, PAGE_SIZE)
                .enqueue(new Callback<List<DisposalRequestModel>>() {
                    @Override
                    public void onResponse(Call<List<DisposalRequestModel>> call,
                                           Response<List<DisposalRequestModel>> response) {
                        if (requestVersion != disposalRequestVersion) return;
                        loadingDisposals = false;
                        if (firstPage) progress.setVisibility(View.GONE);
                        if (response.isSuccessful()) {
                            List<DisposalRequestModel> returned = response.body() != null
                                    ? response.body() : Collections.emptyList();
                            for (DisposalRequestModel request : returned) {
                                if (status.equals(request.getStatus())
                                        && loadedDisposalIds.add(request.getId())) {
                                    loadedDisposals.add(request);
                                }
                            }
                            hasMoreDisposals = responseHasMore(response, returned.size());
                            nextDisposalPage = requestedPage + 1;
                            adapter.submitList(new ArrayList<>(loadedDisposals));
                            state.setText("No disposal requests");
                            state.setVisibility(loadedDisposals.isEmpty()
                                    ? View.VISIBLE : View.GONE);
                        } else if (firstPage) {
                            state.setText(ApiErrorHandler.message(response) + "\nTap to retry");
                            state.setVisibility(View.VISIBLE);
                        } else {
                            Toast.makeText(DisposalActivity.this,
                                    ApiErrorHandler.message(response),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<DisposalRequestModel>> call, Throwable t) {
                        if (requestVersion != disposalRequestVersion) return;
                        loadingDisposals = false;
                        if (firstPage) {
                            progress.setVisibility(View.GONE);
                            state.setText(
                                    "Cannot connect to Inventory Server\nTap to retry");
                            state.setVisibility(View.VISIBLE);
                        } else {
                            Toast.makeText(DisposalActivity.this,
                                    "Cannot load more disposal requests",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private boolean responseHasMore(Response<?> response, int returnedCount) {
        String header = response.headers().get("X-Has-More");
        return header != null ? Boolean.parseBoolean(header) : returnedCount == PAGE_SIZE;
    }

    @Override
    public void onApprove(DisposalRequestModel request) {
        confirmStatusChange(request, "For Disposal",
                "Approve this disposal request?");
    }

    @Override
    public void onReject(DisposalRequestModel request) {
        confirmStatusChange(request, "Rejected",
                "Reject this disposal request?");
    }

    @Override
    public void onFinalize(DisposalRequestModel request) {
        new AlertDialog.Builder(this)
                .setTitle("Finalize Disposal")
                .setMessage("This will reduce the item's quantity by "
                        + request.getQuantity() + ". Continue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Finalize", (dialog, which) -> finalizeDisposal(request))
                .show();
    }

    private void confirmStatusChange(DisposalRequestModel request, String status,
                                     String message) {
        if (!"Pending Approval".equals(request.getStatus())) return;
        new AlertDialog.Builder(this)
                .setTitle(status)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", (dialog, which) ->
                        updateStatus(request.getId(), status))
                .show();
    }

    private void updateStatus(int disposalId, String status) {
        if (processing) return;
        processing = true;
        Map<String, String> body = new HashMap<>();
        body.put("disposal_status", status);
        RetrofitClient.getApiService().updateDisposalStatus(
                authorization(), disposalId, body).enqueue(actionCallback());
    }

    private void finalizeDisposal(DisposalRequestModel request) {
        if (processing || !"For Disposal".equals(request.getStatus())) return;
        processing = true;
        RetrofitClient.getApiService().finalizeDisposal(
                authorization(), request.getId()).enqueue(actionCallback());
    }

    private Callback<GenericResponse> actionCallback() {
        return new Callback<GenericResponse>() {
            @Override
            public void onResponse(Call<GenericResponse> call,
                                   Response<GenericResponse> response) {
                processing = false;
                if (response.isSuccessful()) {
                    String message = response.body() != null
                            && !TextUtils.isEmpty(response.body().getMessage())
                            ? response.body().getMessage() : "Disposal updated successfully";
                    Toast.makeText(DisposalActivity.this, message, Toast.LENGTH_LONG).show();
                    loadDisposals();
                    Intent changed = new Intent(ItemsActivity.ACTION_INVENTORY_DATA_CHANGED);
                    changed.setPackage(getPackageName());
                    sendBroadcast(changed);
                } else {
                    Toast.makeText(DisposalActivity.this, backendMessage(response),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<GenericResponse> call, Throwable t) {
                processing = false;
                Toast.makeText(DisposalActivity.this, "Something went wrong",
                        Toast.LENGTH_LONG).show();
            }
        };
    }

    private String backendMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String message = new JSONObject(response.errorBody().string())
                        .optString("message");
                if (!message.isEmpty()) return message;
            }
        } catch (Exception ignored) { }
        return "Something went wrong";
    }
}
