package com.inventorysystem.ConnectivityandService;

import com.inventorysystem.DashboardModel;
import com.inventorysystem.Model.CategoryBreakdownModel;
import com.inventorysystem.Model.CategoryModel;
import com.inventorysystem.Model.DisposalRequestModel;
import com.inventorysystem.Model.DisposalReportResponse;
import com.inventorysystem.Model.GenericResponse;
import com.inventorysystem.Model.HealthResponse;
import com.inventorysystem.Model.ItemModel;
import com.inventorysystem.Model.ItemRequest;
import com.inventorysystem.Model.OfflineItemRequest;
import com.inventorysystem.Model.ItemResponse;
import com.inventorysystem.Model.LocationBreakdownModel;
import com.inventorysystem.Model.LocationModel;
import com.inventorysystem.Model.LoginRequest;
import com.inventorysystem.Model.LoginResponse;
import com.inventorysystem.Model.LowStockModel;
import com.inventorysystem.Model.MarkAllReadResponse;
import com.inventorysystem.Model.NotificationModel;
import com.inventorysystem.Model.MovementRequest;
import com.inventorysystem.Model.StockMovementModel;
import com.inventorysystem.Model.UserProfile;
import com.inventorysystem.Model.UnreadCountResponse;
import com.inventorysystem.Model.ChangePasswordResponse;
import com.inventorysystem.Model.ActivityLogModel;
import com.inventorysystem.Model.ChangePasswordRequest;
import com.inventorysystem.Model.CreateAccountRequest;
import com.inventorysystem.Model.CreateAccountResponse;
import com.inventorysystem.Model.MessageResponse;
import com.inventorysystem.Model.SystemInformationModel;
import com.inventorysystem.StockCategoryModel;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.PUT;
import retrofit2.http.Query;

public interface ApiService {

    @GET("health")
    Call<HealthResponse> getHealth();

    // Authentication
    @POST("auth/signin")
    Call<LoginResponse> login(
            @Header("X-Device-Name") String deviceName,
            @Body LoginRequest request);

    @GET("activity-logs")
    Call<List<ActivityLogModel>> getActivityLogs(@Query("limit") int limit);

    @GET("system")
    Call<SystemInformationModel> getSystemInformation();

    @POST("auth/logout")
    Call<MessageResponse> logoutAuthenticated();

    @PUT("profile/change-password")
    Call<ChangePasswordResponse> changePassword(@Body ChangePasswordRequest request);

    @POST("profile/add-account")
    Call<CreateAccountResponse> createAccount(@Body CreateAccountRequest request);

    @GET("profile")
    Call<UserProfile> getProfile(@Header("Authorization") String token);

    @PUT("profile/change-password")
    Call<ChangePasswordResponse> changePassword(
            @Header("Authorization") String token,
            @Body Map<String, String> request
    );

    @POST("auth/logout")
    Call<GenericResponse> logout(@Header("Authorization") String token);

    @GET("auth/session")
    Call<LoginResponse> getSession();

    // Dashboard
    @GET("dashboard/summary")
    Call<DashboardModel> getDashboard(
            @Header("Authorization") String token
    );

    @GET("dashboard/stock-by-category")
    Call<List<StockCategoryModel>> getStockByCategory(
            @Header("Authorization") String token
    );

    // Categories
    @GET("categories")
    Call<List<CategoryModel>> getCategories(
            @Header("Authorization") String token
    );

    // Items
    @GET("items")
    Call<List<ItemModel>> getAllItems(
            @Header("Authorization") String token
    );

    @GET("items")
    Call<List<ItemModel>> getItems(
            @Header("Authorization") String token,
            @Query("has_remarks") Boolean hasRemarks,
            @Query("search") String search,
            @Query("category_id") Integer categoryId,
            @Query("page") int page,
            @Query("limit") int limit
    );

    @GET("items/{id}")
    Call<ItemModel> getItemDetails(
            @Header("Authorization") String token,
            @Path("id") int id
    );

    @POST("items")
    Call<ItemResponse> createItem(
            @Header("Authorization") String token,
            @Header("Idempotency-Key") String idempotencyKey,
            @Body OfflineItemRequest itemRequest
    );

    @Multipart
    @POST("items")
    Call<ItemResponse> createItemMultipart(
            @Header("Authorization") String token,
            @Header("Idempotency-Key") String idempotencyKey,
            @Part("category_id") RequestBody categoryId,
            @Part("category_name") RequestBody categoryName,
            @Part("location_id") RequestBody locationId,
            @Part("item_name") RequestBody itemName,
            @Part("brand") RequestBody brand,
            @Part("model") RequestBody model,
            @Part("serial_number") RequestBody serialNumber,
            @Part("quantity") RequestBody quantity,
            @Part("reorder_level") RequestBody reorderLevel,
            @Part("unit_cost") RequestBody unitCost,
            @Part("remarks") RequestBody remarks,
            @Part MultipartBody.Part image
    );

    @Multipart
    @POST("items/{id}/image")
    Call<ItemResponse> uploadItemImage(
            @Header("Authorization") String token,
            @Path("id") int itemId,
            @Part MultipartBody.Part image
    );

    // Locations
    @GET("locations")
    Call<List<LocationModel>> getLocations(
            @Header("Authorization") String token
    );

