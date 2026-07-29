const express = require('express');
const router = express.Router();
const {
  getLocations,
  createLocation,
  updateLocation,
  deleteLocation,
} = require('../controllers/locationController');
const { verifyToken, requireRole } = require('../middleware/authMiddleware');

router.get('/', verifyToken, getLocations);
router.post('/', verifyToken, requireRole('IT'), createLocation);
router.put('/:id', verifyToken, requireRole('IT'), updateLocation);
router.delete('/:id', verifyToken, requireRole('IT'), deleteLocation);

module.exports = router;
