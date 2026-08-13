package com.inventorysystem;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.inventorysystem.ConnectivityandService.AuthenticatedImageUrl;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.inventorysystem.ConnectivityandService.ApiService;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.CategoryModel;
import com.inventorysystem.Model.ItemModel;
import com.inventorysystem.Model.ItemResponse;
import com.inventorysystem.Model.LocationModel;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditItemActivity extends AppCompatActivity {
    private static final long MAX_IMAGE_SIZE = 5L * 1024L * 1024L;

    private final List<CategoryModel> categories = new ArrayList<>();
    private final List<LocationModel> locations = new ArrayList<>();
    private TextInputEditText etItemCode;
    private TextInputEditText etItemName;
    private TextInputEditText etBrand;
    private TextInputEditText etModel;
    private TextInputEditText etSerialNumber;
    private TextInputEditText etQuantity;
    private TextInputEditText etReorderLevel;
    private TextInputEditText etUnitCost;
    private TextInputEditText etRemarks;
    private TextInputEditText etCustomCategory;
    private TextInputLayout customCategoryLayout;
    private Spinner spinnerCategory;
    private Spinner spinnerLocation;
    private ImageView imgItemPhoto;
    private Button btnSaveChanges;
    private SessionManager sessionManager;
    private ItemModel item;
    private Uri selectedImageUri;
    private int itemId;
    private String initialRemarks = "";

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    Glide.with(this).load(uri).fitCenter().into(imgItemPhoto);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_item);

        sessionManager = new SessionManager(this);
        String role = sessionManager.getRole();
        if (!"IT".equals(role) && !"Admin IT".equals(role)) {
            Toast.makeText(this, "Only IT users can edit items", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        itemId = getIntent().getIntExtra("ITEM_ID", -1);
        if (itemId == -1 || TextUtils.isEmpty(sessionManager.getToken())) {
            Toast.makeText(this, "Unable to edit this item", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        bindViews();
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        findViewById(R.id.btnChooseImage).setOnClickListener(v ->
                pickImageLauncher.launch("image/*"));
        btnSaveChanges.setOnClickListener(v -> saveChanges());

        loadCategories();
        loadLocations();
        loadItem();
    }

    private void bindViews() {
        etItemCode = findViewById(R.id.etItemCode);
        etItemName = findViewById(R.id.etItemName);
        etBrand = findViewById(R.id.etBrand);
        etModel = findViewById(R.id.etModel);
        etSerialNumber = findViewById(R.id.etSerialNumber);
        etQuantity = findViewById(R.id.etQuantity);
        etReorderLevel = findViewById(R.id.etReorderLevel);
        etUnitCost = findViewById(R.id.etUnitCost);
        etRemarks = findViewById(R.id.etRemarks);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        spinnerLocation = findViewById(R.id.spinnerLocation);
        imgItemPhoto = findViewById(R.id.imgItemPhoto);
        btnSaveChanges = findViewById(R.id.btnSaveChanges);
        etCustomCategory = findViewById(R.id.etCustomCategory);
        customCategoryLayout = findViewById(R.id.customCategoryLayout);
        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                customCategoryLayout.setVisibility(position == categories.size()
                        ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        btnSaveChanges.setEnabled(false);
    }

    private String authorization() {
        return "Bearer " + sessionManager.getToken();
    }

    private void loadItem() {
        RetrofitClient.getApiService().getItemDetails(authorization(), itemId)
                .enqueue(new Callback<ItemModel>() {
                    @Override
                    public void onResponse(Call<ItemModel> call, Response<ItemModel> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            item = response.body();
                            populateForm();
                            updateSaveAvailability();
                        } else {
                            showLoadError(response, "item");
                        }
                    }

                    @Override
                    public void onFailure(Call<ItemModel> call, Throwable t) {
                        Toast.makeText(EditItemActivity.this,
                                "Failed to load item: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void populateForm() {
        etItemCode.setText(item.getItemCode());
        etItemName.setText(item.getItemName());
        etBrand.setText(item.getBrand());
        etModel.setText(item.getModel());
        etSerialNumber.setText(item.getSerialNumber());
        etQuantity.setText(String.valueOf(item.getQuantity()));
        etReorderLevel.setText(String.valueOf(item.getReorderLevel()));
        etUnitCost.setText(String.valueOf(item.getUnitCost()));
        initialRemarks = item.getRemarkIssue() == null
                || item.getRemarkIssue().getRemarks() == null
                ? "" : item.getRemarkIssue().getRemarks().trim();
        etRemarks.setText(initialRemarks);

        String imageUrl = RetrofitClient.resolveImageUrl(item.getImage());
        Glide.with(this).load(AuthenticatedImageUrl.from(this, imageUrl)).placeholder(R.drawable.img_placeholder)
                .error(R.drawable.img_placeholder).fitCenter().into(imgItemPhoto);
        selectCurrentLookups();
    }

    private void loadCategories() {
        RetrofitClient.getApiService().getCategories(authorization())
                .enqueue(new Callback<List<CategoryModel>>() {
                    @Override
                    public void onResponse(Call<List<CategoryModel>> call,
                                           Response<List<CategoryModel>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            categories.clear();
                            categories.addAll(response.body());
                            List<String> names = new ArrayList<>();
                            for (CategoryModel category : categories) {
                                names.add(category.getCategoryName());
                            }
                            names.add("Others");
                            spinnerCategory.setAdapter(new ArrayAdapter<>(EditItemActivity.this,
                                    android.R.layout.simple_spinner_dropdown_item, names));
                            selectCurrentLookups();
                            updateSaveAvailability();
                        } else {
                            showLoadError(response, "categories");
                        }
                    }

                    @Override
                    public void onFailure(Call<List<CategoryModel>> call, Throwable t) {
                        Toast.makeText(EditItemActivity.this,
                                "Failed to load categories: " + t.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loadLocations() {
        RetrofitClient.getApiService().getLocations(authorization())
                .enqueue(new Callback<List<LocationModel>>() {
                    @Override
                    public void onResponse(Call<List<LocationModel>> call,
                                           Response<List<LocationModel>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            locations.clear();
                            locations.addAll(response.body());
                            List<String> names = new ArrayList<>();
                            for (LocationModel location : locations) {
                                names.add(location.getLocationName());
                            }
                            spinnerLocation.setAdapter(new ArrayAdapter<>(EditItemActivity.this,
                                    android.R.layout.simple_spinner_dropdown_item, names));
                            selectCurrentLookups();
                            updateSaveAvailability();
                        } else {
                            showLoadError(response, "locations");
                        }
                    }

                    @Override
                    public void onFailure(Call<List<LocationModel>> call, Throwable t) {
                        Toast.makeText(EditItemActivity.this,
                                "Failed to load locations: " + t.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void selectCurrentLookups() {
        if (item == null) return;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getCategoryId() == item.getCategoryId()) {
                spinnerCategory.setSelection(i);
                break;
            }
        }
        for (int i = 0; i < locations.size(); i++) {
            if (locations.get(i).getLocationId() == item.getLocationId()) {
                spinnerLocation.setSelection(i);
                break;
            }
        }
    }

    private void updateSaveAvailability() {
        btnSaveChanges.setEnabled(item != null && !categories.isEmpty() && !locations.isEmpty());
    }

    private void saveChanges() {
        if (!validateForm()) return;

        btnSaveChanges.setEnabled(false);
        btnSaveChanges.setText("Saving...");

        MultipartBody.Part imagePart;
        try {
            imagePart = createImagePart();
        } catch (IOException e) {
            restoreSaveButton();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        boolean customCategory = spinnerCategory.getSelectedItemPosition() == categories.size();
        String categoryId = customCategory ? ""
                : String.valueOf(categories.get(
                        spinnerCategory.getSelectedItemPosition()).getCategoryId());
        String categoryName = customCategory ? value(etCustomCategory) : "";
        LocationModel location = locations.get(spinnerLocation.getSelectedItemPosition());
        String remarks = value(etRemarks);
        RequestBody remarksPart = remarks.equals(initialRemarks) ? null : textPart(remarks);
        ApiService api = RetrofitClient.getApiService();
        api.updateItem(
                authorization(),
                itemId,
                textPart(value(etItemCode)),
                textPart(value(etItemName)),
                textPart(value(etBrand)),
                textPart(value(etModel)),
                textPart(value(etSerialNumber)),
                textPart(categoryId),
                textPart(categoryName),
                textPart(String.valueOf(location.getLocationId())),
                textPart(value(etQuantity)),
                textPart(value(etReorderLevel)),
                textPart(value(etUnitCost)),
                remarksPart,
                imagePart
        ).enqueue(new Callback<ItemResponse>() {
            @Override
            public void onResponse(Call<ItemResponse> call, Response<ItemResponse> response) {
                if (response.isSuccessful()) {
                    String message = response.body() != null
                            && !TextUtils.isEmpty(response.body().getMessage())
                            ? response.body().getMessage() : "Item updated successfully";
                    Toast.makeText(EditItemActivity.this, message, Toast.LENGTH_LONG).show();
                    Intent changed = new Intent(ItemsActivity.ACTION_INVENTORY_DATA_CHANGED);
                    changed.setPackage(getPackageName());
                    sendBroadcast(changed);
                    setResult(Activity.RESULT_OK);
                    finish();
                } else {
                    restoreSaveButton();
                    Toast.makeText(EditItemActivity.this, backendError(response),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ItemResponse> call, Throwable t) {
                restoreSaveButton();
                Toast.makeText(EditItemActivity.this,
                        "Update failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean validateForm() {
        if (value(etRemarks).length() > 500) {
            etRemarks.setError("Remarks must not exceed 500 characters.");
            etRemarks.requestFocus();
            return false;
        }
        TextInputEditText[] required = {etItemCode, etItemName, etBrand, etModel,
                etSerialNumber, etQuantity, etReorderLevel, etUnitCost};
        for (TextInputEditText field : required) {
            if (TextUtils.isEmpty(value(field))) {
                field.setError("Required");
                field.requestFocus();
                return false;
            }
        }
        if (spinnerCategory.getSelectedItemPosition() == categories.size()
                && TextUtils.isEmpty(value(etCustomCategory))) {
            etCustomCategory.setError("Specify the category");
            etCustomCategory.requestFocus();
            return false;
        }
        try {
            if (Integer.parseInt(value(etQuantity)) < 0
                    || Integer.parseInt(value(etReorderLevel)) < 0
                    || Double.parseDouble(value(etUnitCost)) < 0) {
                Toast.makeText(this, "Numeric values cannot be negative", Toast.LENGTH_LONG).show();
                return false;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Enter valid numeric values", Toast.LENGTH_LONG).show();
            return false;
        }
        return spinnerCategory.getSelectedItemPosition() >= 0
                && spinnerLocation.getSelectedItemPosition() >= 0;
    }

    private MultipartBody.Part createImagePart() throws IOException {
        if (selectedImageUri == null) return null;
        String mime = getContentResolver().getType(selectedImageUri);
        if (mime == null || !mime.startsWith("image/")) {
            throw new IOException("Please select a valid image");
        }
        byte[] bytes;
        try (InputStream stream = getContentResolver().openInputStream(selectedImageUri)) {
            if (stream == null) throw new IOException("Unable to read selected image");
            bytes = readImage(stream);
        }
        RequestBody body = RequestBody.create(bytes, MediaType.parse(mime));
        return MultipartBody.Part.createFormData("image", "item-image", body);
    }

    private byte[] readImage(InputStream stream) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = stream.read(buffer)) != -1) {
            total += count;
            if (total > MAX_IMAGE_SIZE) throw new IOException("Image must be smaller than 5 MB");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private RequestBody textPart(String value) {
        return RequestBody.create(value == null ? "" : value, MediaType.parse("text/plain"));
    }

    private String value(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private void restoreSaveButton() {
        btnSaveChanges.setEnabled(true);
        btnSaveChanges.setText("Save Changes");
    }

    private void showLoadError(Response<?> response, String resource) {
        Toast.makeText(this, "Failed to load " + resource + ": " + backendError(response),
                Toast.LENGTH_LONG).show();
    }

    private String backendError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String body = response.errorBody().string();
                JSONObject json = new JSONObject(body);
                String message = json.optString("message");
                if (TextUtils.isEmpty(message)) message = json.optString("error");
                if (!TextUtils.isEmpty(message)) return message;
                if (!TextUtils.isEmpty(body)) return body;
            }
        } catch (Exception ignored) {
            // Fall through to the HTTP status when the body is not JSON.
        }
        return "Request failed (" + response.code() + ")";
    }
}
