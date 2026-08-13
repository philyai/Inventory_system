const { fn, col, literal, Op } = require('sequelize');
const Item = require('../models/item');
const Category = require('../models/category');
const ItemLocation = require('../models/itemLocation');
const ItemMovement = require('../models/itemMovement');
const Disposal = require('../models/disposal');
const { sendServerError } = require('../utils/httpError');
const MAX_PAGE_SIZE = 20;

function isValidDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(value);
}

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
    sendServerError(res, 'Failed to fetch stock summary', error);
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
    sendServerError(res, 'Failed to fetch low stock report', error);
  }
};

// GET /reports/disposal
const getDisposalReport = async (req, res) => {
  try {
    const disposals = await Disposal.findAll({
      where: { disposal_status: 'Disposed' },
      include: [{
        model: Item,
        attributes: ['item_id', 'item_code', 'item_name', 'unit_cost'],
      }],
      order: [['disposed_date', 'DESC']],
    });

    const disposedItems = disposals.reduce(
      (total, disposal) => total + Number(disposal.disposal_quantity || 0),
      0
    );
    const valueWrittenOff = disposals.reduce((total, disposal) => {
      return total
        + (Number(disposal.disposal_quantity || 0) * Number(disposal.Item?.unit_cost || 0));
    }, 0);

    res.status(200).json({
      summary: {
        disposed_items: disposedItems,
        disposal_requests: disposals.length,
        value_written_off: valueWrittenOff,
      },
      disposals,
    });
  } catch (error) {
    sendServerError(res, 'Failed to fetch disposal report', error);
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
    sendServerError(res, 'Failed to fetch category report', error);
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
        [fn('SUM', col('Item.total_value')), 'total_value'],
      ],
      include: [{ model: ItemLocation, attributes: ['location_name', 'description'] }],
      group: [
        'Item.location_id',
        'ItemLocation.location_id',
        'ItemLocation.location_name',
        'ItemLocation.description',
      ],
    });
    res.status(200).json(results);
  } catch (error) {
    sendServerError(res, 'Failed to fetch location report', error);
  }
};

// GET /reports/stock-movement
const getStockMovementReport = async (req, res) => {
  try {
    const { start_date, end_date, movement_type, page, limit } = req.query;
    const paginationRequested = page !== undefined || limit !== undefined;
    const pageNumber = page === undefined ? 1 : Number(page);
    const pageSize = limit === undefined ? MAX_PAGE_SIZE : Number(limit);
    const where = {};

    if (paginationRequested
        && (!Number.isSafeInteger(pageNumber) || pageNumber < 1)) {
      return res.status(400).json({ message: 'page must be a positive integer' });
    }

    if (paginationRequested
        && (!Number.isSafeInteger(pageSize) || pageSize < 1 || pageSize > MAX_PAGE_SIZE)) {
      return res.status(400).json({
        message: `limit must be an integer between 1 and ${MAX_PAGE_SIZE}`,
      });
    }

    const offset = (pageNumber - 1) * pageSize;
    if (paginationRequested && !Number.isSafeInteger(offset)) {
      return res.status(400).json({ message: 'page is too large' });
    }

    if (movement_type && !['In', 'Out', 'Adjustment'].includes(movement_type)) {
      return res.status(400).json({ message: 'Invalid movement_type filter' });
    }

    if (movement_type) {
      where.movement_type = movement_type;
    }

    if (start_date && !isValidDate(start_date)) {
      return res.status(400).json({ message: 'start_date must use YYYY-MM-DD format' });
    }

    if (end_date && !isValidDate(end_date)) {
      return res.status(400).json({ message: 'end_date must use YYYY-MM-DD format' });
    }

    if (start_date && end_date && start_date > end_date) {
      return res.status(400).json({ message: 'start_date must not be after end_date' });
    }

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
      order: [['movement_date', 'DESC'], ['movement_id', 'DESC']],
      ...(paginationRequested
        ? { limit: pageSize + 1, offset }
        : {}),
    });

    const hasMore = paginationRequested && movements.length > pageSize;
    const pageMovements = paginationRequested ? movements.slice(0, pageSize) : movements;

    if (paginationRequested) {
      res.set({
        'X-Page': String(pageNumber),
        'X-Page-Size': String(pageSize),
        'X-Has-More': String(hasMore),
      });
    }

    res.status(200).json(pageMovements);
  } catch (error) {
    sendServerError(res, 'Failed to fetch stock movement report', error);
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
