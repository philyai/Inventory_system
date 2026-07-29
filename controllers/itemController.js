const Item = require('../models/item');
const Category = require('../models/category');
const ItemLocation = require('../models/itemLocation');
const Disposal = require('../models/disposal');
const sequelize = require('../models/index');
const { Op, fn, col, where } = require('sequelize');
const crypto = require('crypto');
const fs = require('fs/promises');
const { findFirst } = require('../utils/modelQueries');

const recentItemSubmissions = new Map();
const ITEM_SUBMISSION_TTL_MS = 30 * 1000;

function rememberItemSubmission(key, item) {
  const record = {
    expiresAt: Date.now() + ITEM_SUBMISSION_TTL_MS,
    item,
  };
  recentItemSubmissions.set(key, record);

  const cleanupTimer = setTimeout(() => {
    if (recentItemSubmissions.get(key) === record) {
      recentItemSubmissions.delete(key);
    }
  }, ITEM_SUBMISSION_TTL_MS);
  cleanupTimer.unref();
}

function getItemSubmissionKey(req) {
  const suppliedKey = req.get('Idempotency-Key');
  if (suppliedKey) {
    return `${req.user.users_id}:key:${suppliedKey}`;
  }

  const submittedItem = {
    userId: req.user.users_id,
    item_name: req.body.item_name,
    brand: req.body.brand,
    model: req.body.model,
    serial_number: req.body.serial_number,
    category_id: req.body.category_id,
    category_name: req.body.category_name,
    location_id: req.body.location_id,
    quantity: req.body.quantity,
    reorder_level: req.body.reorder_level,
    unit_cost: req.body.unit_cost,
    file_name: req.file?.originalname,
    file_size: req.file?.size,
  };

  return crypto
    .createHash('sha256')
    .update(JSON.stringify(submittedItem))
    .digest('hex');
}

async function removeDuplicateUpload(file) {
  if (!file?.path) return;

  try {
    await fs.unlink(file.path);
  } catch (error) {
    if (error.code !== 'ENOENT') {
      console.error('Failed to remove duplicate item upload:', error.message);
    }
  }
}

async function rejectUploadedRequest(res, file, status, message) {
  await removeDuplicateUpload(file);
  return res.status(status).json({ message });
}

function calculateStatus(quantity, reorderLevel) {
  if (quantity <= 0) return 'Out of Stock';
  if (quantity <= reorderLevel) return 'Low Stock';
  return 'In Stock';
}

function getCategoryPrefix(categoryName) {
  return categoryName.replace(/[^a-zA-Z]/g, '').substring(0, 3).toUpperCase();
}

async function generateItemCode(category_id, transaction) {
  const category = await Category.findByPk(category_id, { transaction });
  if (!category) {
    const error = new Error('Invalid category_id');
    error.status = 400;
    throw error;
  }

  const prefix = getCategoryPrefix(category.category_name);

  const lastItem = await findFirst(Item, {
    where: {
      item_code: { [Op.like]: `${prefix}-%` }
    },
    order: [['item_code', 'DESC']],
    transaction,
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
      include: [
        Category,
        ItemLocation,
        {
          model: Disposal,
          attributes: [
            'disposal_id',
            'reason',
            'disposal_status',
            'request_date',
            'approved_date',
            'disposed_date',
          ],
        },
      ],
    });

    const visibleItems = items
      .map(item => {
        const itemData = item.toJSON();
        const activeDisposal = itemData.Disposals.find(disposal =>
          ['Pending Approval', 'For Disposal'].includes(disposal.disposal_status)
        );

        delete itemData.Disposals;
        itemData.active_disposal = activeDisposal || null;
        return itemData;
      });

    res.status(200).json(visibleItems);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch items', error: error.message });
  }
};

