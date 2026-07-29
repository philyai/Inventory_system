const express = require('express');
const router = express.Router();
const {
  getNotifications,
  getUnreadCount,
  markAllAsRead,
  markAsRead,
} = require('../controllers/notificationController');
const { verifyToken } = require('../middleware/authMiddleware');

router.get('/', verifyToken, getNotifications);
router.get('/unread-count', verifyToken, getUnreadCount);
router.put('/read-all', verifyToken, markAllAsRead);
router.put('/:id/read', verifyToken, markAsRead);

module.exports = router;
