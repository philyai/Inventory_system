const bcrypt = require('bcrypt');
const validator = require('validator');
const { Op, literal } = require('sequelize');
const User = require('../models/user');
const { findFirst } = require('../utils/modelQueries');
const { sendServerError } = require('../utils/httpError');

const ACCOUNT_ROLES = ['Admin IT', 'IT', 'Purchasing'];

// GET the logged-in user's own profile
const getProfile = async (req, res) => {
  try {
    const user = await User.findByPk(req.user.users_id, {
      attributes: ['users_id', 'username', 'email', 'role', 'status', 'created_at'],
    });

    if (!user) {
      return res.status(404).json({ message: 'User not found' });
    }

    res.status(200).json(user);
  } catch (error) {
    sendServerError(res, 'Failed to fetch profile', error);
  }
};

// PUT change the logged-in user's own password
const changePassword = async (req, res) => {
  try {
    const {
      current_password,
      new_password,
      confirm_password,
    } = req.body;

    if (
      typeof current_password !== 'string' ||
      typeof new_password !== 'string' ||
      typeof confirm_password !== 'string' ||
      !current_password ||
      !new_password ||
      !confirm_password
    ) {
      return res.status(400).json({
        message: 'current_password, new_password, and confirm_password are required',
      });
    }

    if (new_password.length < 8) {
      return res.status(400).json({ message: 'New password must be at least 8 characters' });
    }

    if (new_password.length > 128 || current_password.length > 128) {
      return res.status(400).json({ message: 'Password must not exceed 128 characters' });
    }

    if (new_password !== confirm_password) {
      return res.status(400).json({ message: 'New password and confirmation password do not match' });
    }

    const user = await User.findByPk(req.user.users_id);
    if (!user) {
      return res.status(404).json({ message: 'User not found' });
    }

    const isMatch = await bcrypt.compare(current_password, user.password_hash);
    if (!isMatch) {
      return res.status(401).json({ message: 'Current password is incorrect' });
    }

    const newHash = await bcrypt.hash(new_password, 10);
    user.password_hash = newHash;
    user.token = null;
    await user.save();

    res.status(200).json({
      message: 'Password changed successfully. Please sign in again.',
      requires_reauthentication: true,
    });
  } catch (error) {
    sendServerError(res, 'Failed to change password', error);
  }
};

// POST create a user account (Admin IT only)
const addAccount = async (req, res) => {
  try {
    const { username, email, password, role } = req.body;

    if (
      typeof username !== 'string' ||
      typeof email !== 'string' ||
      typeof password !== 'string' ||
      typeof role !== 'string' ||
      !username.trim() ||
      !email.trim() ||
      !password
    ) {
      return res.status(400).json({
        message: 'username, email, password, and role are required',
      });
    }

    if (password.length < 8) {
      return res.status(400).json({ message: 'Password must be at least 8 characters' });
    }

    if (!ACCOUNT_ROLES.includes(role)) {
      return res.status(400).json({
        message: 'role must be Admin IT, IT, or Purchasing',
      });
    }

    const normalizedUsername = username.trim();
    const normalizedEmail = email.trim().toLowerCase();
    if (normalizedUsername.length > 100) {
      return res.status(400).json({ message: 'Username must not exceed 100 characters' });
    }
    if (normalizedEmail.length > 254 || !validator.isEmail(normalizedEmail)) {
      return res.status(400).json({ message: 'A valid email address is required' });
    }
    if (password.length > 128) {
      return res.status(400).json({ message: 'Password must not exceed 128 characters' });
    }
    const existingUser = await findFirst(User, {
      where: {
        [Op.or]: [
          { username: normalizedUsername },
          { email: normalizedEmail },
        ],
      },
    });

    if (existingUser) {
      return res.status(409).json({ message: 'Username or email is already in use' });
    }

    const passwordHash = await bcrypt.hash(password, 10);
    const newUser = await User.create({
      username: normalizedUsername,
      email: normalizedEmail,
      password_hash: passwordHash,
      role,
      status: 'active',
      token: null,
      created_at: literal('SYSDATETIME()'),
    });

    res.status(201).json({
      message: 'Account created successfully',
      user: {
        users_id: newUser.users_id,
        username: newUser.username,
        email: newUser.email,
        role: newUser.role,
        status: newUser.status,
        created_at: newUser.created_at,
      },
    });
  } catch (error) {
    if (error.name === 'SequelizeUniqueConstraintError') {
      return res.status(409).json({ message: 'Username or email is already in use' });
    }

    sendServerError(res, 'Failed to create account', error);
  }
};

module.exports = { getProfile, changePassword, addAccount };
