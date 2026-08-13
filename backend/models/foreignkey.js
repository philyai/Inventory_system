const Category = require('./category');
const ItemLocation = require('./itemLocation');
const Item = require('./item');
const ItemMovement = require('./itemMovement');
const ItemOtherDetails = require('./itemOtherDetails');
const ItemRemarkIssue = require('./itemRemarkIssue');
const Disposal = require('./disposal');
const User = require('./user');
const LoginSession = require('./loginSession');

// Items belongs to Category / Location
Item.belongsTo(Category, { foreignKey: 'category_id' });
Category.hasMany(Item, { foreignKey: 'category_id' });

Item.belongsTo(ItemLocation, { foreignKey: 'location_id' });
ItemLocation.hasMany(Item, { foreignKey: 'location_id' });

// Item Movement belongs to Item
ItemMovement.belongsTo(Item, { foreignKey: 'item_id' });
Item.hasMany(ItemMovement, { foreignKey: 'item_id' });

// Other details belongs to Item
ItemOtherDetails.belongsTo(Item, { foreignKey: 'Item_Id' });
Item.hasMany(ItemOtherDetails, { foreignKey: 'Item_Id' });

// One permanent physical issue code per item record
ItemRemarkIssue.belongsTo(Item, { foreignKey: 'item_id' });
Item.hasOne(ItemRemarkIssue, {
  foreignKey: 'item_id',
  as: 'remark_issue',
  onDelete: 'CASCADE',
});

// Disposal belongs to Item
Disposal.belongsTo(Item, { foreignKey: 'item_id' });
Item.hasMany(Disposal, { foreignKey: 'item_id' });

LoginSession.belongsTo(User, { foreignKey: 'users_id' });
User.hasMany(LoginSession, { foreignKey: 'users_id' });

module.exports = {
  Category, ItemLocation, Item, ItemMovement, ItemOtherDetails, ItemRemarkIssue,
  Disposal, User, LoginSession,
};
