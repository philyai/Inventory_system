const express = require('express');
const router = express.Router();
const {
  getNotifications,
  getUnreadCount,
  markAllAsRead,
  markAsRead,
} = require('../controllers/notificationController');
const { verifyToken } = require('../middleware/authMiddleware');
const { writeLimiter } = require('../middleware/rateLimiter');

router.get('/', verifyToken, getNotifications);
router.get('/unread-count', verifyToken, getUnreadCount);
router.put('/read-all', verifyToken, writeLimiter, markAllAsRead);
router.put('/:id/read', verifyToken, writeLimiter, markAsRead);

module.exports = router;