// PUT/PATCH update an item
const updateItem = async (req, res) => {
  try {
    const itemId = Number(req.params.id);
    if (!Number.isInteger(itemId) || itemId <= 0) {
      return rejectUploadedRequest(res, req.file, 400, 'A valid item id is required');
    }

    const item = await Item.findByPk(itemId);
    if (!item) {
      return rejectUploadedRequest(res, req.file, 404, 'Item not found');
    }

    const updates = {};
    for (const field of ['item_code', 'brand', 'model', 'serial_number']) {
      if (req.body[field] !== undefined) updates[field] = req.body[field];
    }

    if (req.body.item_name !== undefined) {
      const itemName = typeof req.body.item_name === 'string' ? req.body.item_name.trim() : '';
      if (!itemName) {
        return rejectUploadedRequest(res, req.file, 400, 'item_name cannot be empty');
      }
      updates.item_name = itemName;
    }

    for (const field of ['category_id', 'location_id']) {
      if (req.body[field] !== undefined) {
        const value = Number(req.body[field]);
        if (!Number.isInteger(value) || value <= 0) {
          return rejectUploadedRequest(res, req.file, 400, `${field} must be a positive integer`);
        }
        updates[field] = value;
      }
    }

    for (const field of ['quantity', 'reorder_level']) {
      if (req.body[field] !== undefined) {
        const value = Number(req.body[field]);
        if (!Number.isInteger(value) || value < 0) {
          return rejectUploadedRequest(res, req.file, 400, `${field} must be a non-negative integer`);
        }
        updates[field] = value;
      }
    }

    if (req.body.unit_cost !== undefined) {
      const unitCost = Number(req.body.unit_cost);
      if (!Number.isFinite(unitCost) || unitCost < 0) {
        return rejectUploadedRequest(res, req.file, 400, 'unit_cost must be a non-negative number');
      }
      updates.unit_cost = unitCost;
    }

    if (updates.category_id) {
      const category = await Category.findByPk(updates.category_id);
      if (!category) {
        return rejectUploadedRequest(res, req.file, 400, 'Invalid category_id');
      }
    }

    if (updates.location_id) {
      const location = await ItemLocation.findByPk(updates.location_id);
      if (!location) {
        return rejectUploadedRequest(res, req.file, 400, 'Invalid location_id');
      }
    }

    const nextQuantity = updates.quantity ?? Number(item.quantity);
    const nextReorderLevel = updates.reorder_level ?? Number(item.reorder_level);
    const nextUnitCost = updates.unit_cost ?? Number(item.unit_cost);
    updates.status = calculateStatus(nextQuantity, nextReorderLevel);
    updates.total_value = nextQuantity * nextUnitCost;

    if (req.file) {
      updates.image_url = `/uploads/items/${req.file.filename}`;
    }

    await item.update(updates);

    res.status(200).json(item);
  } catch (error) {
    await removeDuplicateUpload(req.file);
    res.status(500).json({ message: 'Failed to update item', error: error.message });
  }
};

