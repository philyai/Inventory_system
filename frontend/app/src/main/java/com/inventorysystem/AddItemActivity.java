package com.inventorysystem;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.inventorysystem.ConnectivityandService.ApiService;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.CategoryModel;
import com.inventorysystem.Model.LocationModel;
import com.inventorysystem.Model.ItemRequest;
import com.inventorysystem.Model.ItemResponse;
import com.inventorysystem.offline.CachedCategoryEntity;
import com.inventorysystem.offline.CachedLocationEntity;
import com.inventorysystem.offline.InventoryDatabase;
import com.inventorysystem.offline.OfflineItemSyncScheduler;
import com.inventorysystem.offline.PendingImageStore;
import com.inventorysystem.offline.PendingItemEntity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddItemActivity extends AppCompatActivity {
    private String token;
    private SessionManager sessionManager;
    private static final String TAG = "AddItem";

    private ImageView icBack;
    private MaterialCardView cardSaveItem;

    private TextView txtCategory;
    private TextView txtLocation;
    private TextView txtItemName;
    private TextView txtBrand;
    private TextView txtModel;
    private TextView txtSerial;
    private TextView txtQuantity;
    private TextView txtUnitCost;
    private TextView txtReorderLevel;
    private TextInputLayout customCategorySection;
    private EditText etCustomCategory;
    private EditText etRemarks;

    // New: image picker views + state
    private ImageView imgItemPhoto;
    private TextView txtAddPhoto;
    private Uri selectedImageUri;
    private Uri pendingCameraUri;

    private static final long MAX_IMAGE_SIZE = 5L * 1024L * 1024L;

    private List<CategoryModel> categoryList = new ArrayList<>();
    private int selectedCategoryId = -1;
    private boolean customCategorySelected;

    private List<LocationModel> locationList = new ArrayList<>();
    private int selectedLocationId = -1;

    // New: registers the gallery picker and handles the result
    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) acceptImage(uri);
            });

    private final ActivityResultLauncher<Uri> takePhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && pendingCameraUri != null) {
                    acceptImage(pendingCameraUri);
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    openCamera();
                } else {
                    showPermissionSettings();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_additem);

        sessionManager = new SessionManager(this);
        token = sessionManager.getToken();
        if ("Purchasing".equals(sessionManager.getRole())) {
            Toast.makeText(this, "Purchasing users cannot add items", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        icBack = findViewById(R.id.ic_Back);
        cardSaveItem = findViewById(R.id.cardSaveItem);

        txtCategory = findViewById(R.id.txtCategory);
        txtLocation = findViewById(R.id.txtlocation);
        txtItemName = findViewById(R.id.txtItemName);
        txtBrand = findViewById(R.id.txtBrand);
        txtModel = findViewById(R.id.txtModel);
        txtSerial = findViewById(R.id.txtSerial);
        txtQuantity = findViewById(R.id.txtQuantity);
        txtUnitCost = findViewById(R.id.txtUnitCost);
        txtReorderLevel = findViewById(R.id.txtReorderLevel);
        customCategorySection = findViewById(R.id.customCategorySection);
        etCustomCategory = findViewById(R.id.etCustomCategory);
        etRemarks = findViewById(R.id.etRemarks);

        // New: bind the photo picker views
        imgItemPhoto = findViewById(R.id.imgItemPhoto);
        txtAddPhoto = findViewById(R.id.txtAddPhoto);

        icBack.setOnClickListener(v -> finish());

        txtCategory.setOnClickListener(v -> showCategoryPicker());
        txtLocation.setOnClickListener(v -> showLocationPicker());
        txtItemName.setOnClickListener(v -> showTextInputDialog("Item Name", txtItemName, InputType.TYPE_CLASS_TEXT));
        txtBrand.setOnClickListener(v -> showTextInputDialog("Brand", txtBrand, InputType.TYPE_CLASS_TEXT));
        txtModel.setOnClickListener(v -> showTextInputDialog("Model", txtModel, InputType.TYPE_CLASS_TEXT));
        txtSerial.setOnClickListener(v -> showTextInputDialog("Serial Number / Code", txtSerial, InputType.TYPE_CLASS_TEXT));
        txtQuantity.setOnClickListener(v -> showTextInputDialog("Quantity", txtQuantity, InputType.TYPE_CLASS_NUMBER));
        txtUnitCost.setOnClickListener(v -> showTextInputDialog("Unit Cost", txtUnitCost,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL));
        txtReorderLevel.setOnClickListener(v -> showTextInputDialog(
                "Reorder Level", txtReorderLevel, InputType.TYPE_CLASS_NUMBER));

        imgItemPhoto.setOnClickListener(v -> showPhotoOptions());
        txtAddPhoto.setOnClickListener(v -> showPhotoOptions());

        cardSaveItem.setOnClickListener(v -> saveItem());
        cardSaveItem.setEnabled(false);
        loadCachedLookups();

        if (token != null) {
            loadCategories();
            loadLocations();
        } else {
            Toast.makeText(this, "Missing auth token", Toast.LENGTH_LONG).show();
            Log.e(TAG, "No token passed to AddItemActivity");
        }
    }

    private void showPhotoOptions() {
        String[] options = {"Take Photo", "Choose from Gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Add Item Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        requestCamera();
                    } else {
                        pickImageLauncher.launch("image/*");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        try {
            File cameraDirectory = new File(getCacheDir(), "camera");
            if (!cameraDirectory.exists() && !cameraDirectory.mkdirs()) {
                throw new IOException("Could not create camera directory");
            }

            File photo = File.createTempFile("item_", ".jpg", cameraDirectory);
            pendingCameraUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    photo
            );
            takePhotoLauncher.launch(pendingCameraUri);
        } catch (IOException e) {
            Log.e(TAG, "Unable to prepare camera photo", e);
            Toast.makeText(this, "Unable to open the camera", Toast.LENGTH_LONG).show();
        }
    }

    private void showPermissionSettings() {
        new AlertDialog.Builder(this)
                .setTitle("Camera permission required")
                .setMessage("Enable camera access in device settings to take an item photo.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .show();
    }

    private void acceptImage(Uri uri) {
        String mime = getContentResolver().getType(uri);
        if (mime == null && uri.equals(pendingCameraUri)) {
            mime = "image/jpeg";
        }

        if (!"image/jpeg".equalsIgnoreCase(mime)
                && !"image/png".equalsIgnoreCase(mime)
                && !"image/webp".equalsIgnoreCase(mime)) {
            Toast.makeText(this, "Only JPG, PNG, or WebP images are allowed",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try (android.content.res.AssetFileDescriptor descriptor =
                     getContentResolver().openAssetFileDescriptor(uri, "r")) {
            long size = descriptor != null ? descriptor.getLength() : -1;
            if (size > MAX_IMAGE_SIZE) {
                Toast.makeText(this, "Please select an image smaller than 5 MB",
                        Toast.LENGTH_LONG).show();
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to validate selected image", e);
            Toast.makeText(this, "Unable to read the selected image",
                    Toast.LENGTH_LONG).show();
            return;
        }

        selectedImageUri = uri;
        txtAddPhoto.setText("Change Photo");
        Glide.with(this)
                .load(uri)
                .fitCenter()
                .into(imgItemPhoto);
    }

    private void loadCategories() {
        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);

        apiService.getCategories("Bearer " + token)
                .enqueue(new Callback<List<CategoryModel>>() {
                    @Override
                    public void onResponse(Call<List<CategoryModel>> call,
                                           Response<List<CategoryModel>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            categoryList = response.body();
                            cacheCategories(categoryList);
                            updateSaveAvailability();
                        } else {
                            Toast.makeText(AddItemActivity.this,
                                    "Failed to load categories: " + response.code(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<CategoryModel>> call, Throwable t) {
                        Toast.makeText(AddItemActivity.this,
                                "Connection failed: " + t.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loadLocations() {
        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);

        apiService.getLocations("Bearer " + token)
                .enqueue(new Callback<List<LocationModel>>() {
                    @Override
                    public void onResponse(Call<List<LocationModel>> call,
                                           Response<List<LocationModel>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            locationList = response.body();
                            cacheLocations(locationList);
                            updateSaveAvailability();
                        } else {
                            Toast.makeText(AddItemActivity.this,
                                    "Failed to load locations: " + response.code(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<LocationModel>> call, Throwable t) {
                        Toast.makeText(AddItemActivity.this,
                                "Connection failed: " + t.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loadCachedLookups() {
        InventoryDatabase.IO.execute(() -> {
            List<CachedCategoryEntity> categories = InventoryDatabase.get(this).offlineDao().getCategories();
            List<CachedLocationEntity> locations = InventoryDatabase.get(this).offlineDao().getLocations();
            List<CategoryModel> cachedCategories = new ArrayList<>();
            for (CachedCategoryEntity item : categories) cachedCategories.add(new CategoryModel(item.categoryId, item.categoryName));
            List<LocationModel> cachedLocations = new ArrayList<>();
            for (CachedLocationEntity item : locations) cachedLocations.add(new LocationModel(item.locationId, item.locationName));
            runOnUiThread(() -> {
                if (!cachedCategories.isEmpty()) categoryList = cachedCategories;
                if (!cachedLocations.isEmpty()) locationList = cachedLocations;
                updateSaveAvailability();
            });
        });
    }

    private void updateSaveAvailability() {
        boolean available = !categoryList.isEmpty() && !locationList.isEmpty();
        cardSaveItem.setEnabled(available);
        cardSaveItem.setClickable(available);
        cardSaveItem.setAlpha(available ? 1f : 0.55f);
        if (!available) Toast.makeText(this, "Connect to the internet once to load categories and locations.", Toast.LENGTH_LONG).show();
    }
    private void cacheCategories(List<CategoryModel> values) {
        InventoryDatabase.IO.execute(() -> {
            List<CachedCategoryEntity> cached = new ArrayList<>();
            for (CategoryModel value : values) cached.add(new CachedCategoryEntity(value.getCategoryId(), value.getCategoryName()));
            InventoryDatabase.get(this).offlineDao().replaceCategories(cached);
        });
    }

    private void cacheLocations(List<LocationModel> values) {
        InventoryDatabase.IO.execute(() -> {
            List<CachedLocationEntity> cached = new ArrayList<>();
            for (LocationModel value : values) cached.add(new CachedLocationEntity(value.getLocationId(), value.getLocationName()));
            InventoryDatabase.get(this).offlineDao().replaceLocations(cached);
        });
    }
    private void showCategoryPicker() {
        if (categoryList.isEmpty()) {
            Toast.makeText(this, "Categories still loading, please wait", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[categoryList.size() + 1];
        for (int i = 0; i < categoryList.size(); i++) {
            names[i] = categoryList.get(i).getCategoryName();
        }
        names[categoryList.size()] = "Others";

        AlertDialog categoryDialog = new AlertDialog.Builder(this)
                .setTitle("Select Category")
                .setItems(names, (dialog, which) -> {
                    if (which == categoryList.size()) {
                        customCategorySelected = true;
                        selectedCategoryId = -1;
                        txtCategory.setText("Others");
                        customCategorySection.setVisibility(View.VISIBLE);
                        etCustomCategory.requestFocus();
                    } else {
                        CategoryModel selected = categoryList.get(which);
                        customCategorySelected = false;
                        selectedCategoryId = selected.getCategoryId();
                        txtCategory.setText(selected.getCategoryName());
                        etCustomCategory.setText("");
                        customCategorySection.setVisibility(View.GONE);
                    }
                })
                .create();

        categoryDialog.setOnShowListener(dialog -> {
            ListView categoryListView = categoryDialog.getListView();
            if (categoryListView != null) {
                categoryListView.setVerticalScrollBarEnabled(true);
                categoryListView.setScrollbarFadingEnabled(false);
                categoryListView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            }
        });
        categoryDialog.show();
    }

    private void showLocationPicker() {
        if (locationList.isEmpty()) {
            Toast.makeText(this, "Locations still loading, please wait", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[locationList.size()];
        for (int i = 0; i < locationList.size(); i++) {
            names[i] = locationList.get(i).getLocationName();
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Location")
                .setItems(names, (dialog, which) -> {
                    LocationModel selected = locationList.get(which);
                    selectedLocationId = selected.getLocationId();
                    txtLocation.setText(selected.getLocationName());
                })
                .show();
    }

    private void showTextInputDialog(String label, TextView targetView, int inputType) {
        EditText input = new EditText(this);
        input.setInputType(inputType);

        String current = targetView.getText().toString();
        if (!current.equals("0") && !current.equals("0.00")
                && !current.toLowerCase().startsWith("enter")) {
            input.setText(current);
        }

        new AlertDialog.Builder(this)
                .setTitle(label)
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        targetView.setText(value);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveItem() {
        String itemName = txtItemName.getText().toString().trim();
        String brand = txtBrand.getText().toString().trim();
        String model = txtModel.getText().toString().trim();
        String serial = txtSerial.getText().toString().trim();
        String quantityText = txtQuantity.getText().toString().trim();
        String unitCostText = txtUnitCost.getText().toString().trim();
        String reorderLevelText = txtReorderLevel.getText().toString().trim();
        String remarks = etRemarks.getText() == null
                ? "" : etRemarks.getText().toString().trim();

        if (remarks.length() > 500) {
            etRemarks.setError("Remarks must not exceed 500 characters.");
            etRemarks.requestFocus();
            return;
        }

        if (!customCategorySelected && selectedCategoryId == -1) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }

        String customCategoryName = etCustomCategory.getText().toString().trim();
        if (customCategorySelected && customCategoryName.isEmpty()) {
            etCustomCategory.setError("Please enter a specific category");
            etCustomCategory.requestFocus();
            return;
        }

        if (selectedLocationId == -1) {
            Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show();
            return;
        }

        if (itemName.isEmpty() || itemName.equalsIgnoreCase("Enter item name")) {
            Toast.makeText(this, "Please enter item name", Toast.LENGTH_SHORT).show();
            return;
        }

        int quantity;
        int reorderLevel;
        double unitCost;

        try {
            quantity = Integer.parseInt(quantityText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid quantity", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            unitCost = Double.parseDouble(unitCostText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid unit cost", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            reorderLevel = Integer.parseInt(reorderLevelText);
            if (reorderLevel < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid reorder level", Toast.LENGTH_SHORT).show();
            return;
        }

        int ownerUserId = sessionManager.getUserId();
        if (ownerUserId <= 0) {
            Toast.makeText(this, "Sign in before adding an item", Toast.LENGTH_LONG).show();
            return;
        }

        String clientRequestId = UUID.randomUUID().toString();
        PendingItemEntity pending = new PendingItemEntity();
        pending.localId = UUID.randomUUID().toString();
        pending.clientRequestId = clientRequestId;
        pending.ownerUserId = ownerUserId;
        pending.itemName = itemName;
        pending.brand = brand.isEmpty() ? null : brand;
        pending.model = model.isEmpty() ? null : model;
        pending.serialNumber = serial.isEmpty() ? null : serial;
        pending.categoryId = customCategorySelected ? null : selectedCategoryId;
        pending.categoryName = customCategorySelected ? customCategoryName : null;
        pending.locationId = selectedLocationId;
        pending.quantity = quantity;
        pending.reorderLevel = reorderLevel;
        pending.unitCost = unitCostText;
        pending.remarks = remarks.isEmpty() ? null : remarks;
        pending.syncStatus = "PENDING";
        pending.createdAt = System.currentTimeMillis();

        setSaving(true);
        InventoryDatabase.IO.execute(() -> {
            try {
                InventoryDatabase database = InventoryDatabase.get(getApplicationContext());
                database.offlineDao().insertPending(pending);
                if (selectedImageUri != null) {
                    PendingImageStore.StoredImage stored = PendingImageStore.copy(
                            getApplicationContext(), selectedImageUri, clientRequestId);
                    pending.localImagePath = stored.path;
                    pending.imageMimeType = stored.mimeType;
                    database.offlineDao().updatePending(pending);
                }
                OfflineItemSyncScheduler.enqueue(getApplicationContext());
                runOnUiThread(() -> {
                    setSaving(false);
                    Toast.makeText(this, "Item saved. It will sync when a connection is available.", Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception exception) {
                InventoryDatabase.get(getApplicationContext()).offlineDao().deleteById(pending.localId, ownerUserId);
                PendingImageStore.delete(pending.localImagePath);
                Log.e(TAG, "Unable to queue item", exception);
                runOnUiThread(() -> {
                    setSaving(false);
                    Toast.makeText(this, "Unable to save item locally: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private RequestBody textPart(String value) {
        return RequestBody.create(value, MediaType.parse("text/plain"));
    }

    private RequestBody optionalTextPart(String value) {
        return value == null || value.trim().isEmpty() ? null : textPart(value.trim());
    }

    private void resetForm() {
        selectedCategoryId = -1;
        customCategorySelected = false;
        selectedLocationId = -1;
        selectedImageUri = null;
        pendingCameraUri = null;

        txtCategory.setText(R.string.categories);
        txtLocation.setText(R.string.locations);
        txtItemName.setText(R.string.ItemName);
        txtBrand.setText(R.string.Brand);
        txtModel.setText(R.string.Model);
        txtSerial.setText(R.string.Code);
        txtQuantity.setText(R.string.placeholder_quantity);
        txtUnitCost.setText(R.string.placeholder_cost);
        txtReorderLevel.setText(R.string.placeholder_quantity);
        etCustomCategory.setText("");
        etRemarks.setText("");
        customCategorySection.setVisibility(View.GONE);
        txtAddPhoto.setText(R.string.add_photo);
        imgItemPhoto.setImageResource(R.drawable.img_placeholder);
    }

    private void setSaving(boolean saving) {
        cardSaveItem.setEnabled(!saving);
        cardSaveItem.setClickable(!saving);
        cardSaveItem.setAlpha(saving ? 0.55f : 1f);
    }

    // New: uploads the picked photo for the item that was just created
    private void uploadItemImage(int itemId, Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            byte[] bytes = readAllBytes(inputStream);

            String mime = getContentResolver().getType(imageUri);
            if (mime == null) mime = "image/jpeg";

            RequestBody requestFile = RequestBody.create(bytes, MediaType.parse(mime));
            MultipartBody.Part imagePart = MultipartBody.Part.createFormData(
                    "image", "item_" + itemId + ".jpg", requestFile);

            ApiService apiService = RetrofitClient.getClient().create(ApiService.class);

            apiService.uploadItemImage("Bearer " + token, itemId, imagePart)
                    .enqueue(new Callback<ItemResponse>() {
                        @Override
                        public void onResponse(Call<ItemResponse> call, Response<ItemResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                Toast.makeText(AddItemActivity.this,
                                        "Item saved successfully", Toast.LENGTH_SHORT).show();
                            } else {
                                try {
                                    Log.e(TAG, "Image upload failed: " + response.code() +
                                            (response.errorBody() != null ? " " + response.errorBody().string() : ""));
                                } catch (IOException e) {
                                    Log.e(TAG, "Error reading errorBody", e);
                                }
                                Toast.makeText(AddItemActivity.this,
                                        "Item saved, but photo upload failed: " + response.code(),
                                        Toast.LENGTH_LONG).show();
                            }
                            setResult(RESULT_OK);
                            finish();
                        }

                        @Override
                        public void onFailure(Call<ItemResponse> call, Throwable t) {
                            Toast.makeText(AddItemActivity.this,
                                    "Item saved, but photo upload failed: " + t.getMessage(),
                                    Toast.LENGTH_LONG).show();
                            setResult(RESULT_OK);
                            finish();
                        }
                    });

        } catch (IOException e) {
            Log.e(TAG, "Failed to read image file", e);
            Toast.makeText(this, "Item saved, but reading the photo failed", Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}
