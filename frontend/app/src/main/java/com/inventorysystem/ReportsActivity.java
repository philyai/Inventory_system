package com.inventorysystem;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.inventorysystem.ConnectivityandService.ApiService;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.CategoryBreakdownModel;
import com.inventorysystem.Model.DisposalRequestModel;
import com.inventorysystem.Model.DisposalReportResponse;
import com.inventorysystem.Model.GenericResponse;
import com.inventorysystem.Model.ItemModel;
import com.inventorysystem.Model.LocationBreakdownModel;
import com.inventorysystem.Model.LowStockModel;
import com.inventorysystem.Model.StockMovementModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Activity backing the multi-report screen (Stock Movement / Low Stock /
 * Disposal / Category / Location), matching activity_reports.xml.
 *
 * Each tab loads its data from the backend the first time it's selected
 * (simple lazy-load / cache-per-session) and renders it through its own
 * RecyclerView + adapter, since list lengths are backend-driven and not
 * fixed.
 */
public class ReportsActivity extends SwipeNavigationActivity {

    private static final int PAGE_SIZE = 20;
    private ApiService apiService;
    private String bearerToken;

    // Top bar
    private ImageButton bttnBack;

    // Report tabs
    private TextView tabMovement, tabLowStock, tabDisposal, tabCategory, tabLocation;
    private List<TextView> tabs;

    // Per-tab header widgets (only one group visible at a time)
    private View segmentRow, lowStockHeader, disposalHeader;

    // Stock Movement: segmented filter
    private TextView segAll, segIn, segOut, segAdjust;
    private List<TextView> segments;

    // Low Stock: search
    private EditText etSearch;

    // Disposal: summary counts
    private TextView dispPendingCount, dispApprovedCount, dispDisposedCount;

    // RecyclerViews + adapters
    private RecyclerView recyclerMovement, recyclerLowStock, recyclerDisposal, recyclerCategory, recyclerLocation;
    private List<RecyclerView> recyclers;
    private StockMovementAdapter movementAdapter;
    private LowStockAdapter lowStockAdapter;
    private DisposalRequestAdapter disposalAdapter;
    private CategoryBreakdownAdapter categoryAdapter;
    private LocationBreakdownAdapter locationAdapter;

    // Loading / empty state
    private ProgressBar progressBar;
    private TextView emptyState;
    private SwipeRefreshLayout swipeRefresh;

    // Tracks which tabs have already been fetched this session
    private final boolean[] loaded = new boolean[5]; // movement, lowStock, disposal, category, location
    private int selectedTabIndex;
    private final List<StockMovementModel> loadedMovements = new ArrayList<>();
    private final Set<Integer> loadedMovementIds = new HashSet<>();
    private int nextMovementPage = 1;
    private int movementRequestVersion;
    private boolean loadingMovements;
    private boolean hasMoreMovements = true;
    private String selectedMovementType;

    // Bottom navigation
    private View navDashboard, navItems, navReports, navNotifications, navDisposal;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        SessionManager session = new SessionManager(this);
        bearerToken = "Bearer " + session.getToken();
        apiService = RetrofitClient.getApiService();

        bindViews();
        setupBackButton();
        setupPullToRefresh();
        setupTabs();
        setupMovementSegments();
        setupLowStockSearch();
        setupDisposalActions();
        setupBottomNav();

