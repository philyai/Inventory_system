const express = require('express');
const router = express.Router();
const { getCategories, createCategory, updateCategory, deleteCategory } = require('../controllers/categoryController');
const { verifyToken, requireRole } = require('../middleware/authMiddleware');
const { writeLimiter } = require('../middleware/rateLimiter');

router.get('/', verifyToken, getCategories);
router.post('/', verifyToken, requireRole('IT'), writeLimiter, createCategory);
router.put('/:id', verifyToken, requireRole('IT'), writeLimiter, updateCategory);
router.delete('/:id', verifyToken, requireRole('IT'), writeLimiter, deleteCategory);

module.exports = router;
