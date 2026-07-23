const { DataTypes } = require('sequelize');
const sequelize = require('./index');

const ItemMovement = sequelize.define('ItemMovement', {
  movement_id: { type: DataTypes.INTEGER, primaryKey: true, autoIncrement: true },
  item_id: DataTypes.INTEGER,
  movement_type: DataTypes.STRING,
  quantity_change: DataTypes.INTEGER,
  source_destination: DataTypes.STRING,
  remarks: DataTypes.STRING,
  processed_by: DataTypes.INTEGER,
  movement_date: DataTypes.DATE,
}, {
  tableName: 'Item_Movement',
  timestamps: false,
});

module.exports = ItemMovement;