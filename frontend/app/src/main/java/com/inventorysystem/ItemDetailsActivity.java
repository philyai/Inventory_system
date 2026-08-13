package com.inventorysystem;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.inventorysystem.ConnectivityandService.AuthenticatedImageUrl;
import com.inventorysystem.Model.ItemModel;
import com.inventorysystem.Model.ItemRemarkIssue;
import com.inventorysystem.ConnectivityandService.ApiService;
import com.inventorysystem.ConnectivityandService.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ItemDetailsActivity extends AppCompatActivity {

    private ImageView imgItem;
    private TextView txtItemName;
    private TextView txtItemCode;
    private TextView txtCategory;
    private TextView txtBrand;
    private TextView txtModel;
    private TextView txtSerial;
    private TextView txtQuantity;
    private TextView txtUnitCost;
    private TextView txtTotalValue;
    private TextView txtLocation;
    private TextView txtDateAdded;
    private TextView txtStatus;
    private View issueSection;
    private TextView txtIssueCode;
    private TextView txtRemarks;

    private SessionManager sessionManager;
    private int itemId = -1;
    private final ActivityResultLauncher<Intent> editItemLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && itemId != -1) {
                    loadItem(itemId);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_details);

        initializeViews();
        sessionManager = new SessionManager(this);

        itemId = getIntent().getIntExtra("ITEM_ID", -1);
        View editButton = findViewById(R.id.btnEditItem);
        String role = sessionManager.getRole();
        boolean isItUser = "IT".equals(role) || "Admin IT".equals(role);
        editButton.setVisibility(isItUser ? View.VISIBLE : View.GONE);
        editButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditItemActivity.class);
            intent.putExtra("ITEM_ID", itemId);
            editItemLauncher.launch(intent);
        });

        if (itemId != -1) {
            loadItem(itemId);
        }
    }

    private void initializeViews() {
        imgItem = findViewById(R.id.imgItem);
        txtItemName = findViewById(R.id.txtItemName);
        txtItemCode = findViewById(R.id.txtItemCode);
        txtCategory = findViewById(R.id.txtCategory);
        txtBrand = findViewById(R.id.txtBrand);
        txtModel = findViewById(R.id.txtModel);
        txtSerial = findViewById(R.id.txtSerial);
        txtQuantity = findViewById(R.id.txtQuantity);
        txtUnitCost = findViewById(R.id.txtUnitCost);
        txtTotalValue = findViewById(R.id.txtTotalValue);
        txtLocation = findViewById(R.id.txtLocation);
        txtDateAdded = findViewById(R.id.txtDateAdded);
        txtStatus = findViewById(R.id.txtStatus);
        issueSection = findViewById(R.id.issueSection);
        txtIssueCode = findViewById(R.id.txtIssueCode);
        txtRemarks = findViewById(R.id.txtRemarks);
        findViewById(R.id.btnBackDetails).setOnClickListener(v -> finish());
    }

    private void loadItem(int id) {

        String token = "Bearer " + sessionManager.getToken();
        ApiService api = RetrofitClient.getApiService();

        api.getItemDetails(token, id).enqueue(new Callback<ItemModel>() {

            @Override
            public void onResponse(Call<ItemModel> call, Response<ItemModel> response) {

                if (response.isSuccessful() && response.body() != null) {

                    ItemModel item = response.body();

                    txtItemName.setText(item.getItemName());
                    txtItemCode.setText(item.getItemCode());

                    if (item.getCategory() != null) {
                        txtCategory.setText(item.getCategory().getCategoryName());
                    }

                    txtBrand.setText(item.getBrand());
                    txtModel.setText(item.getModel());
                    txtSerial.setText(item.getSerialNumber());

                    txtQuantity.setText(String.valueOf(item.getQuantity()));
                    txtUnitCost.setText("\u20b1" + item.getUnitCost());
                    txtTotalValue.setText("\u20b1" + item.getTotalValue());

                    txtLocation.setText(item.getLocation());
                    txtDateAdded.setText(formatDateAdded(item.getDateAdded()));
                    txtStatus.setText(item.getStatus());
                    styleStatus(item.getStatus());

                    ItemRemarkIssue remarkIssue = item.getRemarkIssue();
                    if (remarkIssue == null) {
                        issueSection.setVisibility(View.GONE);
                    } else {
                        issueSection.setVisibility(View.VISIBLE);
                        txtIssueCode.setText(remarkIssue.getIssueCode());
                        String remarks = remarkIssue.getRemarks();
                        txtRemarks.setText(remarks == null || remarks.trim().isEmpty()
                                ? "No remarks" : remarks);
                    }

                    String imageUrl = RetrofitClient.resolveImageUrl(item.getImage());

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Glide.with(ItemDetailsActivity.this)
                                .load(AuthenticatedImageUrl.from(ItemDetailsActivity.this, imageUrl))
                                .placeholder(R.drawable.img_placeholder)
                                .error(R.drawable.img_placeholder)
                                .into(imgItem);
                    } else {
                        imgItem.setImageResource(R.drawable.img_placeholder);
                    }
                }
            }

            @Override
            public void onFailure(Call<ItemModel> call, Throwable t) {
                Toast.makeText(ItemDetailsActivity.this,
                        t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String formatDateAdded(String dateAdded) {
        if (dateAdded == null || dateAdded.trim().isEmpty()) {
            return dateAdded;
        }

        String value = dateAdded.trim();
        if (value.length() < 16) {
            return value;
        }

        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        inputFormat.setLenient(false);

        try {
            String timestamp = value.substring(0, 16).replace('T', ' ');
            Date parsedDate = inputFormat.parse(timestamp);
            return new SimpleDateFormat("yyyy-MM-dd - hh:mm a", Locale.US).format(parsedDate);
        } catch (ParseException e) {
            return value;
        }
    }

    private void styleStatus(String status) {
        String normalized = status != null ? status.trim().toLowerCase() : "";

        if (normalized.contains("out")
                || normalized.contains("disposal")
                || normalized.contains("disposed")) {
            txtStatus.setBackgroundResource(R.drawable.bg_low_stock);
            txtStatus.setTextColor(getColor(R.color.status_red_dark));
        } else if (normalized.contains("low")
                || normalized.contains("reserved")
                || normalized.contains("pending")) {
            txtStatus.setBackgroundResource(R.drawable.bg_status_pending);
            txtStatus.setTextColor(getColor(R.color.status_orange_dark));
        } else if (normalized.isEmpty()) {
            txtStatus.setBackgroundResource(R.drawable.bg_no_stock);
            txtStatus.setTextColor(getColor(R.color.status_gray_dark));
        } else {
            txtStatus.setBackgroundResource(R.drawable.bg_in_stock);
            txtStatus.setTextColor(getColor(R.color.status_green_dark));
        }
        txtStatus.setTypeface(txtStatus.getTypeface(), android.graphics.Typeface.BOLD);
    }
}
