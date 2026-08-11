const test = require('node:test');
const assert = require('node:assert/strict');
const {
  calculateStatus,
  formatIssueCode,
  movementTypeForQuantityChange,
} = require('../controllers/itemController');
const { requireRole } = require('../middleware/authMiddleware');
const { findFirst } = require('../utils/modelQueries');
const {
  getDeviceDetails,
  isMissingLoginSessionsTable,
} = require('../controllers/authController');
const { changePassword } = require('../controllers/profileController');
const User = require('../models/user');
const bcrypt = require('bcrypt');
const { fingerprintSessionToken } = require('../utils/sessionToken');

test('session token fingerprints are deterministic and do not expose the token', () => {
  const token = 'header.payload.signature';
  const fingerprint = fingerprintSessionToken(token);

  assert.equal(fingerprint.length, 64);
  assert.equal(fingerprint, fingerprintSessionToken(token));
  assert.notEqual(fingerprint, token);
});

test('calculateStatus returns Out of Stock for zero or negative quantity', () => {
  assert.equal(calculateStatus(0, 5), 'Out of Stock');
  assert.equal(calculateStatus(-1, 5), 'Out of Stock');
});

test('calculateStatus returns Low Stock at or below the reorder level', () => {
  assert.equal(calculateStatus(1, 5), 'Low Stock');
  assert.equal(calculateStatus(5, 5), 'Low Stock');
});

test('calculateStatus returns In Stock above the reorder level', () => {
  assert.equal(calculateStatus(6, 5), 'In Stock');
});

test('formatIssueCode preserves the established padded issue code format', () => {
  assert.equal(formatIssueCode(46), 'ISS-000046');
});

test('quantity increases are reported as In movements', () => {
  assert.equal(movementTypeForQuantityChange(5), 'In');
});

test('quantity decreases are reported as Out movements', () => {
  assert.equal(movementTypeForQuantityChange(-3), 'Out');
});

test('unchanged quantity does not create a movement', () => {
  assert.equal(movementTypeForQuantityChange(0), null);
});

test('Admin IT bypasses role-specific restrictions', () => {
  let nextCalled = false;
  requireRole('Purchasing')(
    { user: { role: 'Admin IT' } },
    {},
    () => { nextCalled = true; }
  );
  assert.equal(nextCalled, true);
});

test('an explicitly allowed role can continue', () => {
  let nextCalled = false;
  requireRole('IT')(
    { user: { role: 'IT' } },
    {},
    () => { nextCalled = true; }
  );
  assert.equal(nextCalled, true);
});

test('a disallowed role receives HTTP 403', () => {
  let statusCode;
  let responseBody;
  const res = {
    status(code) {
      statusCode = code;
      return this;
    },
    json(body) {
      responseBody = body;
      return this;
    },
  };

  requireRole('Purchasing')(
    { user: { role: 'IT' } },
    res,
    () => assert.fail('next must not be called')
  );

  assert.equal(statusCode, 403);
  assert.deepEqual(responseBody, { message: 'You do not have permission to do this' });
});

test('findFirst avoids legacy-SQL pagination and returns the first matching row', async () => {
  const expectedOptions = { where: { username: 'admin' } };
  const firstRow = { users_id: 1 };
  const Model = {
    async findAll(options) {
      assert.deepEqual(options, expectedOptions);
      return [firstRow, { users_id: 2 }];
    },
  };

  assert.equal(await findFirst(Model, expectedOptions), firstRow);
});

test('device details prefer the client device name and preserve the user agent', () => {
  const headers = {
    'x-device-name': 'Samsung Galaxy Tab A8',
    'user-agent': 'InventoryApp/1.0 Android/14',
  };
  const details = getDeviceDetails({
    get(name) {
      return headers[name.toLowerCase()];
    },
  });

  assert.deepEqual(details, {
    deviceInfo: 'Samsung Galaxy Tab A8',
    userAgent: 'InventoryApp/1.0 Android/14',
  });
});

test('device details fall back to the user agent', () => {
  const details = getDeviceDetails({
    get(name) {
      return name.toLowerCase() === 'user-agent' ? 'Mozilla/5.0 Test Browser' : undefined;
    },
  });

  assert.equal(details.deviceInfo, 'Mozilla/5.0 Test Browser');
});

test('missing login-session table errors are recognized for sign-in fallback', () => {
  assert.equal(
    isMissingLoginSessionsTable({
      parent: { message: "Invalid object name 'login_sessions'." },
    }),
    true
  );
  assert.equal(
    isMissingLoginSessionsTable(new Error('Connection timed out')),
    false
  );
});

test('change password verifies the current password, hashes the replacement, and saves it', async () => {
  const originalFindByPk = User.findByPk;
  const originalCompare = bcrypt.compare;
  const originalHash = bcrypt.hash;
  let saved = false;
  let statusCode;
  let responseBody;
  const user = {
    username: 'it.admin',
    password_hash: 'old-hash',
    async save() {
      saved = true;
    },
  };

  User.findByPk = async (usersId) => {
    assert.equal(usersId, 7);
    return user;
  };
  bcrypt.compare = async (password, hash) => {
    assert.equal(password, 'current-password');
    assert.equal(hash, 'old-hash');
    return true;
  };
  bcrypt.hash = async (password, rounds) => {
    assert.equal(password, 'replacement-password');
    assert.equal(rounds, 10);
    return 'new-hash';
  };

  try {
    await changePassword(
      {
        user: { users_id: 7 },
        body: {
          current_password: 'current-password',
          new_password: 'replacement-password',
          confirm_password: 'replacement-password',
        },
      },
      {
        status(code) {
          statusCode = code;
          return this;
        },
        json(body) {
          responseBody = body;
          return this;
        },
      }
    );
  } finally {
    User.findByPk = originalFindByPk;
    bcrypt.compare = originalCompare;
    bcrypt.hash = originalHash;
  }

  assert.equal(statusCode, 200);
  assert.deepEqual(responseBody, {
    message: 'Password changed successfully. Please sign in again.',
    requires_reauthentication: true,
  });
  assert.equal(user.password_hash, 'new-hash');
  assert.equal(saved, true);
});

test('profile add-account route includes authentication and authorization middleware', () => {
  const profileRouter = require('../routes/profileRoutes');
  const addAccountRoute = profileRouter.stack.find(
    (layer) => layer.route && layer.route.path === '/add-account'
  );

  assert.ok(addAccountRoute);
  assert.equal(addAccountRoute.route.methods.post, true);
  assert.equal(addAccountRoute.route.stack.length, 4);
});
