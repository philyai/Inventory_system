const { DataTypes } = require('sequelize');
const sequelize = require('./index');

const Item = sequelize.define('Item', {
  item_id: { type: DataTypes.INTEGER, primaryKey: true, autoIncrement: true },
  item_code: DataTypes.STRING,
  item_name: DataTypes.STRING,
  brand: DataTypes.STRING,
  model: DataTypes.STRING,
  serial_number: DataTypes.STRING,
  category_id: DataTypes.INTEGER,
  location_id: DataTypes.INTEGER,
  quantity: DataTypes.INTEGER,
  reorder_level: DataTypes.INTEGER,
  unit_cost: DataTypes.DECIMAL,
  total_value: { type: DataTypes.DECIMAL, allowNull: true,
},
  status: DataTypes.STRING,
  image_url: DataTypes.TEXT,
  created_by: DataTypes.INTEGER,
  client_request_id: { type: DataTypes.STRING(100), allowNull: true },
  date_added: DataTypes.DATE,
}, {
  tableName: 'Items',
  timestamps: false,
});

module.exports = Item;
