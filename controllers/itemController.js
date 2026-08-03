const Item = require('../models/item');
const Category = require('../models/category');
const ItemLocation = require('../models/itemLocation');
const Disposal = require('../models/disposal');
const ItemRemarkIssue = require('../models/itemRemarkIssue');
const sequelize = require('../models/index');
const { Op, fn, col, where } = require('sequelize');
const crypto = require('crypto');
const fs = require('fs/promises');
const { findFirst } = require('../utils/modelQueries');

const recentItemSubmissions = new Map();
const ITEM_SUBMISSION_TTL_MS = 30 * 1000;
const CLIENT_REQUEST_ID_PATTERN = /^[A-Za-z0-9._:-]{1,100}$/;
const ITEM_REMARKS_MAX_LENGTH = 500;

const remarkIssueInclude = {
  model: ItemRemarkIssue,
  as: 'remark_issue',
  attributes: [
    'issue_id',
    'issue_code',
    'remarks',
    'created_by',
    'created_date',
    'updated_date',
  ],
};

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
    remarks: req.body.remarks,
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
    const { search, category_id, has_remarks } = req.query;

    const where = {};

    if (has_remarks !== undefined && has_remarks !== 'true') {
      return res.status(400).json({ message: 'has_remarks must be true when provided' });
    }

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
          ...remarkIssueInclude,
          required: has_remarks === 'true',
          ...(has_remarks === 'true'
            ? { where: { remarks: { [Op.ne]: '' } } }
            : {}),
        },
        {
          model: Disposal,
          attributes: [
            'disposal_id',
            'disposal_quantity',
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
    let normalizedRemarks;
    if (req.body.remarks !== undefined) {
      if (typeof req.body.remarks !== 'string') {
        return rejectUploadedRequest(res, req.file, 400, 'remarks must be a string');
      }

      normalizedRemarks = req.body.remarks.trim();
      if (normalizedRemarks.length > ITEM_REMARKS_MAX_LENGTH) {
        return rejectUploadedRequest(
          res,
          req.file,
          400,
          `remarks must not exceed ${ITEM_REMARKS_MAX_LENGTH} characters`
        );
      }
    }

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
    updates.status = calculateStatus(nextQuantity, nextReorderLevel);

    if (req.file) {
      updates.image_url = `/uploads/items/${req.file.filename}`;
    }

    const updatedItem = await sequelize.transaction(async transaction => {
      const lockedItem = await Item.findByPk(itemId, {
        transaction,
        lock: transaction.LOCK.UPDATE,
      });

      if (!lockedItem) {
        const error = new Error('Item not found');
        error.status = 404;
        throw error;
      }

      await lockedItem.update(updates, { transaction });

      let remarkIssue = await ItemRemarkIssue.findOne({
        where: { item_id: itemId },
        transaction,
        lock: transaction.LOCK.UPDATE,
      });

      if (normalizedRemarks !== undefined) {
        if (remarkIssue) {
          await remarkIssue.update({
            remarks: normalizedRemarks,
            updated_date: fn('GETDATE'),
          }, { transaction });
        } else if (normalizedRemarks) {
          remarkIssue = await ItemRemarkIssue.create({
            item_id: itemId,
            remarks: normalizedRemarks,
            created_by: req.user.users_id,
            created_date: fn('GETDATE'),
            updated_date: fn('GETDATE'),
          }, {
            fields: [
              'item_id',
              'remarks',
              'created_by',
              'created_date',
              'updated_date',
            ],
            transaction,
          });
        }
      }

      const responseItem = lockedItem.toJSON();
      responseItem.remark_issue = remarkIssue ? remarkIssue.toJSON() : null;
      return responseItem;
    });

    res.status(200).json(updatedItem);
  } catch (error) {
    await removeDuplicateUpload(req.file);
    res.status(error.status || 500).json({
      message: error.status ? error.message : 'Failed to update item',
      error: error.message,
    });
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
      include: [Category, ItemLocation, remarkIssueInclude],
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
  let clientRequestId;

  try {
    const {
      item_name, brand, model, serial_number,
      category_id, category_name, location_id, quantity, reorder_level,
      unit_cost, remarks
    } = req.body;
    const normalizedItemName = typeof item_name === 'string' ? item_name.trim() : '';
    const normalizedQuantity = Number(quantity);
    const normalizedReorderLevel = Number(reorder_level);
    const normalizedUnitCost = Number(unit_cost);
    const normalizedLocationId = Number(location_id);
    const normalizedRemarks = typeof remarks === 'string' ? remarks.trim() : '';

    if (remarks !== undefined && typeof remarks !== 'string') {
      return rejectUploadedRequest(res, req.file, 400, 'remarks must be a string');
    }

    if (normalizedRemarks.length > ITEM_REMARKS_MAX_LENGTH) {
      return rejectUploadedRequest(
        res,
        req.file,
        400,
        `remarks must not exceed ${ITEM_REMARKS_MAX_LENGTH} characters`
      );
    }

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

    const suppliedClientRequestId = req.get('Idempotency-Key');
    if (suppliedClientRequestId) {
      clientRequestId = suppliedClientRequestId.trim();
      if (!CLIENT_REQUEST_ID_PATTERN.test(clientRequestId)) {
        return rejectUploadedRequest(
          res,
          req.file,
          400,
          'Idempotency-Key must be 1 to 100 characters using letters, numbers, dot, underscore, colon, or hyphen'
        );
      }

      const existingItem = await Item.findOne({
        where: {
          created_by: req.user.users_id,
          client_request_id: clientRequestId,
        },
        include: [remarkIssueInclude],
      });

      if (existingItem) {
        await removeDuplicateUpload(req.file);
        res.set('Idempotent-Replayed', 'true');
        return res.status(200).json(existingItem);
      }
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

    const createdItem = await sequelize.transaction(async (transaction) => {
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

      const item = await Item.create({
        item_code, item_name: normalizedItemName, brand, model, serial_number,
        category_id: resolvedCategoryId, location_id: normalizedLocationId,
        quantity: normalizedQuantity, reorder_level: normalizedReorderLevel,
        unit_cost: normalizedUnitCost,
        status: itemStatus,
        image_url: req.file ? `/uploads/items/${req.file.filename}` : null,
        created_by: req.user.users_id,
        client_request_id: clientRequestId || null,
        date_added: new Date(),
      }, {
        fields: [
          'item_code',
          'item_name',
          'brand',
          'model',
          'serial_number',
          'category_id',
          'location_id',
          'quantity',
          'reorder_level',
          'unit_cost',
          'status',
          'image_url',
          'created_by',
          'client_request_id',
          'date_added',
        ],
        transaction,
      });

      let remarkIssue = null;
      if (normalizedRemarks) {
        remarkIssue = await ItemRemarkIssue.create({
          item_id: item.item_id,
          remarks: normalizedRemarks,
          created_by: req.user.users_id,
          created_date: fn('GETDATE'),
          updated_date: fn('GETDATE'),
        }, {
          fields: [
            'item_id',
            'remarks',
            'created_by',
            'created_date',
            'updated_date',
          ],
          transaction,
        });
      }

      const responseItem = item.toJSON();
      responseItem.remark_issue = remarkIssue ? remarkIssue.toJSON() : null;
      return responseItem;
    });

    rememberItemSubmission(submissionKey, createdItem);

    res.status(201).json(createdItem);
  } catch (error) {
    if (submissionKey) {
      recentItemSubmissions.delete(submissionKey);
    }

    if (clientRequestId && error.name === 'SequelizeUniqueConstraintError') {
      const existingItem = await Item.findOne({
        where: {
          created_by: req.user.users_id,
          client_request_id: clientRequestId,
        },
        include: [remarkIssueInclude],
      });

      if (existingItem) {
        await removeDuplicateUpload(req.file);
        res.set('Idempotent-Replayed', 'true');
        return res.status(200).json(existingItem);
      }
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
