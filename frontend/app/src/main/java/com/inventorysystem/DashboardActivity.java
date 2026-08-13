package com.inventorysystem;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;


import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.inventorysystem.ConnectivityandService.ApiService;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.offline.InventoryDatabase;
import com.inventorysystem.offline.OfflineItemSyncScheduler;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardActivity extends SwipeNavigationActivity {

    private TextView txtITAdmin;
    private TextView txtGreetingRole;
    private TextView txtTotalItems;
    private TextView txtTotalValueValue;
    private TextView txtStock;
    private TextView txtStockCount;
    private TextView txtDisposal;
    private SessionManager sessionManager;
    private PieChart pieChart;
    private LinearLayout chartContent;
    private ScrollView categoryLegendScroll;
    private TextView txtSyncStatus;

    private LinearLayout categoryLegendContainer;

    // Bottom navigation
    private LinearLayout navHome;
    private LinearLayout navItem;
    private LinearLayout navReports;
    private LinearLayout navNotif;
    private LinearLayout navDisposal;

    // Auth/session info, kept as fields so nav listeners can reuse them
    private String token;
    private String username;
    private String role;

    // Colors matching the legend dots in activity_dashboard.xml
    private static final int COLOR_MONITORS   = Color.parseColor("#2979FF"); // blue
    private static final int COLOR_PRINTERS   = Color.parseColor("#00A896"); // teal
    private static final int COLOR_AVR_UPS    = Color.parseColor("#B71C1C"); // dark red
    private static final int COLOR_POWER_PLUG = Color.parseColor("#8E44AD"); // purple
    private static final int COLOR_CONNECTORS = Color.parseColor("#F39C12"); // orange/gold
    private static final int COLOR_OTHERS     = Color.parseColor("#B0BEC5"); // gray

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Bind dashboard views
        txtITAdmin = findViewById(R.id.txtITAdmin);
        txtGreetingRole = findViewById(R.id.txtGreetingRole);
        txtTotalItems = findViewById(R.id.txtTotalItems);
        txtTotalValueValue = findViewById(R.id.txtTotalValueValue);
        txtStock = findViewById(R.id.txtStock);
        txtStockCount = findViewById(R.id.txtStockCount);
        txtDisposal = findViewById(R.id.txtDisposal);
        pieChart = findViewById(R.id.pieChart);
        chartContent = findViewById(R.id.chartContent);
        categoryLegendScroll = findViewById(R.id.categoryLegendScroll);
        txtSyncStatus = findViewById(R.id.txtSyncStatus);

        categoryLegendContainer = findViewById(R.id.categoryLegendContainer);

        // Bind bottom nav
        navHome = findViewById(R.id.navHome);
        navItem = findViewById(R.id.navItem);
        navReports = findViewById(R.id.navReports);
        navNotif = findViewById(R.id.navNotif);
        navDisposal = findViewById(R.id.navDisposal);
        NavigationUi.attachDrawer(this, findViewById(R.id.btnMenu));
        NavigationUi.selectBottomItem(navHome, navHome, navItem, navReports, navNotif, navDisposal);

        configureResponsiveChartLayout();
        setupPieChartStyle();
        setupNavigationListeners();

        sessionManager = new SessionManager(this);
        username = sessionManager.getUsername();
        role = sessionManager.getRole();
        token = sessionManager.getToken();

        int userId = sessionManager.getUserId();
        if ("Purchasing".equals(role)) {
            txtSyncStatus.setVisibility(View.GONE);
        } else if (userId > 0) {
            InventoryDatabase.get(this).offlineDao().observePendingCount(userId).observe(this, count -> updateSyncStatus(count, null));
            InventoryDatabase.get(this).offlineDao().observeFailedCount(userId).observe(this, count -> updateSyncStatus(null, count));
            txtSyncStatus.setOnClickListener(v -> startActivity(new Intent(this, PendingSyncActivity.class)));
            OfflineItemSyncScheduler.enqueue(this);
            WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(OfflineItemSyncScheduler.WORK_NAME)
                    .observe(this, workInfos -> {
                        if (workInfos == null) return;
                        for (WorkInfo info : workInfos) {
                            Log.d("OfflineSync", "Work ID=" + info.getId() + ", state=" + info.getState());
                        }
                    });
        }

        if (txtITAdmin != null && username != null) {
            txtITAdmin.setText(username);
        }
        if (txtGreetingRole != null) {
            if (role == null || role.trim().isEmpty()) {
                txtGreetingRole.setVisibility(View.GONE);
            } else {
                txtGreetingRole.setText(role.trim());
            }
        }

        if (token != null) {
            loadDashboard(token);
            loadStockCategory(token);
        } else {
            Toast.makeText(this, "Missing auth token", Toast.LENGTH_LONG).show();
        }
    }

    private Integer pendingSyncCount = 0;
    private Integer failedSyncCount = 0;

    private void updateSyncStatus(Integer pending, Integer failed) {
        if (pending != null) pendingSyncCount = pending;
        if (failed != null) failedSyncCount = failed;
        txtSyncStatus.setText(pendingSyncCount + " items pending sync • " + failedSyncCount + " need attention");
    }
    private void setupNavigationListeners() {


        navHome.setOnClickListener(v -> {
        });

        navItem.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, ItemsActivity.class);
            intent.putExtra("token", token);
            startActivity(intent);
        });

        navReports.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, ReportsActivity.class);
            startActivity(intent);
        });

        navNotif.setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));

        navDisposal.setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, DisposalActivity.class));
        });
    }

    private void setupPieChartStyle() {
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setDrawEntryLabels(false);
        pieChart.setUsePercentValues(true);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(55f);
        pieChart.setTransparentCircleRadius(0f);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setCenterText("");
        pieChart.setRotationEnabled(false);
        pieChart.setHighlightPerTapEnabled(false);
        pieChart.setMinOffset(0f);
        pieChart.setExtraOffsets(6f, 6f, 6f, 6f);
    }

    private void configureResponsiveChartLayout() {
        boolean wideScreen = getResources().getConfiguration().screenWidthDp >= 600;
        chartContent.setOrientation(wideScreen
                ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        LinearLayout.LayoutParams chartParams;
        LinearLayout.LayoutParams legendParams;
        if (wideScreen) {
            chartParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            legendParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            categoryLegendScroll.setPadding(dp(12), 0, 0, 0);
        } else {
            chartParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f);
            legendParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 2f);
            categoryLegendScroll.setPadding(0, dp(4), 0, 0);
        }
        pieChart.setLayoutParams(chartParams);
        categoryLegendScroll.setLayoutParams(legendParams);
        categoryLegendScroll.setOnTouchListener((view, event) -> {
            boolean gestureInProgress = event.getActionMasked() != android.view.MotionEvent.ACTION_UP
                    && event.getActionMasked() != android.view.MotionEvent.ACTION_CANCEL;
            view.getParent().requestDisallowInterceptTouchEvent(gestureInProgress);
            return false;
        });
    }

    private void loadStockCategory(String token) {

        ApiService apiService =
                RetrofitClient.getClient().create(ApiService.class);

        apiService.getStockByCategory("Bearer " + token)
                .enqueue(new Callback<List<StockCategoryModel>>() {

                    @Override
                    public void onResponse(Call<List<StockCategoryModel>> call,
                                           Response<List<StockCategoryModel>> response) {

                        if (response.isSuccessful() && response.body() != null) {
                            List<StockCategoryModel> categories = response.body();

                            populateLegend(categories);
                            showCategoryPieChart(categories);
                        } else {
                            Toast.makeText(
                                    DashboardActivity.this,
                                    "Failed to load category data: " + response.code(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<StockCategoryModel>> call, Throwable t) {
                        Toast.makeText(
                                DashboardActivity.this,
                                "Connection Failed: " + t.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                        Log.e("STOCK_CATEGORY", t.getMessage(), t);
                    }
                });
    }

    private void populateLegend(List<StockCategoryModel> categories) {
        categoryLegendContainer.removeAllViews();
        int grandTotal = 0;
        for (StockCategoryModel item : categories) {
            if (item.getItemCount() > 0 && item.getTotalQuantity() > 0) {
                grandTotal += item.getTotalQuantity();
            }
        }

        for (StockCategoryModel item : categories) {
            if (item.getItemCount() <= 0 || item.getTotalQuantity() <= 0) continue;
            String name = item.getCategory() != null
                    ? item.getCategory().getCategoryName()
                    : "";

            int qty = item.getTotalQuantity();
            double pct = grandTotal > 0 ? (qty * 100.0 / grandTotal) : 0;
            String label = String.format("%.0f%% (%d)", pct, qty);
            addLegendRow(name, label, getColorForCategory(name));
        }
    }

    private void addLegendRow(String name, String value, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        View dot = new View(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        dot.setBackground(circle);
        row.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextSize(12);
        nameView.setTextColor(getColor(R.color.black));
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nameParams.setMarginStart(dp(8));
        row.addView(nameView, nameParams);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(12);
        valueView.setTextColor(getColor(R.color.gray));
        valueView.setSingleLine(true);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.setMarginStart(dp(8));
        row.addView(valueView, valueParams);
        categoryLegendContainer.addView(row);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showCategoryPieChart(List<StockCategoryModel> categories) {

        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        for (StockCategoryModel item : categories) {
            if (item.getItemCount() <= 0 || item.getTotalQuantity() <= 0) continue;
            String name = item.getCategory() != null
                    ? item.getCategory().getCategoryName()
                    : "Unknown";

            entries.add(new PieEntry((float) item.getTotalQuantity(), name));
            colors.add(getColorForCategory(name));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setDrawValues(false);
        dataSet.setSliceSpace(2f);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.setUsePercentValues(true); // chart computes % from raw quantities itself
        pieChart.animateY(800);
        pieChart.invalidate();
    }

    private int getColorForCategory(String name) {
        switch (name.toLowerCase()) {
            case "monitors": return COLOR_MONITORS;
            case "printers": return COLOR_PRINTERS;
            case "avr / ups":
            case "avr/ups": return COLOR_AVR_UPS;
            case "power plug": return COLOR_POWER_PLUG;
            case "connectors": return COLOR_CONNECTORS;
            case "others": return COLOR_OTHERS;
            default:
                float hue = Math.abs(name.hashCode() % 360);
                return Color.HSVToColor(new float[]{hue, 0.65f, 0.85f});
        }
    }

    private void loadDashboard(String token) {

        ApiService apiService =
                RetrofitClient.getClient().create(ApiService.class);

        apiService.getDashboard("Bearer " + token)
                .enqueue(new Callback<DashboardModel>() {

                    @Override
                    public void onResponse(Call<DashboardModel> call,
                                           Response<DashboardModel> response) {

                        if (response.isSuccessful() && response.body() != null) {

                            DashboardModel dashboard = response.body();

                            txtTotalItems.setText(String.valueOf(dashboard.getTotalItems()));
                            txtTotalValueValue.setText(String.format("₱%.2f", dashboard.getTotalValue()));
                            txtStock.setText(String.valueOf(dashboard.getItemsInStock()));
                            txtStockCount.setText(String.valueOf(dashboard.getLowStock()));
                            txtDisposal.setText(String.valueOf(dashboard.getForDisposal()));

                        } else {
                            Toast.makeText(
                                    DashboardActivity.this,
                                    "Server Error: " + response.code(),
                                    Toast.LENGTH_LONG
                            ).show();

                            try {
                                if (response.errorBody() != null) {
                                    Log.e("API_ERROR", response.errorBody().string());
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<DashboardModel> call,
                                          Throwable t) {

                        Toast.makeText(
                                DashboardActivity.this,
                                "Connection Failed: " + t.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();

                        Log.e("API_FAILURE", t.getMessage(), t);
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh dashboard data in case an item was just added in AddItemActivity
        if (token != null) {
            loadDashboard(token);
            loadStockCategory(token);
        }
    }
}
