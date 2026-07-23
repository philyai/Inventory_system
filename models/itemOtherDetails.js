const { DataTypes } = require('sequelize');
const sequelize = require('./index');

const ItemOtherDetails = sequelize.define('ItemOtherDetails', {
  Others_Id: { type: DataTypes.INTEGER, primaryKey: true, autoIncrement: true },
  Item_Id: DataTypes.INTEGER,
  DetailsName: DataTypes.STRING,
}, {
  tableName: 'Item_Other_details',
  timestamps: false,
});

module.exports = ItemOtherDetails;