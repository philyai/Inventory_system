const ItemLocation = require('../models/itemLocation');
const { Op } = require('sequelize');
const { findFirst } = require('../utils/modelQueries');
const { sendServerError } = require('../utils/httpError');

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
    sendServerError(res, 'Failed to fetch locations', error);
  }
}; 

const createLocation = async (req, res) => {
  try {
    const { location_name, description } = req.body;

    const normalizedName = normalizeLocationName(location_name);
    if (!normalizedName) {
      return res.status(400).json({ message: 'location_name is required' });
    }
    if (normalizedName.length > 100) {
      return res.status(400).json({ message: 'location_name must not exceed 100 characters' });
    }
    if (description !== undefined && (typeof description !== 'string' || description.length > 500)) {
      return res.status(400).json({ message: 'description must be a string of at most 500 characters' });
    }

    const existing = await findFirst(ItemLocation, { where: { location_name: normalizedName } });
    if (existing) {
      return res.status(409).json({ message: 'A location with this name already exists' });
    }

    const newLocation = await ItemLocation.create({ location_name: normalizedName, description });
    res.status(201).json(newLocation);
  } catch (error) {
    sendServerError(res, 'Failed to create location', error);
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
      if (normalizedName.length > 100) {
        return res.status(400).json({ message: 'location_name must not exceed 100 characters' });
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
      if (typeof req.body.description !== 'string' || req.body.description.length > 500) {
        return res.status(400).json({ message: 'description must be a string of at most 500 characters' });
      }
      updates.description = req.body.description;
    }

    if (Object.keys(updates).length === 0) {
      return res.status(400).json({ message: 'No location fields were provided' });
    }

    await location.update(updates);
    res.status(200).json(location);
  } catch (error) {
    sendServerError(res, 'Failed to update location', error);
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
    sendServerError(res, 'Failed to delete location', error);
  }
};

module.exports = { getLocations, createLocation, updateLocation, deleteLocation };
