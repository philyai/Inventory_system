
const express = require('express');
const router = express.Router();
const {
  getItems,
  getItemById,
  createItem,
  updateItem,
  deleteItem,
  uploadItemImage: saveItemImage,
} = require('../controllers/itemController');
const { verifyToken, requireRole } = require('../middleware/authMiddleware');
const { uploadItemImage } = require('../middleware/itemImageUpload');
const { writeLimiter } = require('../middleware/rateLimiter');



router.get('/', verifyToken, getItems);
router.get('/:id', verifyToken, getItemById);
router.post('/', verifyToken, requireRole('IT'), writeLimiter, uploadItemImage.single('image'), createItem);
router.post('/:id/image', verifyToken, requireRole('IT'), writeLimiter, uploadItemImage.single('image'), saveItemImage);
router.put('/:id', verifyToken, requireRole('IT'), uploadItemImage.single('image'), updateItem);
router.delete('/:id', verifyToken, requireRole('IT'), deleteItem);

module.exports = router;
