const jwt = require('jsonwebtoken');
const User = require('../models/user');

// checks the token is valid and attaches the user to req
const verifyToken = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization; // expects "Bearer <token>"
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ message: 'No token provided' });
    }

    const token = authHeader.split(' ')[1];

    // verify signature + expiration
    const decoded = jwt.verify(token, process.env.JWT_SECRET);

    // confirm this token matches what's stored for the user (single active session)
    const user = await User.findByPk(decoded.users_id);
    if (!user || user.token !== token) {
      return res.status(401).json({ message: 'Invalid or expired session' });
    }

    req.user = {
      users_id: user.users_id,
      username: user.username,
      role: user.role,
      login_session_id: decoded.login_session_id,
    };
    next();
  } catch (error) {
    return res.status(401).json({ message: 'Invalid or expired token' });
  }
};

// checks the signed-in user has one of the allowed roles
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

module.exports = { verifyToken, requireRole };
