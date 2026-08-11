const { DataTypes } = require('sequelize');
const sequelize = require('./index');

const Notification = sequelize.define('Notification', {
  notification_id: { type: DataTypes.INTEGER, primaryKey: true, autoIncrement: true },
  // Keep the API-facing name used by the controllers, but map it to the
  // existing SQL Server column.
  user_id: { type: DataTypes.INTEGER, field: 'users_id' },
  item_id: { type: DataTypes.INTEGER, allowNull: true },
  message: DataTypes.STRING,
  type: DataTypes.STRING,
  is_read: { type: DataTypes.BOOLEAN, allowNull: false, defaultValue: false },
  created_at: { type: DataTypes.DATE, defaultValue: sequelize.literal('GETDATE()') },
}, {
  tableName: 'Notifications',
  timestamps: false,
});

module.exports = Notification;
