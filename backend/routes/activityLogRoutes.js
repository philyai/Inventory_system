const express = require('express');
const router = express.Router();
const { getActivityLogs } = require('../controllers/activityLogController');
const { verifyToken } = require('../middleware/authMiddleware');

router.get('/', verifyToken, getActivityLogs);

module.exports = router;
