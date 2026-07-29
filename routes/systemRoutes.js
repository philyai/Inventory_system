const express = require('express');
const router = express.Router();
const { getSystemInformation } = require('../controllers/systemController');
const { verifyToken } = require('../middleware/authMiddleware');

router.get('/', verifyToken, getSystemInformation);

module.exports = router;
