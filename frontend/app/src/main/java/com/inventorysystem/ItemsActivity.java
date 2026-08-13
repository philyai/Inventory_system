package com.inventorysystem;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.inventorysystem.ConnectivityandService.ApiService;
import com.inventorysystem.ConnectivityandService.ApiErrorHandler;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.CategoryModel;
import com.inventorysystem.Model.ItemModel;
import com.inventorysystem.Model.GenericResponse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ItemsActivity extends SwipeNavigationActivity {
    public static final String ACTION_INVENTORY_DATA_CHANGED =
            "com.inventorysystem.INVENTORY_DATA_CHANGED";
    private static final int PAGE_SIZE = 20;
    private static final long SEARCH_DEBOUNCE_MS = 350L;
    private String token;
    private SessionManager sessionManager;
    private RecyclerView recyclerItems;
    private ItemsAdapter adapter;
    private LinearLayout navReports;
    private EditText etSearch;

    private LinearLayout categoryChipContainer;
    private final List<TextView> categoryChips = new ArrayList<>();
    private final Map<String, Integer> categoryIds = new HashMap<>();
    private String currentCategoryFilter = "All";
    private Integer currentCategoryId;
    private TextView txtTabAll;
    private TextView txtTabRemarks;
    private boolean remarksOnly;
    private int itemsRequestVersion;
    private int nextItemsPage = 1;
    private boolean loadingItems;
    private boolean hasMoreItems = true;
    private boolean firstResume = true;
    private Call<List<ItemModel>> activeItemsCall;
    private final List<ItemModel> loadedItems = new ArrayList<>();
    private final Set<Integer> loadedItemIds = new HashSet<>();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::loadItems;
    private boolean inventoryReceiverRegistered;
    private final BroadcastReceiver inventoryChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (token != null) loadItems();
        }
    };

    private LinearLayout navDashboard;
    private LinearLayout navDisposal;
    private ImageView navAddItem;
    private boolean disposalSubmitting;
    private boolean canRequestDisposal;
    private ProgressBar progressItems;
    private TextView txtItemsState;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_items);

        sessionManager = new SessionManager(this);
        token = sessionManager.getToken();

        recyclerItems = findViewById(R.id.recyclerItems);
        recyclerItems.setLayoutManager(new LinearLayoutManager(this));
        String role = sessionManager.getRole();
        canRequestDisposal = "IT".equals(role) || "Admin IT".equals(role);
        adapter = new ItemsAdapter();
        recyclerItems.setAdapter(adapter);
        setupItemPagination();
        setupDisposalSwipe();

        etSearch = findViewById(R.id.etSearch);
        categoryChipContainer = findViewById(R.id.categoryChipContainer);
        progressItems = findViewById(R.id.progressItems);
        txtItemsState = findViewById(R.id.txtItemsState);
        txtItemsState.setOnClickListener(v -> loadItems());
        txtTabAll = findViewById(R.id.txtTabAll);
        txtTabRemarks = findViewById(R.id.txtTabRemarks);
        txtTabAll.setOnClickListener(v -> selectItemsTab(false));
        txtTabRemarks.setOnClickListener(v -> selectItemsTab(true));
        updateTabStyles();

        navDashboard = findViewById(R.id.navDashboard);
        navAddItem = findViewById(R.id.navAddItem);
        navReports = findViewById(R.id.navReports);
        navDisposal = findViewById(R.id.navDisposal);
        if ("Purchasing".equals(role)) {
            navAddItem.setVisibility(View.GONE);
            navAddItem.setEnabled(false);
        }
        NavigationUi.attachDrawer(this, findViewById(R.id.btnMenu));
        NavigationUi.selectBottomItem(findViewById(R.id.navItems), navDashboard,
                findViewById(R.id.navItems), navReports,
                findViewById(R.id.navNotifications), navDisposal);
        setupSearchListener();
        setupNavigationListeners();
        buildCategoryChips(new ArrayList<>());

        if (token != null) {
            loadCategories();
            loadItems();
        } else {
            Toast.makeText(this, "Missing auth token", Toast.LENGTH_LONG).show();
        }
    }

    private void setupNavigationListeners() {
        navDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(ItemsActivity.this, DashboardActivity.class);
            intent.putExtra("token", token);
            startActivity(intent);
            finish();
        });
        if (navAddItem.getVisibility() == View.VISIBLE) {
            navAddItem.setOnClickListener(v -> {
                Intent intent = new Intent(ItemsActivity.this, AddItemActivity.class);
                intent.putExtra("token", token);
                startActivity(intent);
                finish();
            });
        }
        navReports.setOnClickListener(v -> {
            Intent intent = new Intent(ItemsActivity.this, ReportsActivity.class);
            intent.putExtra("token", token);
            startActivity(intent);
            finish();
        });
        findViewById(R.id.navNotifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
        navDisposal.setOnClickListener(v ->
                startActivity(new Intent(ItemsActivity.this, DisposalActivity.class)));
    }

    private void setupDisposalSwipe() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0,
                canRequestDisposal ? ItemTouchHelper.RIGHT : 0) {
            private final Paint background = new Paint();
            private final Paint text = new Paint();
            {
                background.setColor(Color.parseColor("#E5484D"));
                text.setColor(Color.WHITE);
                text.setTextSize(36f);
                text.setFakeBoldText(true);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                  RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                ItemModel item = adapter.getItemAt(position);
                if (item == null) {
                    adapter.notifyDataSetChanged();
                } else {
                    showDisposalDialog(item, position);
                }
            }

            @Override
            public void onChildDraw(Canvas canvas, RecyclerView recyclerView,
                                    RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (dX > 0) {
                    View row = viewHolder.itemView;
                    canvas.drawRect(row.getLeft(), row.getTop(),
                            row.getLeft() + dX, row.getBottom(), background);
                    canvas.drawText("Request Disposal", row.getLeft() + 28,
                            row.getTop() + row.getHeight() / 2f + 12, text);
                }
                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY,
                        actionState, isCurrentlyActive);
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(recyclerItems);
    }

    private void showDisposalDialog(ItemModel item, int position) {
        if (!canRequestDisposal || disposalSubmitting) {
            resetSwipe(position);
            return;
        }
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(padding, 0, padding, 0);

        TextView availableQuantity = new TextView(this);
        availableQuantity.setText("Available quantity: " + item.getQuantity());
        fields.addView(availableQuantity);

        EditText quantity = new EditText(this);
        quantity.setHint("Quantity to dispose");
        quantity.setInputType(InputType.TYPE_CLASS_NUMBER);
        fields.addView(quantity);

        EditText reason = new EditText(this);
        reason.setHint("Reason for disposal");
        reason.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reason.setFilters(new InputFilter[]{new InputFilter.LengthFilter(255)});
        reason.setMinLines(3);
        reason.setMaxLines(5);
        fields.addView(reason);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Request Disposal")
                .setMessage("Submit a disposal request for " + item.getItemName() + "?")
                .setView(fields)
                .setNegativeButton("Cancel", (d, which) -> resetSwipe(position))
                .setPositiveButton("Submit", null)
                .setOnCancelListener(d -> resetSwipe(position))
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String quantityValue = quantity.getText().toString().trim();
                    if (quantityValue.isEmpty()) {
                        quantity.setError("Disposal quantity is required");
                        return;
                    }
                    int disposalQuantity;
                    try {
                        disposalQuantity = Integer.parseInt(quantityValue);
                    } catch (NumberFormatException e) {
                        quantity.setError("Disposal quantity must be a whole number");
                        return;
                    }
                    if (disposalQuantity < 1) {
                        quantity.setError("Disposal quantity must be at least 1");
                        return;
                    }
                    if (disposalQuantity > item.getQuantity()) {
                        quantity.setError("Disposal quantity cannot exceed "
                                + item.getQuantity());
                        return;
                    }
                    String value = reason.getText().toString().trim();
                    if (value.isEmpty()) {
                        reason.setError("Disposal reason is required");
                        return;
                    }
                    submitDisposal(item.getItemId(), disposalQuantity, value, position, dialog);
                }));
        dialog.show();
    }

    private void submitDisposal(int itemId, int disposalQuantity, String reason, int position,
                                AlertDialog dialog) {
        if (disposalSubmitting) return;
        disposalSubmitting = true;
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        Map<String, Object> body = new HashMap<>();
        body.put("item_id", itemId);
        body.put("disposal_quantity", disposalQuantity);
        body.put("reason", reason);
        RetrofitClient.getApiService().createDisposal("Bearer " + token, body)
                .enqueue(new Callback<GenericResponse>() {
                    @Override
                    public void onResponse(Call<GenericResponse> call,
                                           Response<GenericResponse> response) {
                        disposalSubmitting = false;
                        resetSwipe(position);
                        if (response.code() == 201) {
                            dialog.dismiss();
                            String message = response.body() != null
                                    && response.body().getMessage() != null
                                    ? response.body().getMessage()
                                    : "Disposal request submitted successfully";
                            Toast.makeText(ItemsActivity.this, message, Toast.LENGTH_LONG).show();
                            loadItems();
                            Intent changed = new Intent(ACTION_INVENTORY_DATA_CHANGED);
                            changed.setPackage(getPackageName());
                            sendBroadcast(changed);
                        } else {
                            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                            Toast.makeText(ItemsActivity.this, backendMessage(response),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<GenericResponse> call, Throwable t) {
                        disposalSubmitting = false;
                        resetSwipe(position);
                        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                        Toast.makeText(ItemsActivity.this, "Something went wrong",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void resetSwipe(int position) {
        if (position >= 0 && position < adapter.getItemCount()) {
            adapter.notifyItemChanged(position);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    private String backendMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                String message = new JSONObject(raw).optString("message");
                if (!message.isEmpty()) return message;
            }
        } catch (Exception ignored) { }
        return "Something went wrong";
    }

    private void selectItemsTab(boolean showRemarksOnly) {
        if (remarksOnly == showRemarksOnly) return;
        searchHandler.removeCallbacks(searchRunnable);
        remarksOnly = showRemarksOnly;
        currentCategoryFilter = "All";
        currentCategoryId = null;
        adapter.setRemarksMode(remarksOnly);
        updateTabStyles();
        loadItems();
    }

    private void updateTabStyles() {
        txtTabAll.setBackgroundResource(remarksOnly
                ? R.drawable.bg_unselected : R.drawable.bg_selected);
        txtTabRemarks.setBackgroundResource(remarksOnly
                ? R.drawable.bg_selected : R.drawable.bg_unselected);
        txtTabAll.setTextColor(remarksOnly ? getColor(R.color.gray) : Color.WHITE);
        txtTabRemarks.setTextColor(remarksOnly ? Color.WHITE : getColor(R.color.gray));
    }

    private void loadItems() {
        searchHandler.removeCallbacks(searchRunnable);
        int requestVersion = ++itemsRequestVersion;
        if (activeItemsCall != null) activeItemsCall.cancel();
        nextItemsPage = 1;
        hasMoreItems = true;
        loadingItems = false;
        loadedItems.clear();
        loadedItemIds.clear();
        adapter.setItems(new ArrayList<>());
        loadItemsPage(requestVersion, true);
    }

    private void setupItemPagination() {
        recyclerItems.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loadingItems || !hasMoreItems) return;
                LinearLayoutManager layoutManager =
                        (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (lastVisible >= adapter.getItemCount() - 5) {
                    loadItemsPage(itemsRequestVersion, false);
                }
            }
        });
    }

    private void loadItemsPage(int requestVersion, boolean firstPage) {
        if (loadingItems || !hasMoreItems || requestVersion != itemsRequestVersion) return;
        loadingItems = true;

        if (firstPage) {
            progressItems.setVisibility(View.VISIBLE);
            txtItemsState.setVisibility(View.GONE);
            recyclerItems.setVisibility(View.GONE);
        }

        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
        final boolean requestRemarksOnly = remarksOnly;
        String search = etSearch.getText().toString().trim();
        final String requestSearch = search.isEmpty() ? null : search;
        final Integer requestCategoryId = currentCategoryId;
        final int requestedPage = nextItemsPage;
        activeItemsCall = apiService.getItems(
                "Bearer " + token,
                requestRemarksOnly ? true : null,
                requestSearch,
                requestCategoryId,
                requestedPage,
                PAGE_SIZE);
        activeItemsCall.enqueue(new Callback<List<ItemModel>>() {
            @Override
            public void onResponse(Call<List<ItemModel>> call,
                                   Response<List<ItemModel>> response) {
                if (requestVersion != itemsRequestVersion) return;
                loadingItems = false;
                activeItemsCall = null;
                if (firstPage) progressItems.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    List<ItemModel> pageItems = response.body();
                    for (ItemModel item : pageItems) {
                        if (loadedItemIds.add(item.getItemId())) loadedItems.add(item);
                    }
                    hasMoreItems = responseHasMore(response, pageItems.size());
                    nextItemsPage = requestedPage + 1;
                    adapter.setRemarksMode(requestRemarksOnly);
                    adapter.setItems(new ArrayList<>(loadedItems));

                    boolean empty = loadedItems.isEmpty();
                    recyclerItems.setVisibility(empty ? View.GONE : View.VISIBLE);
                    if (requestSearch != null) {
                        txtItemsState.setText("No matching items");
                    } else {
                        txtItemsState.setText(requestRemarksOnly
                                ? "No items with remarks" : "No inventory items");
                    }
                    txtItemsState.setVisibility(empty ? View.VISIBLE : View.GONE);
                } else if (firstPage) {
                    txtItemsState.setText(ApiErrorHandler.message(response)
                            + "\nTap to retry");
                    txtItemsState.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(ItemsActivity.this,
                            ApiErrorHandler.message(response), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<List<ItemModel>> call, Throwable t) {
                if (requestVersion != itemsRequestVersion) return;
                loadingItems = false;
                activeItemsCall = null;
                if (call.isCanceled()) return;
                if (firstPage) {
                    progressItems.setVisibility(View.GONE);
                    txtItemsState.setText(
                            "Cannot connect to Inventory Server\nTap to retry");
                    txtItemsState.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(ItemsActivity.this,
                            "Cannot load more items", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private boolean responseHasMore(Response<?> response, int returnedCount) {
        String header = response.headers().get("X-Has-More");
        return header != null ? Boolean.parseBoolean(header) : returnedCount == PAGE_SIZE;
    }

    private void loadCategories() {
        RetrofitClient.getApiService().getCategories("Bearer " + token)
                .enqueue(new Callback<List<CategoryModel>>() {
                    @Override
                    public void onResponse(Call<List<CategoryModel>> call,
                                           Response<List<CategoryModel>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            buildCategoryChips(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<List<CategoryModel>> call, Throwable t) {
                        // Keep the All chip available when categories cannot be refreshed.
                    }
                });
    }

    private void buildCategoryChips(List<CategoryModel> categories) {
        categoryChipContainer.removeAllViews();
        categoryChips.clear();
        categoryIds.clear();
        addCategoryChip("All");
        for (CategoryModel category : categories) {
            String categoryName = category.getCategoryName();
            if (categoryName != null && !categoryName.trim().isEmpty()
                    && !categoryIds.containsKey(categoryName)) {
                categoryIds.put(categoryName, category.getCategoryId());
                addCategoryChip(categoryName);
            }
        }
        currentCategoryFilter = categoryIds.containsKey(currentCategoryFilter)
                ? currentCategoryFilter : "All";
        currentCategoryId = "All".equals(currentCategoryFilter)
                ? null : categoryIds.get(currentCategoryFilter);
        updateCategoryChipStyles();
    }

    private void addCategoryChip(String category) {
        TextView chip = new TextView(this);
        chip.setText(category);
        chip.setTextSize(13);
        chip.setPadding(dp(18), dp(9), dp(18), dp(9));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(10));
        chip.setLayoutParams(params);
        chip.setOnClickListener(v -> selectChip(category));
        categoryChips.add(chip);
        categoryChipContainer.addView(chip);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void selectChip(String category) {
        searchHandler.removeCallbacks(searchRunnable);
        currentCategoryFilter = category;
        currentCategoryId = "All".equals(category) ? null : categoryIds.get(category);
        updateCategoryChipStyles();
        loadItems();
    }

    private void updateCategoryChipStyles() {
        for (TextView chip : categoryChips) {
            boolean selected = currentCategoryFilter.equals(chip.getText().toString());
            chip.setBackgroundResource(selected
                    ? R.drawable.bg_selected : R.drawable.bg_unselected);
            chip.setTextColor(selected ? Color.WHITE : getColor(R.color.gray));
        }
    }

    private void setupSearchListener() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!inventoryReceiverRegistered) {
            ContextCompat.registerReceiver(this, inventoryChangedReceiver,
                    new IntentFilter(ACTION_INVENTORY_DATA_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            inventoryReceiverRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        searchHandler.removeCallbacks(searchRunnable);
        if (inventoryReceiverRegistered) {
            unregisterReceiver(inventoryChangedReceiver);
            inventoryReceiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
        } else if (token != null) {
            loadItems();
        }
    }
}
