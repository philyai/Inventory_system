const sequelize = require('../models/index');
const ItemMovement = require('../models/itemMovement');
const Item = require('../models/item');
const { calculateStatus } = require('./itemController');
const { sendServerError } = require('../utils/httpError');

const VALID_TYPES = ['In', 'Out', 'Adjustment'];

// POST create a movement (also updates item quantity)
const createMovement = async (req, res) => {
  try {
    const { item_id, movement_type, quantity_change, source_destination, remarks } = req.body;
    const normalizedItemId = Number(item_id);
    const normalizedQuantityChange = Number(quantity_change);
    const normalizedSourceDestination =
      typeof source_destination === 'string' ? source_destination.trim() : '';

    if (!VALID_TYPES.includes(movement_type)) {
      return res.status(400).json({ message: 'movement_type must be In, Out, or Adjustment' });
    }

    if (!Number.isInteger(normalizedItemId) || normalizedItemId <= 0) {
      return res.status(400).json({ message: 'item_id must be a positive integer' });
    }

    if (!Number.isInteger(normalizedQuantityChange) || normalizedQuantityChange === 0) {
      return res.status(400).json({ message: 'quantity_change must be a non-zero integer' });
    }

    if (
      (movement_type === 'In' && normalizedQuantityChange < 0) ||
      (movement_type === 'Out' && normalizedQuantityChange > 0)
    ) {
      return res.status(400).json({
        message: 'In movements require a positive quantity; Out movements require a negative quantity',
      });
    }

    if (!normalizedSourceDestination) {
      return res.status(400).json({ message: 'source_destination is required' });
    }

    const result = await sequelize.transaction(async transaction => {
      const item = await Item.findByPk(normalizedItemId, {
        transaction,
        lock: transaction.LOCK.UPDATE,
      });
      if (!item) {
        const error = new Error('Item not found');
        error.status = 404;
        throw error;
      }

      const newQuantity = Number(item.quantity) + normalizedQuantityChange;
      if (newQuantity < 0) {
        const error = new Error('Insufficient stock for this movement');
        error.status = 400;
        throw error;
      }

      const movement = await ItemMovement.create({
        item_id: normalizedItemId,
        movement_type,
        quantity_change: normalizedQuantityChange,
        source_destination: normalizedSourceDestination,
        remarks,
        processed_by: req.user.users_id,
        movement_date: new Date(),
      }, { transaction });

      const newStatus = calculateStatus(newQuantity, Number(item.reorder_level));
      await item.update({
        quantity: newQuantity,
        status: newStatus,
      }, { transaction });

      return { movement, updated_quantity: newQuantity, updated_status: newStatus };
    });

    res.status(201).json(result);
  } catch (error) {
    if (error.status) {
      return res.status(error.status).json({ message: error.message });
    }
    sendServerError(res, 'Failed to record movement', error);
  }
};

// GET all movements (supports ?movement_type=In/Out/Adjustment, ?page=, ?limit=)
const getMovements = async (req, res) => {
  try {
    const { movement_type, page = 1, limit = 20 } = req.query;
    const normalizedPage = Number(page);
    const normalizedLimit = Number(limit);

    if (movement_type && !VALID_TYPES.includes(movement_type)) {
      return res.status(400).json({ message: 'movement_type must be In, Out, or Adjustment' });
    }

    if (!Number.isInteger(normalizedPage) || normalizedPage <= 0) {
      return res.status(400).json({ message: 'page must be a positive integer' });
    }

    if (!Number.isInteger(normalizedLimit) || normalizedLimit <= 0 || normalizedLimit > 100) {
      return res.status(400).json({ message: 'limit must be an integer between 1 and 100' });
    }

    const where = {};
    if (movement_type) where.movement_type = movement_type;

    const allMovements = await ItemMovement.findAll({
      where,
      include: [Item],
      order: [['movement_date', 'DESC']],
    });
    const offset = (normalizedPage - 1) * normalizedLimit;
    const movements = allMovements.slice(offset, offset + normalizedLimit);

    res.status(200).json(movements);
  } catch (error) {
    sendServerError(res, 'Failed to fetch movements', error);
  }
};

module.exports = { createMovement, getMovements };
