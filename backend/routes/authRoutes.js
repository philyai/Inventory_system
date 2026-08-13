const express = require('express');
const router = express.Router();
const { signIn, logout, getSession } = require('../controllers/authController');
const { signInLimiter } = require('../middleware/rateLimiter');
const { verifyToken } = require('../middleware/authMiddleware');

router.post('/signin', signInLimiter, signIn);
router.get('/session', verifyToken, getSession);
router.post('/logout', verifyToken, logout);

module.exports = router;
