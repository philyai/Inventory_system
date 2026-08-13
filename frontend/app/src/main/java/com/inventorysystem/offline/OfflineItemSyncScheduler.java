package com.inventorysystem.offline;

import android.content.Context;
import android.util.Log;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public final class OfflineItemSyncScheduler {
    public static final String WORK_NAME = "offline-item-sync";
    private OfflineItemSyncScheduler() {}
    public static void enqueue(Context context) {
        enqueue(context, ExistingWorkPolicy.KEEP);
    }
    public static void retryNow(Context context) {
        enqueue(context, ExistingWorkPolicy.REPLACE);
    }
    private static void enqueue(Context context, ExistingWorkPolicy policy) {
        Log.d("OfflineSync", "Scheduling offline item sync");
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(OfflineItemSyncWorker.class)
                .setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(WORK_NAME, policy, request);
    }
}