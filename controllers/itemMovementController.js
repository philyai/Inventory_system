const sequelize = require('../models/index');
const ItemMovement = require('../models/itemMovement');
const Item = require('../models/item');
const { calculateStatus } = require('./itemController');

const VALID_TYPES = ['In', 'Out', 'Adjustment'];

// POST create a movement (also updates item quantity)
const createMovement = async (req, res) => {
  const t = await sequelize.transaction();
  try {
    const { item_id, movement_type, quantity_change, source_destination, remarks } = req.body;

    if (!VALID_TYPES.includes(movement_type)) {
      await t.rollback();
      return res.status(400).json({ message: 'Invalid movement_type' });
    }

    const item = await Item.findByPk(item_id, { transaction: t, lock: t.LOCK.UPDATE });
    if (!item) {
      await t.rollback();
      return res.status(404).json({ message: 'Item not found' });
    }

    if (item.quantity + quantity_change < 0) {
      await t.rollback();
      return res.status(400).json({ message: 'Insufficient stock for this movement' });
    }

    const movement = await ItemMovement.create({
      item_id,
      movement_type,
      quantity_change,
      source_destination,
      remarks,
      processed_by: req.user.users_id,
      movement_date: new Date(),
    }, { transaction: t });

    const newQuantity = item.quantity + quantity_change;
    const newStatus = calculateStatus(newQuantity, item.reorder_level);

    await item.update(
      { quantity: newQuantity, status: newStatus },
      { transaction: t }
    );

    await t.commit();
    res.status(201).json({ movement, updated_quantity: newQuantity, updated_status: newStatus });
  } catch (error) {
    await t.rollback();
    res.status(500).json({ message: 'Failed to record movement', error: error.message });
  }
};

// GET all movements (supports ?movement_type=In/Out/Adjustment, ?page=, ?limit=)
const getMovements = async (req, res) => {
  try {
    const { movement_type, page = 1, limit = 20 } = req.query;

    const where = {};
    if (movement_type) where.movement_type = movement_type;

    const movements = await ItemMovement.findAll({
      where,
      include: [Item],
      order: [['movement_date', 'DESC']],
      limit: parseInt(limit),
      offset: (parseInt(page) - 1) * parseInt(limit),
    });

    res.status(200).json(movements);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch movements', error: error.message });
  }
};

module.exports = { createMovement, getMovements };