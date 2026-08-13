package com.inventorysystem.offline;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.inventorysystem.ConnectivityandService.ApiService;
import com.inventorysystem.ConnectivityandService.BackendServerSelector;
import com.inventorysystem.ConnectivityandService.RetrofitClient;
import com.inventorysystem.Model.ItemRemarkIssue;
import com.inventorysystem.Model.ItemResponse;
import com.inventorysystem.Model.OfflineItemRequest;
import com.inventorysystem.SessionManager;
import java.io.File;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Response;

public class OfflineItemSyncWorker extends Worker {
    private static final String TAG = "OfflineSync";
    private static final int MAX_TRANSIENT_RETRIES = 10;

    public OfflineItemSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    @NonNull @Override public Result doWork() {
        Log.d(TAG, "Worker started; attempt=" + getRunAttemptCount());
        SessionManager session = new SessionManager(getApplicationContext());
        String token = session.getToken();
        int userId = session.getUserId();
        Log.d(TAG, "Current user ID=" + userId + ", token available=" + (token != null && !token.isEmpty()));
        if (token == null || token.isEmpty() || userId <= 0) {
            Log.d(TAG, "Worker result=success; synchronization paused until authenticated login");
            return Result.success();
        }

        OfflineDao dao = InventoryDatabase.get(getApplicationContext()).offlineDao();
        dao.resetAbandonedSyncing(userId);
        List<PendingItemEntity> items = dao.getSyncableItems(userId);
        Log.d(TAG, "Pending record count=" + items.size());
        if (items.isEmpty()) {
            Log.d(TAG, "Worker result=success; queue empty");
            return Result.success();
        }

        long now = System.currentTimeMillis();
        boolean hasReadyItem = false;
        for (PendingItemEntity item : items) {
            if (item.retryAfterAt == null || item.retryAfterAt <= now) {
                hasReadyItem = true;
                break;
            }
        }
        if (!hasReadyItem) {
            Log.d(TAG, "Worker result=retry; all pending items are waiting for Retry-After");
            return Result.retry();
        }

        String backendUrl = configureBackend();
        if (backendUrl == null) {
            for (PendingItemEntity item : items) {
                item.syncStatus = "PENDING";
                item.lastError = "No configured backend is currently reachable";
                dao.updatePending(item);
            }
            Log.d(TAG, "Worker result=retry; backend selection failed");
            return Result.retry();
        }
        Log.d(TAG, "Selected backend URL=" + backendUrl);

        boolean delayedItemRemaining = false;
        boolean retryNeeded = false;
        for (PendingItemEntity item : items) {
            if (item.retryAfterAt != null && item.retryAfterAt > System.currentTimeMillis()) {
                delayedItemRemaining = true;
                continue;
            }
            Log.d(TAG, "Processing local record=" + item.localId + ", stored status=" + item.syncStatus);
            if (item.localImagePath != null) {
                File image = new File(item.localImagePath);
                Log.d(TAG, "Image exists=" + image.exists() + ", readable=" + image.canRead() + ", size=" + image.length());
                if (!image.isFile() || !image.canRead()) {
                    item.syncStatus = "FAILED";
                    item.lastError = "The locally copied image is missing or unreadable";
                    dao.updatePending(item);
                    Log.d(TAG, "Final Room status=FAILED; missing image");
                    continue;
                }
            }

            item.syncStatus = "SYNCING";
            item.retryAfterAt = null;
            item.lastAttemptAt = System.currentTimeMillis();
            dao.updatePending(item);
            Log.d(TAG, "POST /items started for local record=" + item.localId);
            try {
                Response<ItemResponse> response = createCall(item, token).execute();
                int code = response.code();
                Log.d(TAG, "HTTP response code=" + code);
                if (code == 200 || code == 201) {
                    if (response.body() == null) {
                        retryNeeded |= recordTransientFailure(
                                dao, item, "Server returned an empty item response");
                        continue;
                    }
                    persistSuccessfulSync(dao, item, response.body());
                } else if (code == 400 || code == 403) {
                    item.syncStatus = "FAILED";
                    item.lastError = errorMessage(response, "Server rejected item (" + code + ")");
                    dao.updatePending(item);
                    Log.e(TAG, "Backend error=" + item.lastError);
                    Log.d(TAG, "Final Room status=FAILED");
                } else if (code == 401) {
                    item.syncStatus = "PENDING";
                    item.lastError = "Sign in again to synchronize";
                    dao.updatePending(item);
                    Log.d(TAG, "Final Room status=PENDING; worker result=success after 401");
                    return Result.success();
                } else if (code == 429) {
                    item.retryCount++;
                    item.retryAfterAt = retryAfterAt(response);
                    item.lastError = errorMessage(response, "Too many requests. Synchronization will retry.");
                    item.syncStatus = item.retryCount >= MAX_TRANSIENT_RETRIES ? "FAILED" : "PENDING";
                    dao.updatePending(item);
                    retryNeeded |= "PENDING".equals(item.syncStatus);
                    Log.d(TAG, "Final Room status=" + item.syncStatus + "; continuing after 429");
                } else {
                    retryNeeded |= recordTransientFailure(
                            dao,
                            item,
                            errorMessage(response, "Temporary server error (" + code + ")"));
                    Log.e(TAG, "Backend error=" + item.lastError);
                    Log.d(TAG, "Final Room status=" + item.syncStatus + "; continuing queue");
                }
            } catch (Exception exception) {
                retryNeeded |= recordTransientFailure(
                        dao,
                        item,
                        exception.getMessage() == null
                                ? exception.getClass().getSimpleName() : exception.getMessage());
                Log.e(TAG, "Synchronization exception", exception);
                Log.d(TAG, "Final Room status=" + item.syncStatus + "; continuing queue");
            }
        }
        boolean shouldRetry = delayedItemRemaining || retryNeeded;
        Log.d(TAG, "Worker result=" + (shouldRetry ? "retry" : "success")
                + "; eligible queue processed");
        return shouldRetry ? Result.retry() : Result.success();
    }

