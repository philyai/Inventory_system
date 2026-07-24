const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const User = require('../models/user');

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

const signIn = async (req, res) => {
  const { username, password } = req.body;
  const ip = req.ip;

  try {
    const record = getRecord(username);
    if (record.lockUntil && record.lockUntil > Date.now()) {
      const secondsLeft = Math.ceil((record.lockUntil - Date.now()) / 1000);
      console.warn(`[LOGIN LOCKED] username="${username}" ip=${ip}`);
      return res.status(429).json({
        message: `Too many failed attempts. Try again in ${secondsLeft}s.`,
      });
    }

    const user = await User.findOne({ where: { username } });
    if (!user) {
      registerFailure(username);
      console.warn(`[LOGIN FAIL] username="${username}" ip=${ip} reason=no_such_user`);
      return res.status(401).json({ message: 'Invalid username or password' });
    }

    if (user.status !== 'active') {
      return res.status(403).json({ message: 'This account is inactive. Contact an administrator.' });
    }

    const isMatch = await bcrypt.compare(password, user.password_hash);
    if (!isMatch) {
      registerFailure(username);
      console.warn(`[LOGIN FAIL] username="${username}" ip=${ip} reason=bad_password`);
      return res.status(401).json({ message: 'Invalid username or password' });
    }

    // success — clear any accumulated failures
    clearAttempts(username);

    const token = jwt.sign(
      { users_id: user.users_id, username: user.username, role: user.role, email: user.email },
      process.env.JWT_SECRET,
      { expiresIn: '168h' }
    );

    user.token = token;
    await user.save();

    res.status(200).json({
      token,
      user: {
        users_id: user.users_id,
        username: user.username,
        role: user.role,
      },
    });
  } catch (error) {
    res.status(500).json({ message: 'Sign in failed', error: error.message });
  }
};

const logout = async (req, res) => {
  try {
    const user = await User.findByPk(req.user.users_id);
    if (!user) {
      return res.status(404).json({ message: 'User not found' });
    }

    user.token = null;
    await user.save();

    res.status(200).json({ message: 'Logged out successfully' });
  } catch (error) {
    res.status(500).json({ message: 'Logout failed', error: error.message });
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

module.exports = { signIn, logout, getSession };
