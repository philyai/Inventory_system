const Disposal = require('../models/disposal');
const Item = require('../models/item');
const User = require('../models/user');
const Notification = require('../models/notification');
const sequelize = require('../models');
const { Op, literal } = require('sequelize');
const { calculateStatus } = require('./itemController');
const { findFirst } = require('../utils/modelQueries');

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

      const existingRequest = await findFirst(Disposal, {
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

      const createdDisposal = await Disposal.create({
        item_id: normalizedItemId,
        requested_by: req.user.users_id,
        users_id: req.user.users_id,
        request_date: literal('SYSDATETIME()'),
        reason: normalizedReason,
        disposal_status: 'Pending Approval',
      }, { transaction });

      const reviewers = await User.findAll({
        where: { role: { [Op.in]: ['Purchasing', 'Admin IT'] } },
        attributes: ['users_id'],
        transaction,
      });
      if (reviewers.length > 0) {
        await Notification.bulkCreate(
          reviewers.map(user => ({
            user_id: user.users_id,
            message: 'New disposal request submitted for review.',
            type: 'disposal_requested',
          })),
          { transaction }
        );
      }

      return createdDisposal;
    });

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
    const validStatuses = ['Pending Approval', 'For Disposal', 'Disposed', 'Rejected', 'Approved'];
    if (status && !validStatuses.includes(status)) {
      return res.status(400).json({ message: 'Invalid disposal status filter' });
    }

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
    const disposalId = Number(req.params.id);
    if (!Number.isInteger(disposalId) || disposalId <= 0) {
      return res.status(400).json({ message: 'A valid disposal id is required' });
    }

    const disposal = await Disposal.findByPk(disposalId, { include: [Item] });

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
    const normalizedDisposalId = Number(id);

    if (!Number.isInteger(normalizedDisposalId) || normalizedDisposalId <= 0) {
      return res.status(400).json({ message: 'A valid disposal id is required' });
    }

    if (!['For Disposal', 'Rejected'].includes(disposal_status)) {
      return res.status(400).json({ message: 'disposal_status must be For Disposal or Rejected' });
    }

    const disposal = await sequelize.transaction(async transaction => {
      const lockedDisposal = await Disposal.findByPk(normalizedDisposalId, {
        transaction,
        lock: transaction.LOCK.UPDATE,
      });

      if (!lockedDisposal) {
        const error = new Error('Disposal request not found');
        error.status = 404;
        throw error;
      }

      if (lockedDisposal.disposal_status !== 'Pending Approval') {
        const error = new Error('Only pending disposal requests can be approved or rejected');
        error.status = 409;
        throw error;
      }

      await lockedDisposal.update({
        disposal_status,
        approved_by: req.user.users_id,
        approved_date: literal('SYSDATETIME()'),
      }, { transaction });

      await Notification.create({
        user_id: lockedDisposal.requested_by,
        message: `Your disposal request was ${disposal_status === 'For Disposal' ? 'approved' : 'rejected'}.`,
        type: disposal_status === 'For Disposal' ? 'disposal_approved' : 'disposal_rejected',
      }, { transaction });

      return lockedDisposal;
    });

    res.status(200).json(disposal);
  } catch (error) {
    if (error.status) {
      return res.status(error.status).json({ message: error.message });
    }
    res.status(500).json({ message: 'Failed to update disposal status', error: error.message });
  }
};

// PUT finalize a disposal (IT/Admin IT action - confirms physical disposal)
const finalizeDisposal = async (req, res) => {
  try {
    const { id } = req.params;
    const normalizedDisposalId = Number(id);

    if (!Number.isInteger(normalizedDisposalId) || normalizedDisposalId <= 0) {
      return res.status(400).json({ message: 'A valid disposal id is required' });
    }

    const disposal = await sequelize.transaction(async transaction => {
      const lockedDisposal = await Disposal.findByPk(normalizedDisposalId, {
        transaction,
        lock: transaction.LOCK.UPDATE,
      });

      if (!lockedDisposal) {
        const error = new Error('Disposal request not found');
        error.status = 404;
        throw error;
      }

      if (lockedDisposal.disposal_status !== 'For Disposal') {
        const error = new Error('Only items in For Disposal status can be finalized');
        error.status = 409;
        throw error;
      }

      const item = await Item.findByPk(lockedDisposal.item_id, {
        transaction,
        lock: transaction.LOCK.UPDATE,
      });
      if (!item) {
        const error = new Error('The item for this disposal request no longer exists');
        error.status = 409;
        throw error;
      }

      const newQuantity = Math.max(Number(item.quantity) - 1, 0);
      const newStatus = calculateStatus(newQuantity, Number(item.reorder_level));
      await item.update(
        {
          quantity: newQuantity,
          status: newStatus,
          total_value: newQuantity * Number(item.unit_cost),
        },
        { transaction }
      );

      await lockedDisposal.update({
        disposal_status: 'Disposed',
        disposed_by: req.user.users_id,
        disposed_date: literal('SYSDATETIME()'),
      }, { transaction });

      await Notification.create({
        user_id: lockedDisposal.requested_by,
        message: 'Your approved disposal request has been finalized.',
        type: 'disposal_completed',
      }, { transaction });

      return lockedDisposal;
    });

    res.status(200).json(disposal);
  } catch (error) {
    console.error('Finalize disposal error:', error);

    if (error.status) {
      return res.status(error.status).json({ message: error.message });
    }

    if ([
      'SequelizeConnectionError',
      'SequelizeConnectionAcquireTimeoutError',
      'SequelizeHostNotFoundError',
      'SequelizeHostNotReachableError',
    ].includes(error.name)) {
      return res.status(503).json({
        message: 'Database temporarily unavailable. Please try again shortly.',
      });
    }

    res.status(500).json({ message: 'Failed to finalize disposal', error: error.message });
  }
};

module.exports = { createDisposal, getDisposals, getDisposalById, updateDisposalStatus, finalizeDisposal };
