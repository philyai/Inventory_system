# Login activity database setup

The login activity feature requires a `dbo.login_sessions` table. The Sequelize
model and API use `login_session_id` as the primary-key column.

## Required table

Create `dbo.login_sessions` in the same SQL Server database configured by
`DB_NAME` in `.env`, using these columns:

| Column | SQL Server type | Rules |
| --- | --- | --- |
| `login_session_id` | `BIGINT` | Primary key, identity |
| `users_id` | `INT` | Required; foreign key to `dbo.users(users_id)` |
| `login_time` | `DATETIME2` | Required; default `SYSDATETIME()` |
| `logout_time` | `DATETIME2` | Nullable |
| `device_info` | `NVARCHAR(500)` | Required |
| `ip_address` | `NVARCHAR(64)` | Nullable |
| `user_agent` | `NVARCHAR(1000)` | Nullable |
| `status` | `NVARCHAR(20)` | Required; default `active` |

Add an index on `users_id` and descending `login_time` for the activity-list
query.

After creating the table, confirm it is accessible:

```sql
SELECT TOP (10) *
FROM dbo.login_sessions
ORDER BY login_time DESC;
```

Do not manually insert login rows. The backend adds a row after a successful
sign-in and updates it during logout or the user's next sign-in.

## Existing table created with `session_id`

If an earlier version of the table has a `session_id` column instead of
`login_session_id`, rename it once:

```sql
IF COL_LENGTH(N'dbo.login_sessions', N'login_session_id') IS NULL
   AND COL_LENGTH(N'dbo.login_sessions', N'session_id') IS NOT NULL
BEGIN
  EXEC sp_rename
    N'dbo.login_sessions.session_id',
    N'login_session_id',
    N'COLUMN';
END;
```

After the setup, restart the backend and sign in. Then call:

```http
GET /activity-logs?limit=50
Authorization: Bearer <token returned by POST /auth/signin>
```

The endpoint should return the signed-in user's newest sessions first.
