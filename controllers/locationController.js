const ItemLocation = require('../models/itemLocation');

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

    if (!location_name || !location_name.trim()) {
      return res.status(400).json({ message: 'location_name is required' });
    }

    const normalizedName = location_name.trim();
    const existing = await ItemLocation.findOne({ where: { location_name: normalizedName } });
    if (existing) {
      return res.status(409).json({ message: 'A location with this name already exists' });
    }

    const newLocation = await ItemLocation.create({ location_name: normalizedName, description });
    res.status(201).json(newLocation);
  } catch (error) {
    res.status(500).json({ message: 'Failed to create location', error: error.message });
  }
};

module.exports = { getLocations, createLocation };
