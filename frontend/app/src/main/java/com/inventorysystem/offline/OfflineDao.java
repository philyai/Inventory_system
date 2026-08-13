package com.inventorysystem.offline;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface OfflineDao {
    @Insert void insertPending(PendingItemEntity item);
    @Update void updatePending(PendingItemEntity item);
    @Delete void deletePending(PendingItemEntity item);

    @Query("SELECT * FROM pending_items WHERE localId = :localId AND ownerUserId = :userId LIMIT 1")
    PendingItemEntity getPendingById(String localId, int userId);

    @Query("SELECT * FROM pending_items WHERE ownerUserId = :userId AND syncStatus != 'SYNCED' ORDER BY createdAt ASC")
    LiveData<List<PendingItemEntity>> observeItems(int userId);

    @Query("SELECT * FROM pending_items WHERE ownerUserId = :userId AND syncStatus IN ('PENDING', 'SYNCING') ORDER BY createdAt ASC")
    List<PendingItemEntity> getSyncableItems(int userId);

    @Query("SELECT COUNT(*) FROM pending_items WHERE ownerUserId = :userId AND syncStatus IN ('PENDING', 'SYNCING')")
    LiveData<Integer> observePendingCount(int userId);

    @Query("SELECT COUNT(*) FROM pending_items WHERE ownerUserId = :userId AND syncStatus = 'FAILED'")
    LiveData<Integer> observeFailedCount(int userId);

    @Query("UPDATE pending_items SET syncStatus = 'PENDING' WHERE ownerUserId = :userId AND syncStatus = 'SYNCING'")
    void resetAbandonedSyncing(int userId);

    @Query("DELETE FROM pending_items WHERE localId = :localId AND ownerUserId = :userId")
    int deleteById(String localId, int userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertCategories(List<CachedCategoryEntity> items);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertLocations(List<CachedLocationEntity> items);
    @Query("DELETE FROM cached_categories") void clearCategories();
    @Query("DELETE FROM cached_locations") void clearLocations();
    @Query("SELECT * FROM cached_categories ORDER BY categoryName COLLATE NOCASE") List<CachedCategoryEntity> getCategories();
    @Query("SELECT * FROM cached_locations ORDER BY locationName COLLATE NOCASE") List<CachedLocationEntity> getLocations();

    @Transaction default void replaceCategories(List<CachedCategoryEntity> items) { clearCategories(); insertCategories(items); }
    @Transaction default void replaceLocations(List<CachedLocationEntity> items) { clearLocations(); insertLocations(items); }
}