    private boolean recordTransientFailure(OfflineDao dao, PendingItemEntity item,
                                           String message) {
        item.retryCount++;
        item.retryAfterAt = null;
        item.lastError = message;
        item.syncStatus = item.retryCount >= MAX_TRANSIENT_RETRIES ? "FAILED" : "PENDING";
        dao.updatePending(item);
        return "PENDING".equals(item.syncStatus);
    }

    private void persistSuccessfulSync(OfflineDao dao, PendingItemEntity item,
                                       ItemResponse result) {
        item.serverItemId = result.getItemId();
        item.serverItemCode = result.getItemCode();
        ItemRemarkIssue issue = result.getRemarkIssue();
        if (issue != null) {
            item.issueId = issue.getIssueId();
            item.issueCode = issue.getIssueCode();
            item.remarks = issue.getRemarks();
        } else {
            item.issueId = null;
            item.issueCode = null;
            if (item.remarks != null && item.remarks.trim().isEmpty()) item.remarks = null;
        }
        item.syncStatus = "SYNCED";
        item.lastError = null;
        item.retryAfterAt = null;
        if (PendingImageStore.delete(item.localImagePath)) {
            item.localImagePath = null;
        } else {
            item.lastError = "Item synchronized, but local image cleanup failed";
        }
        dao.updatePending(item);
        Intent changed = new Intent("com.inventorysystem.INVENTORY_DATA_CHANGED");
        changed.setPackage(getApplicationContext().getPackageName());
        getApplicationContext().sendBroadcast(changed);
        Log.d(TAG, "Final Room status=SYNCED; synchronization succeeded");
    }

