package com.inventorysystem;

import android.app.Application;
import com.inventorysystem.offline.InventoryDatabase;
import com.inventorysystem.offline.OfflineItemSyncScheduler;

public class InventoryApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        SessionManager session = new SessionManager(this);
        if (session.isLoggedIn()) {
            int userId = session.getUserId();
            InventoryDatabase.IO.execute(() -> InventoryDatabase.get(this)
                    .offlineDao().resetAbandonedSyncing(userId));
            OfflineItemSyncScheduler.enqueue(this);
        }
    }
}
