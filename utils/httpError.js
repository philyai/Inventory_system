const crypto = require('crypto');

function sendServerError(res, message, error) {
  const reference = crypto.randomUUID();
  const errorName = error && error.name ? error.name : 'Error';
  const diagnostic = process.env.NODE_ENV === 'production'
    ? errorName
    : `${errorName}: ${error && error.message ? error.message : 'Unknown error'}`;
  console.error(`[${reference}] ${message}: ${diagnostic}`);
  return res.status(500).json({ message, reference });
}

module.exports = { sendServerError };
