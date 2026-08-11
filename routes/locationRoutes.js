const express = require('express');
const router = express.Router();
const {
  getLocations,
  createLocation,
  updateLocation,
  deleteLocation,
} = require('../controllers/locationController');
const { verifyToken, requireRole } = require('../middleware/authMiddleware');
const { writeLimiter } = require('../middleware/rateLimiter');

router.get('/', verifyToken, getLocations);
router.post('/', verifyToken, requireRole('IT'), writeLimiter, createLocation);
router.put('/:id', verifyToken, requireRole('IT'), writeLimiter, updateLocation);
router.delete('/:id', verifyToken, requireRole('IT'), writeLimiter, deleteLocation);

module.exports = router;
