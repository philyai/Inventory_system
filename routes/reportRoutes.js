const express = require('express');
const router = express.Router();
const {
  getStockSummary,
  getLowStockReport,
  getDisposalReport,
  getCategoryReport,
  getLocationReport,
  getStockMovementReport,
} = require('../controllers/reportController');
const { verifyToken } = require('../middleware/authMiddleware');

router.get('/stock-summary', verifyToken, getStockSummary);
router.get('/low-stock', verifyToken, getLowStockReport);
router.get('/disposal', verifyToken, getDisposalReport);
router.get('/category', verifyToken, getCategoryReport);
router.get('/location', verifyToken, getLocationReport);
router.get('/stock-movement', verifyToken, getStockMovementReport);

module.exports = router;