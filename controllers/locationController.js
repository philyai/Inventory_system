const ItemLocation = require('../models/itemLocation');
const { Op } = require('sequelize');
const { findFirst } = require('../utils/modelQueries');

function normalizeLocationName(value) {
  return typeof value === 'string' ? value.trim() : '';
}

const getLocations = async (req, res) => {
  try {
    const locations = await ItemLocation.findAll({
      order: [['location_name', 'ASC']],
    });
    res.status(200).json(locations);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch locations', error: error.message });
  }
}; 

const createLocation = async (req, res) => {
  try {
    const { location_name, description } = req.body;

    const normalizedName = normalizeLocationName(location_name);
    if (!normalizedName) {
      return res.status(400).json({ message: 'location_name is required' });
    }

    const existing = await findFirst(ItemLocation, { where: { location_name: normalizedName } });
    if (existing) {
      return res.status(409).json({ message: 'A location with this name already exists' });
    }

    const newLocation = await ItemLocation.create({ location_name: normalizedName, description });
    res.status(201).json(newLocation);
  } catch (error) {
    res.status(500).json({ message: 'Failed to create location', error: error.message });
  }
};

const updateLocation = async (req, res) => {
  try {
    const locationId = Number(req.params.id);
    if (!Number.isInteger(locationId) || locationId <= 0) {
      return res.status(400).json({ message: 'A valid location id is required' });
    }

    const location = await ItemLocation.findByPk(locationId);
    if (!location) {
      return res.status(404).json({ message: 'Location not found' });
    }

    const updates = {};
    if (req.body.location_name !== undefined) {
      const normalizedName = normalizeLocationName(req.body.location_name);
      if (!normalizedName) {
        return res.status(400).json({ message: 'location_name cannot be empty' });
      }

      const duplicate = await findFirst(ItemLocation, {
        where: {
          location_name: normalizedName,
          location_id: { [Op.ne]: locationId },
        },
      });
      if (duplicate) {
        return res.status(409).json({ message: 'A location with this name already exists' });
      }
      updates.location_name = normalizedName;
    }

    if (req.body.description !== undefined) {
      updates.description = req.body.description;
    }

    if (Object.keys(updates).length === 0) {
      return res.status(400).json({ message: 'No location fields were provided' });
    }

    await location.update(updates);
    res.status(200).json(location);
  } catch (error) {
    res.status(500).json({ message: 'Failed to update location', error: error.message });
  }
};

const deleteLocation = async (req, res) => {
  try {
    const locationId = Number(req.params.id);
    if (!Number.isInteger(locationId) || locationId <= 0) {
      return res.status(400).json({ message: 'A valid location id is required' });
    }

    const location = await ItemLocation.findByPk(locationId);
    if (!location) {
      return res.status(404).json({ message: 'Location not found' });
    }

    await location.destroy();
    res.status(200).json({ message: 'Location deleted successfully' });
  } catch (error) {
    if (error.name === 'SequelizeForeignKeyConstraintError') {
      return res.status(409).json({
        message: 'Cannot delete this location because items are still assigned to it',
      });
    }
    res.status(500).json({ message: 'Failed to delete location', error: error.message });
  }
};

module.exports = { getLocations, createLocation, updateLocation, deleteLocation };