    // ---------------------------------------------------------------
    // Reports
    // ---------------------------------------------------------------

    @GET("reports/stock-movement")
    Call<List<StockMovementModel>> getStockMovements(
            @Header("Authorization") String token,
            @Query("movement_type") String movementType,
            @Query("page") int page,
            @Query("limit") int limit
    );

    @GET("reports/low-stock")
    Call<List<LowStockModel>> getLowStockItems(
            @Header("Authorization") String token
    );

    @GET("reports/category")
    Call<List<CategoryBreakdownModel>> getCategoryBreakdown(
            @Header("Authorization") String token
    );

    @GET("reports/location")
    Call<List<LocationBreakdownModel>> getLocationBreakdown(
            @Header("Authorization") String token
    );

    // ---------------------------------------------------------------
    // Disposal Requests
    // ---------------------------------------------------------------

    @GET("reports/disposal")
    Call<DisposalReportResponse> getDisposalReport(
            @Header("Authorization") String token
    );

    @Multipart
    @PUT("items/{id}")
    Call<ItemResponse> updateItem(
            @Header("Authorization") String token,
            @Path("id") int itemId,
            @Part("item_code") RequestBody itemCode,
            @Part("item_name") RequestBody itemName,
            @Part("brand") RequestBody brand,
            @Part("model") RequestBody model,
            @Part("serial_number") RequestBody serialNumber,
            @Part("category_id") RequestBody categoryId,
            @Part("category_name") RequestBody categoryName,
            @Part("location_id") RequestBody locationId,
            @Part("quantity") RequestBody quantity,
            @Part("reorder_level") RequestBody reorderLevel,
            @Part("unit_cost") RequestBody unitCost,
            @Part("remarks") RequestBody remarks,
            @Part MultipartBody.Part image
    );

    @GET("disposal-requests")
    Call<List<DisposalRequestModel>> getDisposalRequests(
            @Header("Authorization") String token
    );

    @GET("disposal-requests")
    Call<List<DisposalRequestModel>> getDisposalRequestsByStatus(
            @Header("Authorization") String token,
            @Query("status") String status
    );

    @POST("disposal-requests/{id}/approve")
    Call<GenericResponse> approveDisposalRequest(
            @Header("Authorization") String token,
            @Path("id") int id
    );

    @POST("disposal-requests/{id}/reject")
    Call<GenericResponse> rejectDisposalRequest(
            @Header("Authorization") String token,
            @Path("id") int id
    );

    @POST("disposals")
    Call<GenericResponse> createDisposal(
            @Header("Authorization") String token,
            @Body Map<String, Object> request
    );

    @GET("disposals")
    Call<List<DisposalRequestModel>> getDisposals(
            @Header("Authorization") String token,
            @Query("status") String status,
            @Query("page") int page,
            @Query("limit") int limit
    );

    @PUT("disposals/{id}")
    Call<GenericResponse> updateDisposalStatus(
            @Header("Authorization") String token,
            @Path("id") int disposalId,
            @Body Map<String, String> request
    );

    @PUT("disposals/{id}/dispose")
    Call<GenericResponse> finalizeDisposal(
            @Header("Authorization") String token,
            @Path("id") int disposalId
    );

    @GET("notifications")
    Call<List<NotificationModel>> getNotifications(
            @Query("unread_only") Boolean unreadOnly,
            @Query("page") int page,
            @Query("limit") int limit
    );

    @GET("notifications/unread-count")
    Call<UnreadCountResponse> getUnreadCount();

    @PUT("notifications/{id}/read")
    Call<NotificationModel> markNotificationRead(@Path("id") int notificationId);

    @PUT("notifications/read-all")
    Call<MarkAllReadResponse> markAllNotificationsRead();

    // Backend contract endpoints not yet represented by dedicated screens.
    @GET("items")
    Call<List<ItemModel>> searchItems(
            @Query("search") String search,
            @Query("category_id") Integer categoryId
    );

    @DELETE("items/{id}")
    Call<GenericResponse> deleteItem(@Path("id") int itemId);

    @POST("categories")
    Call<CategoryModel> createCategory(@Body Map<String, String> request);

    @PUT("categories/{id}")
    Call<CategoryModel> updateCategory(
            @Path("id") int categoryId,
            @Body Map<String, String> request
    );

    @DELETE("categories/{id}")
    Call<GenericResponse> deleteCategory(@Path("id") int categoryId);

    @POST("locations")
    Call<LocationModel> createLocation(@Body Map<String, String> request);

    @PUT("locations/{id}")
    Call<LocationModel> updateLocation(
            @Path("id") int locationId,
            @Body Map<String, String> request
    );

    @DELETE("locations/{id}")
    Call<GenericResponse> deleteLocation(@Path("id") int locationId);

    @GET("movements")
    Call<List<StockMovementModel>> getMovements(
            @Query("movement_type") String movementType,
            @Query("page") Integer page,
            @Query("limit") Integer limit
    );

    @POST("movements")
    Call<StockMovementModel> createMovement(@Body MovementRequest request);

    @GET("disposals/{id}")
    Call<DisposalRequestModel> getDisposal(@Path("id") int disposalId);

    @POST("profile/add-account")
    Call<GenericResponse> addAccount(@Body Map<String, String> request);
}
