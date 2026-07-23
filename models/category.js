const { DataTypes } = require('sequelize');
const sequelize = require('./index');

const Category = sequelize.define('Category', {
  category_id: { type: DataTypes.INTEGER, primaryKey: true, autoIncrement: true },
  category_name: DataTypes.STRING,
  description: DataTypes.STRING,
}, {
  tableName: 'Categories',
  timestamps: false,
});

module.exports = Category;