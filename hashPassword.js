const bcrypt = require('bcrypt');

const plainPassword = process.argv[2];

bcrypt.hash(plainPassword, 10).then(hash => {
  console.log('Hashed password:', hash);
});