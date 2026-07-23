const express = require('express');
const router = express.Router();
const { getNotifications, markAsRead } = require('../controllers/notificationController');
const { verifyToken } = require('../middleware/authMiddleware');

router.get('/', verifyToken, getNotifications);
router.put('/:id/read', verifyToken, markAsRead);

module.exports = router;