const express = require('express');
const router = express.Router();
const { createDisposal, getDisposals, getDisposalById, updateDisposalStatus, finalizeDisposal } = require('../controllers/disposalController');
const { verifyToken, requireRole } = require('../middleware/authMiddleware');
const { writeLimiter } = require('../middleware/rateLimiter');

router.get('/', verifyToken, getDisposals);
router.get('/:id', verifyToken, getDisposalById);
router.post('/', verifyToken, requireRole('IT'), writeLimiter, createDisposal);
router.put('/:id', verifyToken, requireRole('Purchasing'), writeLimiter, updateDisposalStatus);
router.put('/:id/dispose', verifyToken, requireRole('IT'), writeLimiter, finalizeDisposal);

module.exports = router;
