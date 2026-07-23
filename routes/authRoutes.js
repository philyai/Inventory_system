const express = require('express');
const router = express.Router();
const { signIn, logout } = require('../controllers/authController');
const { signInLimiter } = require('../middleware/rateLimiter');
const { verifyToken } = require('../middleware/authMiddleware');

router.post('/signin', signInLimiter, signIn);
router.post('/logout', verifyToken, logout);

module.exports = router;
