# Add `Admin IT` Role and Add-Account Feature

You are working on an existing Node.js/Express 5/Sequelize 6 backend using MSSQL through `tedious`, together with its existing mobile frontend.

Implement the feature below surgically. Inspect the current files first and preserve all existing routes, response shapes, image-upload behavior, disposal behavior, authentication behavior, and frontend functionality unless a change is explicitly required here.

## Goal

Introduce one new exact role value:

```text
Admin IT
```

`Admin IT` is the system's full-access role:

- It can perform every action currently allowed to `IT`.
- It can perform every action currently allowed to `Purchasing`.
- It can access all authenticated endpoints that do not require a specific role.
- It is the only role allowed to create new user accounts.

Existing `IT` and `Purchasing` permissions must remain unchanged. Neither role may create accounts.

## Hard constraints

1. Use the exact case-sensitive role strings `Admin IT`, `IT`, and `Purchasing` everywhere.
2. Do not rename existing roles, fields, endpoints, functions, or response keys.
3. Do not rewrite existing controllers or route files. Make only the smallest edits required.
4. Do not change sign-in, token storage, password change, item upload, disposal, movement, notification, report, category, or location behavior.
5. Do not add npm dependencies or call `sequelize.sync()`.
6. Do not make an automatic database role update. The first `Admin IT` account must be selected explicitly by the system owner.
7. Do not expose `password_hash` or `token` in any API response.
8. Preserve the existing JSON error-response style and existing Bearer-token authentication.

## Backend role hierarchy

### File: `middleware/authMiddleware.js`

Make the smallest possible change to `requireRole` so that `Admin IT` has full role-based access while existing roles behave exactly as before.

Required behavior:

```js
const requireRole = (...allowedRoles) => {
  return (req, res, next) => {
    if (req.user.role === 'Admin IT') {
      return next();
    }

    if (!allowedRoles.includes(req.user.role)) {
      return res.status(403).json({ message: 'You do not have permission to do this' });
    }

    next();
  };
};
```

Do not edit every existing IT/Purchasing route. This hierarchy must allow `Admin IT` through the existing `requireRole('IT')` and `requireRole('Purchasing')` checks without changing those route definitions.

The `Admin IT` bypass applies only after `verifyToken` has authenticated the user and populated `req.user` from the database.

## Add-account backend API

### Endpoint

```http
POST /profile/add-account
Authorization: Bearer <token>
Content-Type: application/json
```

### Request body

```json
{
  "username": "new.user",
  "email": "new.user@example.com",
  "password": "password123",
  "role": "IT"
}
```

### Allowed role values for new accounts

The `role` field is not free text. The backend must accept exactly one of:

```text
Admin IT
IT
Purchasing
```

Reject every other value with HTTP 400.

### File: `controllers/profileController.js`

Add an `addAccount` function without changing `getProfile` or `changePassword`.

Requirements:

1. Accept only `username`, `email`, `password`, and `role` from `req.body`.
2. Require all four values to be non-empty strings.
3. Trim `username` and `email`; normalize email to lowercase. Do not trim or modify the password.
4. Require a password of at least eight characters.
5. Validate the role against the exact three-value allowlist above.
6. Check for an existing username or email using Sequelize `Op.or`.
7. Return HTTP 409 when the username or email is already used.
8. Hash the password with `bcrypt.hash(password, 10)`.
9. Create the user with:
   - `password_hash`: the generated hash
   - `status`: `active`
   - `token`: `null`
   - `created_at`: `literal('SYSDATETIME()')`, avoiding JavaScript-date conversion problems with this SQL Server
10. Return HTTP 201 with an explicit safe response object containing only:
    - `users_id`
    - `username`
    - `email`
    - `role`
    - `status`
    - `created_at`
11. Never return the Sequelize instance directly if doing so could expose `password_hash` or `token`.
12. Translate `SequelizeUniqueConstraintError` into HTTP 409 in case two simultaneous requests pass the initial duplicate check.
13. Use the existing try/catch and JSON error style for unexpected failures.
14. Export `addAccount` alongside the existing profile controller functions.

Suggested success response:

```json
{
  "message": "Account created successfully",
  "user": {
    "users_id": 4,
    "username": "new.user",
    "email": "new.user@example.com",
    "role": "IT",
    "status": "active",
    "created_at": "2026-07-22T06:30:00.000Z"
  }
}
```

