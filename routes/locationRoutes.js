const express = require('express');
const router = express.Router();
const { getLocations, createLocation } = require('../controllers/locationController');
const { verifyToken, requireRole } = require('../middleware/authMiddleware');

router.get('/', verifyToken, getLocations);
router.post('/', verifyToken, requireRole('IT'), createLocation);

module.exports = router;