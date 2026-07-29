# Android Frontend Integration Guide

## Connection and authentication

The API listens on port `3001` by default. The Android base URL must end with `/`:

```java
private static final String BASE_URL = "http://<backend-laptop-ip>:3001/";
```

For local HTTP development, add the Internet permission above `<application>` and
allow cleartext traffic on the application:

```xml
<uses-permission android:name="android.permission.INTERNET" />

<application
    android:usesCleartextTraffic="true"
    ...>
```

Except for `GET /health` and `POST /auth/signin`, every endpoint requires:

```http
Authorization: Bearer <token>
```

Store the token returned by sign-in and add it with an OkHttp interceptor. Treat
HTTP `401` as an expired session: clear the saved token and return to login. Treat
HTTP `403` as an authenticated user without the required role. Every request error
is JSON with at least:

```json
{ "message": "Human-readable error" }
```

Always clear progress indicators in `finally`, `onFailure`, and non-2xx response
paths. A `401`, `403`, or `500` response must never leave a screen permanently
showing "Loading".

## Roles

- `Admin IT`: full access to IT and Purchasing operations, plus account creation.
- `IT`: item, category, location, stock movement, disposal request, and physical
  disposal operations.
- `Purchasing`: approve or reject pending disposal requests.
- All authenticated roles can read inventory, dashboard, reports, profile, and
  their own notifications.

Hide actions the role cannot call, but still handle HTTP `403` because the backend
is the final authority.

## Endpoint contract

All list endpoints return a raw JSON array unless a response is explicitly shown
as an object below.

### Health and authentication

| Method | Path | Auth | Request/response |
|---|---|---|---|
| GET | `/health` | No | `{ "status": "ok", "database": "connected" }` |
| POST | `/auth/signin` | No | Body: `username`, `password`; returns `token` and `user` |
| GET | `/auth/session` | Yes | Returns the current `user` |
| POST | `/auth/logout` | Yes | Invalidates the stored token |
| GET | `/activity-logs?limit=50` | Yes | Current account's login sessions, newest first |
| GET | `/system` | Yes | Application, runtime, OS, database, and uptime information |

Sign-in response:

```json
{
  "token": "<jwt>",
  "user": {
    "users_id": 1,
    "username": "admin",
    "role": "Admin IT"
  }
}
```

The client may send `X-Device-Name` during sign-in (for example,
`Samsung Galaxy Tab A8`). If omitted, the backend uses the request User-Agent.
Create the `login_sessions` table in the application database before deploying
the activity-log feature. Explicit logout records `logout_time`;
closing the app without calling logout leaves that session open until it is
replaced by a later login.

### Items

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/items` | Any | Optional `search` and `category_id` query parameters |
| GET | `/items/{id}` | Any | Item with `Category` and `ItemLocation` |
| POST | `/items` | IT/Admin IT | `multipart/form-data`; optional image |
| PUT | `/items/{id}` | IT/Admin IT | `multipart/form-data`; partial fields accepted |
| POST | `/items/{id}/image` | IT/Admin IT | Multipart field name must be `image` |
| DELETE | `/items/{id}` | IT/Admin IT | Returns `409` if related records prevent deletion |

Create-item multipart fields:

```text
item_name             required string
brand                 optional string
model                 optional string
serial_number         optional string
category_id           positive integer, or send category_name
category_name         custom category name when applicable
location_id           required positive integer
quantity              required non-negative integer
reorder_level         required non-negative integer
unit_cost             required non-negative decimal
image                 optional JPG, PNG, or WebP, maximum 5 MB
```

Do not send `status`, `total_value`, `created_by`, `date_added`, or a generated
`item_code`. The backend calculates them.

For each Add Item form submission, generate one UUID and send:

```http
Idempotency-Key: <uuid>
```

Reuse that key only when retrying the same submission. Disable the Save button
until the request finishes. The backend also blocks identical rapid submissions.
A replay may return HTTP `200` with `Idempotent-Replayed: true`; a new item returns
HTTP `201`.

Item image paths are relative, for example `/uploads/items/file.jpg`. Display them
using the API origin:

```text
http://<backend-laptop-ip>:3001/uploads/items/file.jpg
```

### Categories and locations

| Method | Path | Role | Body |
|---|---|---|---|
| GET | `/categories` | Any | None |
| POST | `/categories` | IT/Admin IT | `category_name`, optional `description` |
| PUT | `/categories/{id}` | IT/Admin IT | `category_name` and/or `description` |
| DELETE | `/categories/{id}` | IT/Admin IT | None |
| GET | `/locations` | Any | None |
| POST | `/locations` | IT/Admin IT | `location_name`, optional `description` |
| PUT | `/locations/{id}` | IT/Admin IT | `location_name` and/or `description` |
| DELETE | `/locations/{id}` | IT/Admin IT | None |

The category and location list responses are raw arrays suitable for Retrofit
`Call<List<CategoryModel>>` and `Call<List<LocationModel>>`.

### Stock movements

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/movements` | Any | Optional `movement_type`, `page`, `limit` |
| POST | `/movements` | IT/Admin IT | Creates movement and updates stock atomically |

POST body:

