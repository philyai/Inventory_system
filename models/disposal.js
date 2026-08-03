const { DataTypes } = require('sequelize');
const sequelize = require('./index');

const Disposal = sequelize.define('Disposal', {
  disposal_id: { type: DataTypes.INTEGER, primaryKey: true, autoIncrement: true },
  item_id: DataTypes.INTEGER,
  disposal_quantity: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: 1,
    validate: { min: 1 },
  },
  requested_by: DataTypes.INTEGER,
  request_date: DataTypes.DATE,
  reason: DataTypes.STRING,
  disposal_status: DataTypes.STRING,
  approved_by: DataTypes.INTEGER,
  approved_date: DataTypes.DATE,
  users_id: DataTypes.INTEGER,
  disposed_by: DataTypes.INTEGER,
  disposed_date: DataTypes.DATE,
}, {
  tableName: 'Disposal',
  timestamps: false,
});

module.exports = Disposal;

