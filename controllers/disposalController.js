const Disposal = require('../models/disposal');
const Item = require('../models/item');
const User = require('../models/user');
const Notification = require('../models/notification');
const { Op, literal } = require('sequelize');
const { calculateStatus } = require('./itemController');

// POST create a disposal request (Disposal Management -> "request for disposal")
const createDisposal = async (req, res) => {
  try {
    const { item_id, reason } = req.body;

    const item = await Item.findByPk(item_id);
    if (!item) {
      return res.status(404).json({ message: 'Item not found' });
    }

    const disposal = await Disposal.create({
      item_id,
      requested_by: req.user.users_id,
      users_id: req.user.users_id,
      request_date: literal('SYSDATETIME()'),
      reason,
      disposal_status: 'Pending Approval',
    });

    const purchasingUsers = await User.findAll({ where: { role: 'Purchasing' } });
    await Promise.all(
      purchasingUsers.map(u =>
        Notification.create({
          user_id: u.users_id,
          message: `New disposal request submitted for review.`,
          type: 'disposal_requested',
        })
      )
    );

    res.status(201).json(disposal);
  } catch (error) {
    console.error('Create disposal error:', error);
    if (error.parent && error.parent.message) {
      console.error('SQL error detail:', error.parent.message);
    }
    res.status(500).json({
      message: 'Failed to create disposal request',
      error: error.message,
      sqlError: error.parent ? error.parent.message : undefined,
    });
  }
};

// GET all disposals. The Approved UI tab contains both Purchasing-approved
// requests waiting for IT and requests that IT has finished disposing.
const getDisposals = async (req, res) => {
  try {
    const { status } = req.query;
    const where = {};
    if (status === 'Approved') {
      where.disposal_status = { [Op.in]: ['For Disposal', 'Disposed'] };
    } else if (status) {
      where.disposal_status = status;
    }

    const disposals = await Disposal.findAll({
      where,
      include: [Item],
      order: [['request_date', 'DESC']],
    });

    res.status(200).json(disposals);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch disposals', error: error.message });
  }
};

// GET a single disposal by id
const getDisposalById = async (req, res) => {
  try {
    const { id } = req.params;
    const disposal = await Disposal.findByPk(id, { include: [Item] });

    if (!disposal) {
      return res.status(404).json({ message: 'Disposal request not found' });
    }

    res.status(200).json(disposal);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch disposal', error: error.message });
  }
};

// PUT approve or reject a disposal (Purchasing action)
const updateDisposalStatus = async (req, res) => {
  try {
    const { id } = req.params;
    const { disposal_status } = req.body; // 'For Disposal' or 'Rejected'

    if (!['For Disposal', 'Rejected'].includes(disposal_status)) {
      return res.status(400).json({ message: 'disposal_status must be For Disposal or Rejected' });
    }

    const disposal = await Disposal.findByPk(id);
    if (!disposal) {
      return res.status(404).json({ message: 'Disposal request not found' });
    }

    disposal.disposal_status = disposal_status;
    disposal.approved_by = req.user.users_id;
    disposal.approved_date = literal('SYSDATETIME()');
    await disposal.save();

    await Notification.create({
      user_id: disposal.requested_by,
      message: `Your disposal request was ${disposal_status === 'For Disposal' ? 'approved' : 'rejected'}.`,
      type: disposal_status === 'For Disposal' ? 'disposal_approved' : 'disposal_rejected',
    });

    res.status(200).json(disposal);
  } catch (error) {
    res.status(500).json({ message: 'Failed to update disposal status', error: error.message });
  }
};

// PUT finalize a disposal (IT action — confirms physical disposal)
const finalizeDisposal = async (req, res) => {
  try {
    const { id } = req.params;

    const disposal = await Disposal.findByPk(id);
    if (!disposal) {
      return res.status(404).json({ message: 'Disposal request not found' });
    }

    if (disposal.disposal_status !== 'For Disposal') {
      return res.status(400).json({ message: 'Only items in For Disposal status can be finalized' });
    }

    const item = await Item.findByPk(disposal.item_id);
    if (item) {
      const newQuantity = Math.max(item.quantity - 1, 0);
      const newStatus = calculateStatus(newQuantity, item.reorder_level);
      await item.update({ quantity: newQuantity, status: newStatus });
    }

    disposal.disposal_status = 'Disposed';
    disposal.disposed_by = req.user.users_id;
    disposal.disposed_date = literal('GETDATE()');
    await disposal.save();

    res.status(200).json(disposal);
  } catch (error) {
    res.status(500).json({ message: 'Failed to finalize disposal', error: error.message });
  }
};

module.exports = { createDisposal, getDisposals, getDisposalById, updateDisposalStatus, finalizeDisposal };
