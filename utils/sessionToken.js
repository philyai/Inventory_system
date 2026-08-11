const crypto = require('crypto');

function fingerprintSessionToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

module.exports = { fingerprintSessionToken };
