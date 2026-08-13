const { fn, col, Op } = require('sequelize');
const Item = require('../models/item');
const Category = require('../models/category');
const Disposal = require('../models/disposal');
const { sendServerError } = require('../utils/httpError');

// GET /dashboard/summary
const getSummary = async (req, res) => {
  try {
    const totalItems = await Item.sum('quantity');
    const totalValue = await Item.sum('total_value');

    const itemsInStock = await Item.sum('quantity', { where: { status: 'In Stock' } });
    const lowStockCount = await Item.count({ where: { status: 'Low Stock' } });
    const forDisposalCount = await Disposal.count({
      where: { disposal_status: { [Op.in]: ['Pending Approval', 'For Disposal'] } },
    });
    const reservedCount = await Item.count({ where: { status: 'Reserved' } });

    res.status(200).json({
      total_items: totalItems || 0,
      total_value: totalValue || 0,
      items_in_stock: itemsInStock || 0,
      low_stock: lowStockCount,
      for_disposal: forDisposalCount,
      reserved: reservedCount,
    });
  } catch (error) {
    sendServerError(res, 'Failed to fetch dashboard summary', error);
  }
};

// GET /dashboard/stock-by-category
const getStockByCategory = async (req, res) => {
  try {
    const results = await Item.findAll({
      attributes: [
        [col('Item.category_id'), 'category_id'],
        [fn('COUNT', col('Item.item_id')), 'item_count'],
        [fn('SUM', col('Item.quantity')), 'total_quantity'],
      ],
      include: [{ model: Category, attributes: ['category_name'] }],
      group: ['Item.category_id', 'Category.category_id', 'Category.category_name'],
    });

    res.status(200).json(results);
  } catch (error) {
    sendServerError(res, 'Failed to fetch stock by category', error);
  }
};

module.exports = { getSummary, getStockByCategory };
