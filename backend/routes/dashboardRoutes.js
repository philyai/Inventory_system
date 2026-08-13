const express = require('express');
const router = express.Router();
const { getSummary, getStockByCategory } = require('../controllers/dashboardController');
const { verifyToken } = require('../middleware/authMiddleware');

router.get('/summary', verifyToken, getSummary);
router.get('/stock-by-category', verifyToken, getStockByCategory);

module.exports = router;