```json
{
  "item_id": 1,
  "movement_type": "Out",
  "quantity_change": -2,
  "source_destination": "IT Department",
  "remarks": "Issued to staff"
}
```

`In` requires a positive quantity, `Out` requires a negative quantity, and
`Adjustment` accepts either sign but not zero. `limit` must be from 1 to 100.

### Disposal workflow

```text
Pending Approval -> For Disposal -> Disposed
                 \-> Rejected
```

| Method | Path | Role | Notes |
|---|---|---|---|
| GET | `/disposals` | Any | Optional `status`; `Approved` returns For Disposal + Disposed |
| GET | `/disposals/{id}` | Any | Single request with item and approver details |
| POST | `/disposals` | IT/Admin IT | Body: `item_id`, `reason` |
| PUT | `/disposals/{id}` | Purchasing/Admin IT | Body status: `For Disposal` or `Rejected` |
| PUT | `/disposals/{id}/dispose` | IT/Admin IT | Finalizes an approved request |

Finalization decreases quantity by one, recalculates stock status/value, marks the
request `Disposed`, and creates a requester notification in one transaction.
Only show Finalize for `For Disposal`. Repeated finalization returns HTTP `409`.

### Dashboard and reports

| Method | Path |
|---|---|
| GET | `/dashboard/summary` |
| GET | `/dashboard/stock-by-category` |
| GET | `/reports/stock-summary` |
| GET | `/reports/low-stock` |
| GET | `/reports/disposal` |
| GET | `/reports/category` |
| GET | `/reports/location` |
| GET | `/reports/stock-movement?start_date=YYYY-MM-DD&end_date=YYYY-MM-DD` |

Dashboard category entries contain aggregate fields plus a nested `Category`
object. Use `@SerializedName("Category")` if the Java property uses another name.

### Profile and account management

| Method | Path | Role | Body |
|---|---|---|---|
| GET | `/profile` | Any | None |
| PUT | `/profile/change-password` | Any | `username`, `current_password`, `new_password`, `confirm_password` |
| POST | `/profile/add-account` | Admin IT | `username`, `email`, `password`, `role` |

Account roles must be exactly `Admin IT`, `IT`, or `Purchasing`.

The Profile & Settings frontend should remove the separate `User Profile` menu
button. Keep using the sign-in response, `/auth/session`, or `GET /profile` to
render the signed-in account summary at the top of the menu.

### Activity logs and About the System

`GET /activity-logs` returns only the authenticated user's sessions. The optional
`limit` is from 1 to 100 and defaults to 50:

```json
[
  {
    "login_session_id": 12,
    "login_time": "2026-07-28T01:43:00.000Z",
    "logout_time": "2026-07-28T03:15:00.000Z",
    "device_info": "Samsung Galaxy Tab A8",
    "ip_address": "192.168.1.25",
    "status": "logged_out"
  }
]
```

`GET /system` returns safe server information for the About screen. Set optional
environment variables `SYSTEM_NAME` and `FIRMWARE_VERSION` to customize the
displayed labels. When `FIRMWARE_VERSION` is absent, the application version is
used.

## Notifications frontend

The backend notification endpoints are:

| Method | Path | Purpose |
|---|---|---|
| GET | `/notifications` | All notifications newest first |
| GET | `/notifications?unread_only=true` | Unread notifications only |
| GET | `/notifications/unread-count` | `{ "unread_count": 3 }` |
| PUT | `/notifications/{id}/read` | Mark one owned notification read |
| PUT | `/notifications/read-all` | Mark all owned notifications read |

Notification model:

```java
public class NotificationModel {
    int notification_id;
    int user_id;
    String message;
    String type;
    boolean is_read;
    String created_at;
}
```

Known `type` values:

```text
disposal_requested
disposal_approved
disposal_rejected
disposal_completed
```

Recommended UI:

1. Add a bell icon to the authenticated app bar.
2. Fetch `/notifications/unread-count` after login and whenever the main screen
   resumes. Show a badge only when the count is greater than zero.
3. Poll the unread count every 30 seconds while the app is in the foreground, or
   refresh it after disposal actions. Stop polling when the lifecycle owner stops.
4. Open a Notifications screen from the bell. Fetch `/notifications` and render
   unread rows with stronger styling.
5. When a row is opened, call `PUT /notifications/{id}/read`, update that row
   locally, and decrement/refetch the badge.
6. Add "Mark all as read" using `PUT /notifications/read-all`.
7. For `disposal_requested`, navigate reviewers to the pending disposal list.
   For approval/rejection/completion types, navigate the requester to disposal
   history or the matching disposal detail.
8. Show an empty state when the returned array is empty and a retry state for
   network errors. Never treat an empty array as an error.

Example Retrofit interface:

```java
@GET("notifications")
Call<List<NotificationModel>> getNotifications(
        @Query("unread_only") Boolean unreadOnly
);

@GET("notifications/unread-count")
Call<UnreadCountResponse> getUnreadCount();

@PUT("notifications/{id}/read")
Call<NotificationModel> markNotificationRead(@Path("id") int id);

@PUT("notifications/read-all")
Call<MarkAllReadResponse> markAllNotificationsRead();
```

Notifications are API/in-app notifications, not Android system push
notifications. Firebase Cloud Messaging would be a separate future feature.
