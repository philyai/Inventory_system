const express = require('express');
const router = express.Router();
const { getCategories, createCategory, updateCategory, deleteCategory } = require('../controllers/categoryController');
const { verifyToken, requireRole } = require('../middleware/authMiddleware');

router.get('/', verifyToken, getCategories);
router.post('/', verifyToken, requireRole('IT'), createCategory);
router.put('/:id', verifyToken, requireRole('IT'), updateCategory);
router.delete('/:id', verifyToken, requireRole('IT'), deleteCategory);

module.exports = router;
