const { DataTypes } = require('sequelize');
const sequelize = require('./index');

const ItemRemarkIssue = sequelize.define('ItemRemarkIssue', {
  issue_id: { type: DataTypes.INTEGER, primaryKey: true, autoIncrement: true },
  issue_code: { type: DataTypes.STRING(30), allowNull: false },
  item_id: { type: DataTypes.INTEGER, allowNull: false, unique: true },
  remarks: { type: DataTypes.STRING(500), allowNull: false },
  created_by: { type: DataTypes.INTEGER, allowNull: false },
  created_date: { type: DataTypes.DATE, allowNull: false },
  updated_date: { type: DataTypes.DATE, allowNull: false },
}, {
  tableName: 'Item_Remark_Issue',
  timestamps: false,
});

module.exports = ItemRemarkIssue;
