const { DataTypes } = require('sequelize');
const sequelize = require('./index');

const LoginSession = sequelize.define('LoginSession', {
  login_session_id: {
    type: DataTypes.BIGINT,
    primaryKey: true,
    autoIncrement: true,
  },
  users_id: {
    type: DataTypes.INTEGER,
    allowNull: false,
  },
  login_time: {
    type: DataTypes.DATE,
    allowNull: false,
  },
  logout_time: {
    type: DataTypes.DATE,
    allowNull: true,
  },
  device_info: {
    type: DataTypes.STRING(500),
    allowNull: false,
  },
  ip_address: {
    type: DataTypes.STRING(64),
    allowNull: true,
  },
  user_agent: {
    type: DataTypes.STRING(1000),
    allowNull: true,
  },
  status: {
    type: DataTypes.STRING(20),
    allowNull: false,
  },
}, {
  tableName: 'login_sessions',
  timestamps: false,
});

module.exports = LoginSession;