    private Call<ItemResponse> createCall(PendingItemEntity item, String token) {
        ApiService api = RetrofitClient.getApiService();
        if (item.localImagePath == null) {
            OfflineItemRequest request = new OfflineItemRequest();
            request.itemName=item.itemName; request.brand=item.brand; request.model=item.model; request.serialNumber=item.serialNumber;
            request.categoryId=item.categoryId; request.categoryName=item.categoryName; request.locationId=item.locationId;
            request.quantity=item.quantity; request.reorderLevel=item.reorderLevel; request.unitCost=item.unitCost;
            request.remarks=item.remarks;
            return api.createItem("Bearer " + token, item.clientRequestId, request);
        }
        File file = new File(item.localImagePath);
        RequestBody imageBody=RequestBody.create(file, MediaType.parse(item.imageMimeType == null ? "image/jpeg" : item.imageMimeType));
        MultipartBody.Part image=MultipartBody.Part.createFormData("image", file.getName(), imageBody);
        return api.createItemMultipart("Bearer "+token,item.clientRequestId,
                item.categoryId == null ? null : text(String.valueOf(item.categoryId)), item.categoryId == null ? text(item.categoryName) : null,
                text(String.valueOf(item.locationId)),text(item.itemName),optional(item.brand),optional(item.model),optional(item.serialNumber),
                text(String.valueOf(item.quantity)),text(String.valueOf(item.reorderLevel)),text(item.unitCost),optional(item.remarks),image);
    }

    private String configureBackend() {
        String saved=BackendPreferences.get(getApplicationContext());
        if (saved != null && healthy(saved)) {
            RetrofitClient.configureBaseUrl(getApplicationContext(), saved);
            return saved;
        }
        CountDownLatch latch=new CountDownLatch(1); AtomicReference<String> selected=new AtomicReference<>();
        new BackendServerSelector().findAvailableServer(new BackendServerSelector.Callback(){
            public void onServerSelected(String url){selected.set(url); latch.countDown();}
            public void onNoServerAvailable(){latch.countDown();}
        });
        try { latch.await(20, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
        if(selected.get()==null) return null;
        BackendPreferences.save(getApplicationContext(),selected.get());
        RetrofitClient.configureBaseUrl(getApplicationContext(),selected.get());
        return selected.get();
    }

    private boolean healthy(String url) {
        try(okhttp3.Response response=new OkHttpClient.Builder().callTimeout(5,TimeUnit.SECONDS).build().newCall(new Request.Builder().url(url+"health").build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return false;
            JSONObject body = new JSONObject(response.body().string());
            return "ok".equalsIgnoreCase(body.optString("status")) && "connected".equalsIgnoreCase(body.optString("database"));
        } catch(Exception ignored){return false;}
    }

    private Long retryAfterAt(Response<?> response) {
        String value = response.headers().get("Retry-After");
        if (value == null || value.trim().isEmpty()) return null;
        try {
            long seconds = Math.max(0, Long.parseLong(value.trim()));
            return System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds);
        } catch (NumberFormatException ignored) {
            try {
                return ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli();
            } catch (Exception invalidDate) {
                return null;
            }
        }
    }

    private String errorMessage(Response<?> response, String fallback) {
        try {
            if (response.errorBody() == null) return fallback;
            String body = response.errorBody().string().trim();
            if (body.isEmpty()) return fallback;
            String diagnosticBody = body.replace('\n', ' ').replace('\r', ' ');
            if (diagnosticBody.length() > 1000) diagnosticBody = diagnosticBody.substring(0, 1000);
            Log.e(TAG, "Backend error body=" + diagnosticBody);

            JSONObject json = new JSONObject(body);
            String message = json.optString("message").trim();
            if (message.isEmpty()) message = json.optString("error").trim();
            if (message.isEmpty()) message = json.optString("details").trim();
            return message.isEmpty() ? fallback : message;
        } catch (Exception ignored) { return fallback; }
    }
    private RequestBody text(String value){return RequestBody.create(value,MediaType.parse("text/plain"));}
    private RequestBody optional(String value){return value==null||value.trim().isEmpty()?null:text(value.trim());}
}
