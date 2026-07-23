const { fn, col, literal, Op } = require('sequelize');
const Item = require('../models/item');
const Category = require('../models/category');
const ItemLocation = require('../models/itemLocation');
const ItemMovement = require('../models/itemMovement');
const Disposal = require('../models/disposal');

// GET /reports/stock-summary
const getStockSummary = async (req, res) => {
  try {
    const totalItems = await Item.count();
    const totalQuantity = await Item.sum('quantity');
    const totalValue = await Item.sum('total_value');

    res.status(200).json({
      total_items: totalItems,
      total_quantity: totalQuantity || 0,
      total_value: totalValue || 0,
    });
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch stock summary', error: error.message });
  }
};

// GET /reports/low-stock
const getLowStockReport = async (req, res) => {
  try {
    const items = await Item.findAll({
      where: {
        quantity: { [Op.lte]: col('reorder_level') },
      },
      include: [Category, ItemLocation],
      order: [['quantity', 'ASC']],
    });
    res.status(200).json(items);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch low stock report', error: error.message });
  }
};

// GET /reports/disposal
const getDisposalReport = async (req, res) => {
  try {
    const disposals = await Disposal.findAll({
      include: [Item],
      order: [['request_date', 'DESC']],
    });
    res.status(200).json(disposals);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch disposal report', error: error.message });
  }
};

// GET /reports/category
const getCategoryReport = async (req, res) => {
  try {
    const results = await Item.findAll({
      attributes: [
        [col('Item.category_id'), 'category_id'],
        [fn('COUNT', col('Item.item_id')), 'item_count'],
        [fn('SUM', col('Item.quantity')), 'total_quantity'],
        [fn('SUM', col('Item.total_value')), 'total_value'],
      ],
      include: [{ model: Category, attributes: ['category_name'] }],
      group: ['Item.category_id', 'Category.category_id', 'Category.category_name'],
    });
    res.status(200).json(results);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch category report', error: error.message });
  }
};

// GET /reports/location
const getLocationReport = async (req, res) => {
  try {
    const results = await Item.findAll({
      attributes: [
        [col('Item.location_id'), 'location_id'],
        [fn('COUNT', col('Item.item_id')), 'item_count'],
        [fn('SUM', col('Item.quantity')), 'total_quantity'],
      ],
      include: [{ model: ItemLocation, attributes: ['location_name'] }],
      group: ['Item.location_id', 'ItemLocation.location_id', 'ItemLocation.location_name'],
    });
    res.status(200).json(results);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch location report', error: error.message });
  }
};

// GET /reports/stock-movement
const getStockMovementReport = async (req, res) => {
  try {
    const { start_date, end_date } = req.query;
    const where = {};

    if (start_date && end_date) {
      where.movement_date = { [Op.between]: [start_date, end_date] };
    } else if (start_date) {
      where.movement_date = { [Op.gte]: start_date };
    } else if (end_date) {
      where.movement_date = { [Op.lte]: end_date };
    }

    const movements = await ItemMovement.findAll({
      where,
      include: [Item],
      order: [['movement_date', 'DESC']],
    });

    res.status(200).json(movements);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch stock movement report', error: error.message });
  }
};

module.exports = {
  getStockSummary,
  getLowStockReport,
  getDisposalReport,
  getCategoryReport,
  getLocationReport,
  getStockMovementReport,
};
