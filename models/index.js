const { Sequelize } = require('sequelize');

function getPositiveNumber(value, fallback) {
  const parsedValue = Number(value);
  return Number.isFinite(parsedValue) && parsedValue > 0 ? parsedValue : fallback;
}

const sequelize = new Sequelize(
  process.env.DB_NAME,
  process.env.DB_USER,
  process.env.DB_PASSWORD,
  {
    host: process.env.DB_HOST,
    port: process.env.DB_PORT,
    dialect: 'mssql',
    logging: false,
    pool: {
      acquire: getPositiveNumber(process.env.DB_POOL_ACQUIRE_TIMEOUT_MS, 15000),
    },
    dialectOptions: {
      options: {
        encrypt: false,
        trustServerCertificate: true,
        connectTimeout: getPositiveNumber(process.env.DB_CONNECT_TIMEOUT_MS, 15000),
        requestTimeout: getPositiveNumber(process.env.DB_REQUEST_TIMEOUT_MS, 15000),
      },
    },
  }
);

module.exports = sequelize;
