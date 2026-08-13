const { DataTypes } = require('sequelize');
const sequelize = require('./index');

const ItemLocation = sequelize.define('ItemLocation', {
  location_id: { type: DataTypes.INTEGER, primaryKey: true, autoIncrement: true },
  location_name: DataTypes.STRING,
  description: DataTypes.STRING,
}, {
  tableName: 'Item_Location',
  timestamps: false,
});

module.exports = ItemLocation;