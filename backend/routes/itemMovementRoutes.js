const express = require('express');
const router = express.Router();
const { createMovement, getMovements } = require('../controllers/itemMovementController');
const { verifyToken, requireRole } = require('../middleware/authMiddleware');
const { writeLimiter } = require('../middleware/rateLimiter');

router.get('/', verifyToken, getMovements);
router.post('/', verifyToken, requireRole('IT'), writeLimiter, createMovement);

module.exports = router;
