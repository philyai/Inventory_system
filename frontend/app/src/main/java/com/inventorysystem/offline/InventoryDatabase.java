package com.inventorysystem.offline;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {PendingItemEntity.class, CachedCategoryEntity.class, CachedLocationEntity.class}, version = 2, exportSchema = false)
public abstract class InventoryDatabase extends RoomDatabase {
    private static volatile InventoryDatabase instance;
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE pending_items ADD COLUMN remarks TEXT");
            database.execSQL("ALTER TABLE pending_items ADD COLUMN issueId INTEGER");
            database.execSQL("ALTER TABLE pending_items ADD COLUMN issueCode TEXT");
            database.execSQL("ALTER TABLE pending_items ADD COLUMN serverItemId INTEGER");
            database.execSQL("ALTER TABLE pending_items ADD COLUMN serverItemCode TEXT");
            database.execSQL("ALTER TABLE pending_items ADD COLUMN retryAfterAt INTEGER");
        }
    };

    public abstract OfflineDao offlineDao();

    public static InventoryDatabase get(Context context) {
        if (instance == null) {
            synchronized (InventoryDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    InventoryDatabase.class, "inventory_offline.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}