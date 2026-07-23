const express = require('express');
const router = express.Router();
const { getProfile, changePassword, addAccount } = require('../controllers/profileController');
const { verifyToken, requireRole } = require('../middleware/authMiddleware');
const { writeLimiter } = require('../middleware/rateLimiter');

router.get('/', verifyToken, getProfile);
router.put('/change-password', verifyToken, changePassword);
router.post('/add-account', verifyToken, requireRole('Admin IT'), writeLimiter, addAccount);

module.exports = router;
