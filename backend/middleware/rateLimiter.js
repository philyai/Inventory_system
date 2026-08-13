const rateLimit = require('express-rate-limit');

const signInLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 10,                  // 10 requests per IP per window
  standardHeaders: true,
  legacyHeaders: false,
  message: { message: 'Too many login attempts from this IP. Please try again later.' },
  handler: (req, res, next, options) => {
    console.warn(`[RATE LIMIT] ip=${req.ip} path=${req.originalUrl} time=${new Date().toISOString()}`);
    res.status(options.statusCode).json(options.message);
  },
});

const writeLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 60,
  standardHeaders: true,
  legacyHeaders: false,
  message: { message: 'Too many requests. Please slow down and try again later.' },
  handler: (req, res, next, options) => {
    console.warn(`[RATE LIMIT] ip=${req.ip} path=${req.originalUrl} time=${new Date().toISOString()}`);
    res.status(options.statusCode).json(options.message);
  },
});

module.exports = { signInLimiter, writeLimiter };
