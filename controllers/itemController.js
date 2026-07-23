const Item = require('../models/item');
const Category = require('../models/category');
const ItemLocation = require('../models/itemLocation');
const { Op } = require('sequelize');

function calculateStatus(quantity, reorderLevel) {
  if (quantity <= 0) return 'Out of Stock';
  if (quantity <= reorderLevel) return 'Low Stock';
  return 'In Stock';
}

function getCategoryPrefix(categoryName) {
  return categoryName.replace(/[^a-zA-Z]/g, '').substring(0, 3).toUpperCase();
}

async function generateItemCode(category_id) {
  const category = await Category.findByPk(category_id);
  if (!category) {
    throw new Error('Invalid category_id');
  }

  const prefix = getCategoryPrefix(category.category_name);

  const lastItem = await Item.findOne({
    where: {
      item_code: { [Op.like]: `${prefix}-%` }
    },
    order: [['item_code', 'DESC']],
  });

  console.log('generateItemCode debug:', {
    prefix,
    lastItemFound: lastItem ? lastItem.item_code : null,
  });

  let nextNumber = 1;
  if (lastItem) {
    const lastNumber = parseInt(lastItem.item_code.split('-')[1], 10);
    if (!isNaN(lastNumber)) {
      nextNumber = lastNumber + 1;
    }
  }

  const paddedNumber = String(nextNumber).padStart(4, '0');
  console.log('generateItemCode result:', `${prefix}-${paddedNumber}`);
  return `${prefix}-${paddedNumber}`;
}

// GET all items
const getItems = async (req, res) => {
  try {
    const { search, category_id } = req.query;

    const where = {};

    if (search) {
      where[Op.or] = [
        { item_name: { [Op.like]: `%${search}%` } },
        { item_code: { [Op.like]: `%${search}%` } },
      ];
    }

    if (category_id) {
      where.category_id = category_id;
    }

    const items = await Item.findAll({
      where,
      include: [Category, ItemLocation],
    });

    res.status(200).json(items);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch items', error: error.message });
  }
};

// PUT/PATCH update an item
const updateItem = async (req, res) => {
  try {
    const { id } = req.params;

    const item = await Item.findByPk(id);
    if (!item) {
      return res.status(404).json({ message: 'Item not found' });
    }

    const {
      item_code, item_name, brand, model, serial_number,
      category_id, location_id, quantity, reorder_level,
      unit_cost
    } = req.body;

    const status = calculateStatus(quantity, reorder_level);

    await item.update({
      item_code, item_name, brand, model, serial_number,
      category_id, location_id, quantity, reorder_level,
      unit_cost, status,
      ...(req.file && { image_url: `/uploads/items/${req.file.filename}` }),
    });

    res.status(200).json(item);
  } catch (error) {
    res.status(500).json({ message: 'Failed to update item', error: error.message });
  }
};

// DELETE an item
const deleteItem = async (req, res) => {
  try {
    const { id } = req.params;

    const item = await Item.findByPk(id);
    if (!item) {
      return res.status(404).json({ message: 'Item not found' });
    }

    await item.destroy();

    res.status(200).json({ message: 'Item deleted successfully' });
  } catch (error) {
    if (error.name === 'SequelizeForeignKeyConstraintError') {
      return res.status(409).json({
        message: 'Cannot delete this item because it has related movement, disposal, or other records. Remove those first.',
      });
    }
    res.status(500).json({ message: 'Failed to delete item', error: error.message });
  }
};

// GET one item by id
const getItemById = async (req, res) => {
  try {
    const { id } = req.params;
    const item = await Item.findByPk(id, {
      include: [Category, ItemLocation],
    });
    if (!item) {
      return res.status(404).json({ message: 'Item not found' });
    }
    res.status(200).json(item);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch item', error: error.message });
  }
};

// POST upload or replace an item's image
const uploadItemImage = async (req, res) => {
  try {
    const { id } = req.params;

    if (!req.file) {
      return res.status(400).json({ message: 'Image file is required' });
    }

    const item = await Item.findByPk(id);
    if (!item) {
      return res.status(404).json({ message: 'Item not found' });
    }

    item.image_url = `/uploads/items/${req.file.filename}`;
    await item.save();

    res.status(200).json(item);
  } catch (error) {
    res.status(500).json({ message: 'Failed to upload item image', error: error.message });
  }
};

// POST create new item
const createItem = async (req, res) => {
  try {
    const {
      item_name, brand, model, serial_number,
      category_id, location_id, quantity, reorder_level,
      unit_cost, status
    } = req.body;

    const item_code = await generateItemCode(category_id);

    const newItem = await Item.create({
      item_code, item_name, brand, model, serial_number,
      category_id, location_id, quantity, reorder_level,
      unit_cost, status,
      image_url: req.file ? `/uploads/items/${req.file.filename}` : null,
      created_by: req.user.users_id,
      date_added: new Date(),
    });
    res.status(201).json(newItem);
  } catch (error) {
    console.error('Create item error:', error);
    if (error.errors) {
      error.errors.forEach(e => console.error(' -', e.message, '| field:', e.path));
    }
    res.status(500).json({
      message: 'Failed to create item',
      error: error.message,
      details: error.errors ? error.errors.map(e => ({ field: e.path, message: e.message })) : undefined,
    });
  }
};

module.exports = {
  getItems,
  getItemById,
  createItem,
  updateItem,
  deleteItem,
  uploadItemImage,
  calculateStatus,
};
