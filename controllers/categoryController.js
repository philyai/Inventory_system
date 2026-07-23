const Category = require('../models/category');
const { Op } = require('sequelize');

// GET all categories
const getCategories = async (req, res) => {
  try {
    const categories = await Category.findAll({
      order: [['category_name', 'ASC']],
    });
    res.status(200).json(categories);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch categories', error: error.message });
  }
};

const createCategory = async (req, res) => {
  try {
    const { category_name, description } = req.body;

    if (!category_name || !category_name.trim()) {
      return res.status(400).json({ message: 'category_name is required' });
    }

    const normalizedName = category_name.trim();
    const existing = await Category.findOne({ where: { category_name: normalizedName } });
    if (existing) {
      return res.status(409).json({ message: 'A category with this name already exists' });
    }

    const newCategory = await Category.create({ category_name: normalizedName, description });
    res.status(201).json(newCategory);
  } catch (error) {
    res.status(500).json({ message: 'Failed to create category', error: error.message });
  }
};

const updateCategory = async (req, res) => {
  try {
    const { id } = req.params;
    const { category_name, description } = req.body;

    const category = await Category.findByPk(id);
    if (!category) {
      return res.status(404).json({ message: 'Category not found' });
    }

    const updates = {};
    if (category_name !== undefined) {
      if (!category_name.trim()) {
        return res.status(400).json({ message: 'category_name cannot be empty' });
      }

      const normalizedName = category_name.trim();
      const existing = await Category.findOne({
        where: {
          category_name: normalizedName,
          category_id: { [Op.ne]: id },
        },
      });
      if (existing) {
        return res.status(409).json({ message: 'A category with this name already exists' });
      }
      updates.category_name = normalizedName;
    }
    if (description !== undefined) updates.description = description;

    await category.update(updates);
    res.status(200).json(category);
  } catch (error) {
    res.status(500).json({ message: 'Failed to update category', error: error.message });
  }
};

const deleteCategory = async (req, res) => {
  try {
    const { id } = req.params;
    const category = await Category.findByPk(id);

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
    res.status(500).json({ message: 'Failed to delete category', error: error.message });
  }
};

module.exports = { getCategories, createCategory, updateCategory, deleteCategory };
