const Disposal = require('../models/disposal');
const Item = require('../models/item');
const User = require('../models/user');
const Notification = require('../models/notification');
const sequelize = require('../models');
const { Op, literal } = require('sequelize');
const { calculateStatus } = require('./itemController');

const attachApproverDetails = async disposals => {
  const approverIds = [
    ...new Set(
      disposals
        .map(disposal => disposal.approved_by)
        .filter(approvedBy => approvedBy !== null && approvedBy !== undefined)
    ),
  ];

  const approvers = approverIds.length
    ? await User.findAll({
        where: { users_id: { [Op.in]: approverIds } },
        attributes: ['users_id', 'username', 'role'],
      })
    : [];

  const approverById = new Map(
    approvers.map(approver => [Number(approver.users_id), approver.toJSON()])
  );

  return disposals.map(disposal => {
    const disposalData = disposal.toJSON();
    disposalData.approved_by_user =
      approverById.get(Number(disposal.approved_by)) || null;
    return disposalData;
  });
};

// POST create a disposal request (IT/Admin IT action)
const createDisposal = async (req, res) => {
  try {
    const { item_id, reason } = req.body;
    const normalizedItemId = Number(item_id);
    const normalizedReason = typeof reason === 'string' ? reason.trim() : '';

    if (!Number.isInteger(normalizedItemId) || normalizedItemId <= 0) {
      return res.status(400).json({ message: 'A valid item_id is required' });
    }

    if (!normalizedReason) {
      return res.status(400).json({ message: 'A disposal reason is required' });
    }

    if (normalizedReason.length > 255) {
      return res.status(400).json({ message: 'Disposal reason must not exceed 255 characters' });
    }

    const disposal = await sequelize.transaction(async (transaction) => {
      const item = await Item.findByPk(normalizedItemId, {
        transaction,
        lock: transaction.LOCK.UPDATE,
      });

      if (!item) {
        const error = new Error('Item not found');
        error.status = 404;
        throw error;
      }

      if (Number(item.quantity) <= 0) {
        const error = new Error('An item with no available quantity cannot be requested for disposal');
        error.status = 400;
        throw error;
      }

      const existingRequest = await Disposal.findOne({
        where: {
          item_id: normalizedItemId,
          disposal_status: { [Op.in]: ['Pending Approval', 'For Disposal'] },
        },
        transaction,
      });

      if (existingRequest) {
        const error = new Error('This item already has an active disposal request');
        error.status = 409;
        throw error;
      }

      return Disposal.create({
        item_id: normalizedItemId,
        requested_by: req.user.users_id,
        users_id: req.user.users_id,
        request_date: literal('SYSDATETIME()'),
        reason: normalizedReason,
        disposal_status: 'Pending Approval',
      }, { transaction });
    });

    const reviewers = await User.findAll({
      where: { role: { [Op.in]: ['Purchasing', 'Admin IT'] } },
    });
    await Promise.all(
      reviewers.map(user =>
        Notification.create({
          user_id: user.users_id,
          message: 'New disposal request submitted for review.',
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

    if (error.status) {
      return res.status(error.status).json({ message: error.message });
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

    const disposalsWithApprovers = await attachApproverDetails(disposals);
    res.status(200).json(disposalsWithApprovers);
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

    const [disposalWithApprover] = await attachApproverDetails([disposal]);
    res.status(200).json(disposalWithApprover);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch disposal', error: error.message });
  }
};

// PUT approve or reject a pending disposal (Purchasing/Admin IT action)
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

    if (disposal.disposal_status !== 'Pending Approval') {
      return res.status(409).json({
        message: 'Only pending disposal requests can be approved or rejected',
      });
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

// PUT finalize a disposal (IT/Admin IT action - confirms physical disposal)
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
