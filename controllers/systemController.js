const os = require('os');
const sequelize = require('../models');
const packageDetails = require('../package.json');

const getSystemInformation = async (req, res) => {
  let databaseStatus = 'unavailable';

  try {
    await sequelize.authenticate();
    databaseStatus = 'connected';
  } catch (error) {
    databaseStatus = 'unavailable';
  }

  res.status(200).json({
    system_name: process.env.SYSTEM_NAME || 'IT Inventory System',
    application_version: packageDetails.version,
    firmware_version: process.env.FIRMWARE_VERSION || packageDetails.version,
    api_version: packageDetails.version,
    node_version: process.version,
    platform: os.platform(),
    operating_system: `${os.type()} ${os.release()}`,
    architecture: os.arch(),
    database: {
      dialect: sequelize.getDialect(),
      status: databaseStatus,
    },
    server_uptime_seconds: Math.floor(process.uptime()),
  });
};

module.exports = { getSystemInformation };
