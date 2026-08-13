
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
const { uploadItemImage, verifyUploadedImage } = require('../middleware/itemImageUpload');
const { writeLimiter } = require('../middleware/rateLimiter');



router.get('/', verifyToken, getItems);
router.get('/:id', verifyToken, getItemById);
router.post('/', verifyToken, requireRole('IT'), writeLimiter, uploadItemImage.single('image'), verifyUploadedImage, createItem);
router.post('/:id/image', verifyToken, requireRole('IT'), writeLimiter, uploadItemImage.single('image'), verifyUploadedImage, saveItemImage);
router.put('/:id', verifyToken, requireRole('IT'), writeLimiter, uploadItemImage.single('image'), verifyUploadedImage, updateItem);
router.delete('/:id', verifyToken, requireRole('IT'), writeLimiter, deleteItem);

module.exports = router;