// DELETE an item
const deleteItem = async (req, res) => {
  try {
    const itemId = Number(req.params.id);
    if (!Number.isInteger(itemId) || itemId <= 0) {
      return res.status(400).json({ message: 'A valid item id is required' });
    }

    const item = await Item.findByPk(itemId);
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
    const itemId = Number(req.params.id);
    if (!Number.isInteger(itemId) || itemId <= 0) {
      return res.status(400).json({ message: 'A valid item id is required' });
    }

    const item = await Item.findByPk(itemId, {
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

    const itemId = Number(id);
    if (!Number.isInteger(itemId) || itemId <= 0) {
      return rejectUploadedRequest(res, req.file, 400, 'A valid item id is required');
    }

    const item = await Item.findByPk(itemId);
    if (!item) {
      return rejectUploadedRequest(res, req.file, 404, 'Item not found');
    }

    item.image_url = `/uploads/items/${req.file.filename}`;
    await item.save();

    res.status(200).json(item);
  } catch (error) {
    await removeDuplicateUpload(req.file);
    res.status(500).json({ message: 'Failed to upload item image', error: error.message });
  }
};

// POST create new item
const createItem = async (req, res) => {
  let submissionKey;

  try {
    const {
      item_name, brand, model, serial_number,
      category_id, category_name, location_id, quantity, reorder_level,
      unit_cost
    } = req.body;
    const normalizedItemName = typeof item_name === 'string' ? item_name.trim() : '';
    const normalizedQuantity = Number(quantity);
    const normalizedReorderLevel = Number(reorder_level);
    const normalizedUnitCost = Number(unit_cost);
    const normalizedLocationId = Number(location_id);

    const normalizedCategoryName =
      typeof category_name === 'string' ? category_name.trim() : '';
    const usesOtherCategory = ['other', 'others'].includes(
      String(category_id || '').toLowerCase()
    );

    if (usesOtherCategory && !normalizedCategoryName) {
      return rejectUploadedRequest(
        res,
        req.file,
        400,
        'category_name is required when category_id is Others'
      );
    }

    if (!category_id && !normalizedCategoryName) {
      return rejectUploadedRequest(res, req.file, 400, 'category_id or category_name is required');
    }

    if (!normalizedItemName) {
      return rejectUploadedRequest(res, req.file, 400, 'item_name is required');
    }

    if (!Number.isInteger(normalizedLocationId) || normalizedLocationId <= 0) {
      return rejectUploadedRequest(res, req.file, 400, 'location_id must be a positive integer');
    }

    if (!Number.isInteger(normalizedQuantity) || normalizedQuantity < 0) {
      return rejectUploadedRequest(res, req.file, 400, 'quantity must be a non-negative integer');
    }

    if (!Number.isInteger(normalizedReorderLevel) || normalizedReorderLevel < 0) {
      return rejectUploadedRequest(res, req.file, 400, 'reorder_level must be a non-negative integer');
    }

    if (!Number.isFinite(normalizedUnitCost) || normalizedUnitCost < 0) {
      return rejectUploadedRequest(res, req.file, 400, 'unit_cost must be a non-negative number');
    }

    submissionKey = getItemSubmissionKey(req);
    const previousSubmission = recentItemSubmissions.get(submissionKey);
    if (previousSubmission && previousSubmission.expiresAt > Date.now()) {
      await removeDuplicateUpload(req.file);

      if (previousSubmission.item) {
        res.set('Idempotent-Replayed', 'true');
        return res.status(200).json(previousSubmission.item);
      }

      return res.status(409).json({
        message: 'This item is already being saved. Please wait for the first request to finish.',
      });
    }

    rememberItemSubmission(submissionKey, null);

    const newItem = await sequelize.transaction(async (transaction) => {
      let resolvedCategoryId = category_id;
      const location = await ItemLocation.findByPk(normalizedLocationId, { transaction });
      if (!location) {
        const error = new Error('Invalid location_id');
        error.status = 400;
        throw error;
      }

      if (normalizedCategoryName) {
        let category = await findFirst(Category, {
          where: where(
            fn('LOWER', col('category_name')),
            normalizedCategoryName.toLowerCase()
          ),
          transaction,
        });

        if (!category) {
          category = await Category.create({
            category_name: normalizedCategoryName,
          }, { transaction });
        }

        resolvedCategoryId = category.category_id;
      } else {
        resolvedCategoryId = Number(category_id);
        if (!Number.isInteger(resolvedCategoryId) || resolvedCategoryId <= 0) {
          const error = new Error('category_id must be a positive integer');
          error.status = 400;
          throw error;
        }
      }

      const item_code = await generateItemCode(resolvedCategoryId, transaction);
      const itemStatus = calculateStatus(normalizedQuantity, normalizedReorderLevel);

      return Item.create({
        item_code, item_name: normalizedItemName, brand, model, serial_number,
        category_id: resolvedCategoryId, location_id: normalizedLocationId,
        quantity: normalizedQuantity, reorder_level: normalizedReorderLevel,
        unit_cost: normalizedUnitCost,
        total_value: normalizedQuantity * normalizedUnitCost,
        status: itemStatus,
        image_url: req.file ? `/uploads/items/${req.file.filename}` : null,
        created_by: req.user.users_id,
        date_added: new Date(),
      }, { transaction });
    });

    rememberItemSubmission(submissionKey, newItem.toJSON());

    res.status(201).json(newItem);
  } catch (error) {
    if (submissionKey) {
      recentItemSubmissions.delete(submissionKey);
    }
    await removeDuplicateUpload(req.file);

    console.error('Create item error:', error);
    if (error.errors) {
      error.errors.forEach(e => console.error(' -', e.message, '| field:', e.path));
    }
    res.status(error.status || 500).json({
      message: error.status ? error.message : 'Failed to create item',
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
