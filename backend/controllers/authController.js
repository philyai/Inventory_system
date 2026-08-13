const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const { literal } = require('sequelize');
const User = require('../models/user');
const LoginSession = require('../models/loginSession');
const sequelize = require('../models');
const { findFirst } = require('../utils/modelQueries');
const { fingerprintSessionToken } = require('../utils/sessionToken');
const { sendServerError } = require('../utils/httpError');

// --- Account-based throttling (in-memory) ---
// Tracks failed attempts per username, independent of IP.
// NOTE: resets on server restart and doesn't sync across multiple
// server instances. If you scale to >1 instance, move this to Redis
// or persist it as columns on the User table.
const failedAttempts = new Map(); // username -> { count, lockUntil }
const MAX_ATTEMPTS = 5;
const LOCK_DURATION_MS = 5 * 60 * 1000; // 5 minutes

function getRecord(username) {
  return failedAttempts.get(username) || { count: 0, lockUntil: null };
}

function registerFailure(username) {
  const record = getRecord(username);
  record.count += 1;
  if (record.count >= MAX_ATTEMPTS) {
    record.lockUntil = Date.now() + LOCK_DURATION_MS;
    record.count = 0;
  }
  failedAttempts.set(username, record);
}

function clearAttempts(username) {
  failedAttempts.delete(username);
}

function truncate(value, maximumLength) {
  return String(value || '').trim().slice(0, maximumLength);
}

function getDeviceDetails(req) {
  const suppliedDevice = truncate(req.get('x-device-name'), 500);
  const userAgent = truncate(req.get('user-agent'), 1000);

  return {
    deviceInfo: suppliedDevice || truncate(userAgent || 'Unknown device', 500),
    userAgent: userAgent || null,
  };
}

function isMissingLoginSessionsTable(error) {
  let currentError = error;
  while (currentError) {
    if (
      typeof currentError.message === 'string' &&
      currentError.message.toLowerCase().includes('login_sessions') &&
      (
        currentError.message.toLowerCase().includes('invalid object') ||
        currentError.message.toLowerCase().includes('does not exist')
      )
    ) {
      return true;
    }
    currentError = currentError.parent || currentError.original;
  }
  return false;
}

function createToken(user, loginSessionId) {
  const payload = {
    users_id: user.users_id,
    username: user.username,
    role: user.role,
    email: user.email,
  };

  if (loginSessionId) {
    payload.login_session_id = loginSessionId;
  }

  return jwt.sign(payload, process.env.JWT_SECRET, { expiresIn: '168h' });
}

const signIn = async (req, res) => {
  const { username, password } = req.body;
  const ip = req.ip;

  try {
    if (
      typeof username !== 'string' ||
      typeof password !== 'string' ||
      !username.trim() ||
      !password
    ) {
      return res.status(400).json({ message: 'username and password are required' });
    }

    if (username.trim().length > 100 || password.length > 128) {
      return res.status(400).json({ message: 'Invalid username or password' });
    }

    const normalizedUsername = username.trim();
    const record = getRecord(normalizedUsername);
    if (record.lockUntil && record.lockUntil > Date.now()) {
      const secondsLeft = Math.ceil((record.lockUntil - Date.now()) / 1000);
      console.warn(`[LOGIN LOCKED] username="${username}" ip=${ip}`);
      return res.status(429).json({
        message: `Too many failed attempts. Try again in ${secondsLeft}s.`,
      });
    }

    const user = await findFirst(User, { where: { username: normalizedUsername } });
    if (!user) {
      registerFailure(normalizedUsername);
      console.warn(`[LOGIN FAIL] username="${normalizedUsername}" ip=${ip} reason=no_such_user`);
      return res.status(401).json({ message: 'Invalid username or password' });
    }

    if (String(user.status).toLowerCase() !== 'active') {
      return res.status(403).json({ message: 'This account is inactive. Contact an administrator.' });
    }

    const isMatch = await bcrypt.compare(password, user.password_hash);
    if (!isMatch) {
      registerFailure(normalizedUsername);
      console.warn(`[LOGIN FAIL] username="${normalizedUsername}" ip=${ip} reason=bad_password`);
      return res.status(401).json({ message: 'Invalid username or password' });
    }

    // success — clear any accumulated failures
    clearAttempts(normalizedUsername);

    const databaseNow = literal('GETDATE()');
    const { deviceInfo, userAgent } = getDeviceDetails(req);
    const transaction = await sequelize.transaction();
    let token;
    let loginSession;

    try {
      await LoginSession.update(
        { logout_time: databaseNow, status: 'replaced' },
        {
          where: {
            users_id: user.users_id,
            logout_time: null,
          },
          transaction,
        }
      );

      loginSession = await LoginSession.create({
        users_id: user.users_id,
        login_time: databaseNow,
        logout_time: null,
        device_info: deviceInfo,
        ip_address: truncate(ip, 64) || null,
        user_agent: userAgent,
        status: 'active',
      }, { transaction });

      token = createToken(user, loginSession.login_session_id);

      user.token = fingerprintSessionToken(token);
      await user.save({ transaction });
      await transaction.commit();
    } catch (error) {
      await transaction.rollback();
      if (!isMissingLoginSessionsTable(error)) {
        throw error;
      }

      console.warn(
        '[SESSION LOG DISABLED] Create the login_sessions table to enable activity logs.'
      );
      loginSession = null;
      token = createToken(user);
      user.token = fingerprintSessionToken(token);
      await user.save();
    }

    const response = {
      token,
      user: {
        users_id: user.users_id,
        username: user.username,
        role: user.role,
      },
    };
    if (loginSession) {
      response.login_session_id = loginSession.login_session_id;
    }

    res.status(200).json(response);
  } catch (error) {
    sendServerError(res, 'Sign in failed', error);
  }
};

const logout = async (req, res) => {
  try {
    const user = await User.findByPk(req.user.users_id);
    if (!user) {
      return res.status(404).json({ message: 'User not found' });
    }

    const transaction = await sequelize.transaction();
    try {
      user.token = null;
      await user.save({ transaction });

      if (req.user.login_session_id) {
        await LoginSession.update(
          {
            logout_time: literal('GETDATE()'),
            status: 'logged_out',
          },
          {
            where: {
              login_session_id: req.user.login_session_id,
              users_id: req.user.users_id,
              logout_time: null,
            },
            transaction,
          }
        );
      }

      await transaction.commit();
    } catch (error) {
      await transaction.rollback();
      if (!isMissingLoginSessionsTable(error)) {
        throw error;
      }

      console.warn(
        '[SESSION LOG DISABLED] Logout time was not recorded because login_sessions is unavailable.'
      );
      user.token = null;
      await user.save();
    }

    res.status(200).json({ message: 'Logged out successfully' });
  } catch (error) {
    sendServerError(res, 'Logout failed', error);
  }
};

const getSession = (req, res) => {
  res.status(200).json({
    user: {
      users_id: req.user.users_id,
      username: req.user.username,
      role: req.user.role,
    },
  });
};

module.exports = {
  signIn,
  logout,
  getSession,
  getDeviceDetails,
  isMissingLoginSessionsTable,
};
