const { DataTypes } = require('sequelize');
const sequelize = require('./index');

const User = sequelize.define('User', {
  users_id: { type: DataTypes.INTEGER, primaryKey: true, autoIncrement: true },
  username: DataTypes.STRING,
  email: DataTypes.STRING,
  password_hash: DataTypes.STRING,
  role: DataTypes.STRING,
  status: DataTypes.STRING,
  token: DataTypes.STRING,
  created_at: DataTypes.DATE,
}, {
  tableName: 'users',
  timestamps: false,
});

module.exports = User;