### File: `routes/profileRoutes.js`

Import `addAccount`, `requireRole`, and the existing `writeLimiter`. Add only this route:

```js
router.post(
  '/add-account',
  verifyToken,
  requireRole('Admin IT'),
  writeLimiter,
  addAccount
);
```

Even though `Admin IT` globally passes role checks, this explicit route declaration documents that account creation is an admin-only operation. `IT` and `Purchasing` must receive HTTP 403.

Do not change the existing profile routes.

## Database requirements

The existing `users.role` column is a string column, so adding `Admin IT` does not require a new table or column unless the live database has a CHECK constraint limiting role values. Inspect constraints before making any database change.

Do not automatically promote a user. Before creating the first admin, show the system owner the candidate users and require an explicit `users_id`. Only after approval should a single targeted statement be run:

```sql
UPDATE dbo.users
SET role = 'Admin IT'
WHERE users_id = <explicitly-approved-user-id>
  AND role = 'IT';
```

Never run a broad update such as `UPDATE users SET role = 'Admin IT'` without an exact user ID.

After promotion, the user should sign out and sign back in so the frontend receives the updated role. The backend's authorization must use `req.user.role`, which is loaded from the current database user.

Do not modify `models/user.js` merely to add the new role because `role` is already stored as a string. Do not add Sequelize enums or automatic migrations.

## Frontend requirements

First inspect the existing frontend stack, navigation, authenticated-user state, API client, styling, and role-gating patterns. Reuse them rather than adding a parallel architecture.

### Full-access role helpers

Centralize these permission checks if the frontend already has a suitable role/permission helper:

```js
const isAdminIT = user?.role === 'Admin IT';
const canUseITFeatures = user?.role === 'IT' || isAdminIT;
const canUsePurchasingFeatures = user?.role === 'Purchasing' || isAdminIT;
```

Use the authenticated user returned by sign-in or the profile endpoint as the role source. Do not rely only on a stale JWT payload.

All existing IT-only UI actions must be visible to `Admin IT`. All existing Purchasing-only UI actions must also be visible to `Admin IT`. Existing visibility for `IT` and `Purchasing` must not change.

### Profile menu

Add an `Add Account` menu option near `User Profile` on the Profile & Settings screen.

Render it only when:

```js
user?.role === 'Admin IT'
```

Do not show it to `IT` or `Purchasing`.

### Add Account screen

Reuse the existing form components and styling. Include exactly these fields:

- Username: required text input
- Email: required email input
- Password: required masked input, minimum eight characters
- Role: required selector/modal, never a free-text field

The role modal must show exactly:

- `Admin IT`
- `IT`
- `Purchasing`

On submit, call `POST /profile/add-account` with the existing Bearer-token pattern. Show loading, success, validation, duplicate, forbidden, and network feedback using existing app components.

After success, return to Profile & Settings or show a confirmation. Do not replace the current session with the newly created account.

## Files allowed to change

Backend:

- `middleware/authMiddleware.js`
- `controllers/profileController.js`
- `routes/profileRoutes.js`

Frontend:

- Only the existing authentication/permission helper if needed
- The existing Profile & Settings screen
- The existing navigation configuration needed to register Add Account
- One Add Account screen and its directly related styles/types

Do not modify unrelated backend or frontend files.

## Acceptance tests

1. Existing `IT` users retain all current IT permissions.
2. Existing `Purchasing` users retain all current Purchasing permissions.
3. `Admin IT` can call every endpoint protected by `requireRole('IT')`.
4. `Admin IT` can call every endpoint protected by `requireRole('Purchasing')`.
5. `Admin IT` sees all IT and Purchasing role-gated frontend actions.
6. Only `Admin IT` sees the Add Account menu and can open the screen.
7. Direct calls by `IT` or `Purchasing` to `POST /profile/add-account` return 403.
8. The role selector and backend accept only `Admin IT`, `IT`, or `Purchasing`.
9. Missing fields, invalid roles, and short passwords return 400.
10. Duplicate usernames or emails return 409.
11. Successful creation returns 201 without `password_hash` or `token`.
12. The new user can sign in immediately and receives the selected role.
13. Creating a new account does not change the current admin session.
14. Existing sign-in, profile, password-change, item, image-upload, movement, disposal, notification, report, category, and location flows continue to behave exactly as before.
