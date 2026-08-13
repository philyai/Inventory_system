const Category = require('../models/category');
const { Op } = require('sequelize');
const { findFirst } = require('../utils/modelQueries');
const { sendServerError } = require('../utils/httpError');

// GET all categories
const getCategories = async (req, res) => {
  try {
    const categories = await Category.findAll({
      order: [['category_name', 'ASC']],
    });
    const visibleCategories = categories.filter(category => {
      const name = String(category.category_name || '').trim().toLowerCase();
      return !['other', 'others'].includes(name);
    });

    res.status(200).json(visibleCategories);
  } catch (error) {
    sendServerError(res, 'Failed to fetch categories', error);
  }
};

const createCategory = async (req, res) => {
  try {
    const { category_name, description } = req.body;

    if (typeof category_name !== 'string' || !category_name.trim()) {
      return res.status(400).json({ message: 'category_name is required' });
    }

    const normalizedName = category_name.trim();
    if (normalizedName.length > 100) {
      return res.status(400).json({ message: 'category_name must not exceed 100 characters' });
    }
    if (description !== undefined && (typeof description !== 'string' || description.length > 500)) {
      return res.status(400).json({ message: 'description must be a string of at most 500 characters' });
    }
    const existing = await findFirst(Category, { where: { category_name: normalizedName } });
    if (existing) {
      return res.status(409).json({ message: 'A category with this name already exists' });
    }

    const newCategory = await Category.create({ category_name: normalizedName, description });
    res.status(201).json(newCategory);
  } catch (error) {
    sendServerError(res, 'Failed to create category', error);
  }
};

const updateCategory = async (req, res) => {
  try {
    const categoryId = Number(req.params.id);
    const { category_name, description } = req.body;

    if (!Number.isInteger(categoryId) || categoryId <= 0) {
      return res.status(400).json({ message: 'A valid category id is required' });
    }

    const category = await Category.findByPk(categoryId);
    if (!category) {
      return res.status(404).json({ message: 'Category not found' });
    }

    const updates = {};
    if (category_name !== undefined) {
      if (typeof category_name !== 'string' || !category_name.trim()) {
        return res.status(400).json({ message: 'category_name cannot be empty' });
      }

      const normalizedName = category_name.trim();
      if (normalizedName.length > 100) {
        return res.status(400).json({ message: 'category_name must not exceed 100 characters' });
      }
      const existing = await findFirst(Category, {
        where: {
          category_name: normalizedName,
          category_id: { [Op.ne]: categoryId },
        },
      });
      if (existing) {
        return res.status(409).json({ message: 'A category with this name already exists' });
      }
      updates.category_name = normalizedName;
    }
    if (description !== undefined) {
      if (typeof description !== 'string' || description.length > 500) {
        return res.status(400).json({ message: 'description must be a string of at most 500 characters' });
      }
      updates.description = description;
    }

    if (Object.keys(updates).length === 0) {
      return res.status(400).json({ message: 'No category fields were provided' });
    }

    await category.update(updates);
    res.status(200).json(category);
  } catch (error) {
    sendServerError(res, 'Failed to update category', error);
  }
};

const deleteCategory = async (req, res) => {
  try {
    const categoryId = Number(req.params.id);
    if (!Number.isInteger(categoryId) || categoryId <= 0) {
      return res.status(400).json({ message: 'A valid category id is required' });
    }

    const category = await Category.findByPk(categoryId);

    if (!category) {
      return res.status(404).json({ message: 'Category not found' });
    }

    await category.destroy();
    res.status(200).json({ message: 'Category deleted successfully' });
  } catch (error) {
    if (error.name === 'SequelizeForeignKeyConstraintError') {
      return res.status(409).json({
        message: 'Cannot delete this category because items are still assigned to it',
      });
    }
    sendServerError(res, 'Failed to delete category', error);
  }
};

module.exports = { getCategories, createCategory, updateCategory, deleteCategory };