        // Default state: Stock Movement tab.
        selectTab(0);
    }

    private void bindViews() {
        bttnBack = findViewById(R.id.bttnBack);

        tabMovement = findViewById(R.id.tabMovement);
        tabLowStock = findViewById(R.id.tabLowStock);
        tabDisposal = findViewById(R.id.tabDisposal);
        tabCategory = findViewById(R.id.tabCategory);
        tabLocation = findViewById(R.id.tabLocation);
        tabs = new ArrayList<>();
        tabs.add(tabMovement);
        tabs.add(tabLowStock);
        tabs.add(tabDisposal);
        tabs.add(tabCategory);
        tabs.add(tabLocation);

        segmentRow = findViewById(R.id.segmentRow);
        lowStockHeader = findViewById(R.id.lowStockHeader);
        disposalHeader = findViewById(R.id.disposalHeader);

        segAll = findViewById(R.id.segAll);
        segIn = findViewById(R.id.segIn);
        segOut = findViewById(R.id.segOut);
        segAdjust = findViewById(R.id.segAdjust);
        segments = new ArrayList<>();
        segments.add(segAll);
        segments.add(segIn);
        segments.add(segOut);
        segments.add(segAdjust);

        etSearch = findViewById(R.id.etSearch);

        dispPendingCount = findViewById(R.id.dispPendingCount);
        dispApprovedCount = findViewById(R.id.dispApprovedCount);
        dispDisposedCount = findViewById(R.id.dispDisposedCount);

        recyclerMovement = findViewById(R.id.recyclerMovement);
        recyclerLowStock = findViewById(R.id.recyclerLowStock);
        recyclerDisposal = findViewById(R.id.recyclerDisposal);
        recyclerCategory = findViewById(R.id.recyclerCategory);
        recyclerLocation = findViewById(R.id.recyclerLocation);
        recyclers = new ArrayList<>();
        recyclers.add(recyclerMovement);
        recyclers.add(recyclerLowStock);
        recyclers.add(recyclerDisposal);
        recyclers.add(recyclerCategory);
        recyclers.add(recyclerLocation);

        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        movementAdapter = new StockMovementAdapter();
        recyclerMovement.setLayoutManager(new LinearLayoutManager(this));
        recyclerMovement.setAdapter(movementAdapter);
        setupMovementPagination();

        lowStockAdapter = new LowStockAdapter();
        recyclerLowStock.setLayoutManager(new LinearLayoutManager(this));
        recyclerLowStock.setAdapter(lowStockAdapter);

        disposalAdapter = new DisposalRequestAdapter(false, null); // read-only in Reports
        recyclerDisposal.setLayoutManager(new LinearLayoutManager(this));
        recyclerDisposal.setAdapter(disposalAdapter);

        categoryAdapter = new CategoryBreakdownAdapter();
        recyclerCategory.setLayoutManager(new LinearLayoutManager(this));
        recyclerCategory.setAdapter(categoryAdapter);

        locationAdapter = new LocationBreakdownAdapter();
        recyclerLocation.setLayoutManager(new LinearLayoutManager(this));
        recyclerLocation.setAdapter(locationAdapter);

        navDashboard = findViewById(R.id.navDashboard);
        navItems = findViewById(R.id.navItems);
        navReports = findViewById(R.id.navReports);
        navNotifications = findViewById(R.id.navNotifications);
        navDisposal = findViewById(R.id.navDisposal);
        NavigationUi.selectBottomItem(navReports, navDashboard, navItems, navReports,
                navNotifications, navDisposal);
    }

    private void setupBackButton() {
        NavigationUi.attachDrawer(this, bttnBack);
    }

    private void setupPullToRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.blue, R.color.green, R.color.orange);
        swipeRefresh.setOnRefreshListener(() -> {
            loaded[selectedTabIndex] = false;
            emptyState.setVisibility(View.GONE);
            loadTab(selectedTabIndex);
        });
    }

    // ---------------------------------------------------------------------
    // Report tabs
    // ---------------------------------------------------------------------

    private void setupTabs() {
        tabMovement.setOnClickListener(v -> selectTab(0));
        tabLowStock.setOnClickListener(v -> selectTab(1));
        tabDisposal.setOnClickListener(v -> selectTab(2));
        tabCategory.setOnClickListener(v -> selectTab(3));
        tabLocation.setOnClickListener(v -> selectTab(4));
    }

    private void selectTab(int index) {
        selectedTabIndex = index;

        for (int i = 0; i < tabs.size(); i++) {
            boolean isSelected = i == index;
            TextView tab = tabs.get(i);
            tab.setBackgroundResource(isSelected ? R.drawable.bg_selected : R.drawable.bg_unselected);
            tab.setTextColor(getColor(isSelected ? R.color.white : R.color.gray));
        }
        for (int i = 0; i < recyclers.size(); i++) {
            recyclers.get(i).setVisibility(i == index ? View.VISIBLE : View.GONE);
        }

        segmentRow.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        lowStockHeader.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        disposalHeader.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        emptyState.setVisibility(View.GONE);

        if (!loaded[index]) {
            loadTab(index);
        }
    }

    private void loadTab(int index) {
        switch (index) {
            case 0:
                fetchStockMovements();
                break;
            case 1:
                fetchLowStock();
                break;
            case 2:
                fetchDisposalRequests();
                break;
            case 3:
                fetchCategoryBreakdown();
                break;
            case 4:
                fetchLocationBreakdown();
                break;
        }
    }

    // ---------------------------------------------------------------------
    // Stock Movement
    // ---------------------------------------------------------------------

    private void fetchStockMovements() {
        movementRequestVersion++;
        nextMovementPage = 1;
        loadingMovements = false;
        hasMoreMovements = true;
        loadedMovements.clear();
        loadedMovementIds.clear();
        movementAdapter.submitList(new ArrayList<>());
        loadMovementPage(movementRequestVersion, true);
    }

    private void setupMovementPagination() {
        recyclerMovement.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loadingMovements || !hasMoreMovements) return;
                LinearLayoutManager layoutManager =
                        (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;
                if (layoutManager.findLastVisibleItemPosition()
                        >= movementAdapter.getItemCount() - 5) {
                    loadMovementPage(movementRequestVersion, false);
                }
            }
        });
    }

    private void loadMovementPage(int requestVersion, boolean firstPage) {
        if (loadingMovements || !hasMoreMovements
                || requestVersion != movementRequestVersion) return;
        loadingMovements = true;
        if (firstPage) showLoading(true);

        final int requestedPage = nextMovementPage;
        apiService.getStockMovements(
                bearerToken,
                selectedMovementType,
                requestedPage,
                PAGE_SIZE).enqueue(new Callback<List<StockMovementModel>>() {
            @Override
            public void onResponse(@NonNull Call<List<StockMovementModel>> call,
                                   @NonNull Response<List<StockMovementModel>> response) {
                if (requestVersion != movementRequestVersion) return;
                loadingMovements = false;
                if (firstPage) showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    loaded[0] = true;
                    List<StockMovementModel> pageMovements = response.body();
                    for (StockMovementModel movement : pageMovements) {
                        if (loadedMovementIds.add(movement.getId())) {
                            loadedMovements.add(movement);
                        }
                    }
                    movementAdapter.submitList(new ArrayList<>(loadedMovements));
                    hasMoreMovements = responseHasMore(response, pageMovements.size());
                    nextMovementPage = requestedPage + 1;
                    if (selectedTabIndex == 0) {
                        showEmptyIfNeeded(loadedMovements.isEmpty());
                    }
                } else if (firstPage) {
                    showError("Couldn't load stock movements." + response.code());
                } else {
                    showError("Couldn't load more stock movements.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<StockMovementModel>> call, @NonNull Throwable t) {
                if (requestVersion != movementRequestVersion) return;
                loadingMovements = false;
                if (firstPage) {
                    showLoading(false);
                    showError("Network error loading stock movements.");
                } else {
                    showError("Network error loading more stock movements.");
                }
            }
        });
    }

    private boolean responseHasMore(Response<?> response, int returnedCount) {
        String header = response.headers().get("X-Has-More");
        return header != null ? Boolean.parseBoolean(header) : returnedCount == PAGE_SIZE;
    }

    private void setupMovementSegments() {
        segAll.setOnClickListener(v -> selectSegment(segAll));
        segIn.setOnClickListener(v -> selectSegment(segIn));
        segOut.setOnClickListener(v -> selectSegment(segOut));
        segAdjust.setOnClickListener(v -> selectSegment(segAdjust));
        selectSegment(segAll);
    }

    private void selectSegment(TextView selectedSegment) {
        String movementType = tagForSegment(selectedSegment);
        boolean changed = selectedMovementType == null
                ? movementType != null
                : !selectedMovementType.equals(movementType);
        selectedMovementType = movementType;
        for (TextView seg : segments) {
            boolean isSelected = seg == selectedSegment;
            seg.setBackgroundResource(isSelected ? R.drawable.bg_selected : 0);
            seg.setTextColor(getColor(isSelected ? R.color.white : R.color.gray));
        }
        movementAdapter.setTypeFilter(null);
        if (changed && loaded[0]) fetchStockMovements();
    }

    @Nullable
    private String tagForSegment(TextView segment) {
        if (segment == segIn) return "In";
        if (segment == segOut) return "Out";
        if (segment == segAdjust) return "Adjustment";
        return null; // "All" - no filtering
    }

    // ---------------------------------------------------------------------
    // Low Stock
    // ---------------------------------------------------------------------

    private void fetchLowStock() {
        showLoading(true);
        apiService.getLowStockItems(bearerToken).enqueue(new Callback<List<LowStockModel>>() {
            @Override
            public void onResponse(@NonNull Call<List<LowStockModel>> call,
                                   @NonNull Response<List<LowStockModel>> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    loaded[1] = true;
                    lowStockAdapter.submitList(response.body());
                    showEmptyIfNeeded(response.body().isEmpty());
                } else {
                    showError("Couldn't load low stock items." + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<LowStockModel>> call, @NonNull Throwable t) {
                showLoading(false);
                showError("Network error loading low stock items.");
            }
        });
    }

    private void setupLowStockSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                lowStockAdapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        findViewById(R.id.btnFilter).setOnClickListener(v ->
                Toast.makeText(this, "Open filter options", Toast.LENGTH_SHORT).show());
    }

    // ---------------------------------------------------------------------
    // Disposal
    // ---------------------------------------------------------------------

    private List<DisposalRequestModel> allDisposalRequests = new ArrayList<>();

    private void fetchDisposalRequests() {
        showLoading(true);
        apiService.getDisposalReport(bearerToken).enqueue(new Callback<DisposalReportResponse>() {
            @Override
            public void onResponse(@NonNull Call<DisposalReportResponse> call,
                                   @NonNull Response<DisposalReportResponse> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    loaded[2] = true;
                    List<DisposalRequestModel> disposals = response.body().getDisposals();
                    allDisposalRequests = disposals != null ? disposals : new ArrayList<>();
                    disposalAdapter.submitList(allDisposalRequests);
                    updateDisposalSummary();
                    showEmptyIfNeeded(allDisposalRequests.isEmpty());
                } else {
                    showError("Couldn't load disposal requests." + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<DisposalReportResponse> call, @NonNull Throwable t) {
                showLoading(false);
                showError("Network error loading disposal requests.");
            }
        });
    }

    private void updateDisposalSummary() {
        int pending = 0, approved = 0, disposed = 0;
        for (DisposalRequestModel r : allDisposalRequests) {
            String status = r.getStatus() != null ? r.getStatus().toLowerCase() : "";
            switch (status) {
                case "pending approval":
                    pending++;
                    break;
                case "for disposal":
                    approved++;
                    break;
                case "disposed":
                    disposed++;
                    break;
            }
        }
        dispPendingCount.setText(String.valueOf(pending));
        dispApprovedCount.setText(String.valueOf(approved));
        dispDisposedCount.setText(String.valueOf(disposed));
    }

    /**
     * This tab is read-only (DisposalRequestAdapter was built with
     * showActions = false), so no approve/reject wiring is needed here.
     * The actionable version lives in DisposalApprovalActivity, which
     * reuses the same adapter with showActions = true.
     */
    private void setupDisposalActions() {
        // Intentionally empty for the Reports tab.
    }

    // ---------------------------------------------------------------------
    // Category
    // ---------------------------------------------------------------------

    private void fetchCategoryBreakdown() {
        showLoading(true);
        apiService.getCategoryBreakdown(bearerToken).enqueue(new Callback<List<CategoryBreakdownModel>>() {
            @Override
            public void onResponse(@NonNull Call<List<CategoryBreakdownModel>> call,
                                   @NonNull Response<List<CategoryBreakdownModel>> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    loaded[3] = true;
                    categoryAdapter.submitList(response.body());
                    showEmptyIfNeeded(response.body().isEmpty());
                } else {
                    showError("Couldn't load category breakdown." + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<CategoryBreakdownModel>> call, @NonNull Throwable t) {
                showLoading(false);
                showError("Network error loading category breakdown.");
            }
        });
    }

    // ---------------------------------------------------------------------
    // Location
    // ---------------------------------------------------------------------

    private void fetchLocationBreakdown() {
        showLoading(true);
        apiService.getLocationBreakdown(bearerToken).enqueue(new Callback<List<LocationBreakdownModel>>() {
            @Override
            public void onResponse(@NonNull Call<List<LocationBreakdownModel>> call,
                                   @NonNull Response<List<LocationBreakdownModel>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    loadItemsForLocations(response.body());
                } else {
                    showLoading(false);
                    showError("Couldn't load location breakdown." + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<LocationBreakdownModel>> call, @NonNull Throwable t) {
                showLoading(false);
                showError("Network error loading location breakdown.");
            }
        });
    }

    private void loadItemsForLocations(List<LocationBreakdownModel> locations) {
        apiService.getAllItems(bearerToken).enqueue(new Callback<List<ItemModel>>() {
            @Override
            public void onResponse(@NonNull Call<List<ItemModel>> call,
                                   @NonNull Response<List<ItemModel>> response) {
                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    for (LocationBreakdownModel location : locations) {
                        List<ItemModel> matchingItems = new ArrayList<>();
                        for (ItemModel item : response.body()) {
                            boolean sameId = item.getLocationId() != 0
                                    && item.getLocationId() == location.getLocationId();
                            boolean sameName = item.getLocation() != null
                                    && item.getLocation().equalsIgnoreCase(location.getLocationName());
                            if (sameId || sameName) {
                                matchingItems.add(item);
                            }
                        }
                        location.setItems(matchingItems);
                    }
                } else {
                    showError("Location totals loaded, but item details could not be loaded.");
                }

                loaded[4] = true;
                locationAdapter.submitList(locations);
                showEmptyIfNeeded(locations.isEmpty());
            }

            @Override
            public void onFailure(@NonNull Call<List<ItemModel>> call, @NonNull Throwable t) {
                showLoading(false);
                loaded[4] = true;
                locationAdapter.submitList(locations);
                showEmptyIfNeeded(locations.isEmpty());
                showError("Location totals loaded, but item details could not be loaded.");
            }
        });
    }

    // ---------------------------------------------------------------------
    // Shared loading / error helpers
    // ---------------------------------------------------------------------

    private void showLoading(boolean loading) {
        boolean pullRefreshing = swipeRefresh != null && swipeRefresh.isRefreshing();
        progressBar.setVisibility(loading && !pullRefreshing ? View.VISIBLE : View.GONE);
        if (!loading && swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
        if (loading) emptyState.setVisibility(View.GONE);
    }

    private void showEmptyIfNeeded(boolean isEmpty) {
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------------
    // Bottom navigation
    // ---------------------------------------------------------------------

    private void setupBottomNav() {
        navDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(ReportsActivity.this, DashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        navItems.setOnClickListener(v -> {
            Intent intent = new Intent(ReportsActivity.this, ItemsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        navReports.setOnClickListener(v -> { /* already on Reports */ });
        navNotifications.setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
        navDisposal.setOnClickListener(v ->
                startActivity(new Intent(ReportsActivity.this, DisposalActivity.class)));
    }
}
