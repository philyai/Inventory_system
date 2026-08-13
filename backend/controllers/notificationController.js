const Notification = require('../models/notification');
const Item = require('../models/item');
const { sendServerError } = require('../utils/httpError');
const MAX_PAGE_SIZE = 20;

// GET the logged-in user's notifications
const getNotifications = async (req, res) => {
  try {
    const { page, limit } = req.query;
    const paginationRequested = page !== undefined || limit !== undefined;
    const pageNumber = page === undefined ? 1 : Number(page);
    const pageSize = limit === undefined ? MAX_PAGE_SIZE : Number(limit);

    if (paginationRequested
        && (!Number.isSafeInteger(pageNumber) || pageNumber < 1)) {
      return res.status(400).json({ message: 'page must be a positive integer' });
    }

    if (paginationRequested
        && (!Number.isSafeInteger(pageSize) || pageSize < 1 || pageSize > MAX_PAGE_SIZE)) {
      return res.status(400).json({
        message: `limit must be an integer between 1 and ${MAX_PAGE_SIZE}`,
      });
    }

    const offset = (pageNumber - 1) * pageSize;
    if (paginationRequested && !Number.isSafeInteger(offset)) {
      return res.status(400).json({ message: 'page is too large' });
    }

    const where = { user_id: req.user.users_id };
    if (req.query.unread_only === 'true') {
      where.is_read = false;
    }

    const notifications = await Notification.findAll({
      where,
      order: [['created_at', 'DESC'], ['notification_id', 'DESC']],
      ...(paginationRequested
        ? { limit: pageSize + 1, offset }
        : {}),
    });

    const hasMore = paginationRequested && notifications.length > pageSize;
    const pageNotifications = paginationRequested
      ? notifications.slice(0, pageSize)
      : notifications;

    const itemIds = [...new Set(pageNotifications
      .map(notification => Number(notification.item_id))
      .filter(itemId => Number.isSafeInteger(itemId) && itemId > 0))];
    const items = itemIds.length > 0
      ? await Item.findAll({
        where: { item_id: itemIds },
        attributes: ['item_id', 'item_name', 'image_url'],
      })
      : [];
    const itemsById = new Map(items.map(item => [Number(item.item_id), item]));
    const responseNotifications = pageNotifications.map(notification => {
      const responseNotification = notification.toJSON();
      const item = itemsById.get(Number(notification.item_id));
      return {
        ...responseNotification,
        item_name: item ? item.item_name : null,
        image_url: item ? item.image_url : null,
      };
    });

    if (paginationRequested) {
      res.set({
        'X-Page': String(pageNumber),
        'X-Page-Size': String(pageSize),
        'X-Has-More': String(hasMore),
      });
    }

    res.status(200).json(responseNotifications);
  } catch (error) {
    sendServerError(res, 'Failed to fetch notifications', error);
  }
};

const getUnreadCount = async (req, res) => {
  try {
    const unreadCount = await Notification.count({
      where: {
        user_id: req.user.users_id,
        is_read: false,
      },
    });

    res.status(200).json({ unread_count: unreadCount });
  } catch (error) {
    sendServerError(res, 'Failed to fetch unread notification count', error);
  }
};

const markAllAsRead = async (req, res) => {
  try {
    const [updatedCount] = await Notification.update(
      { is_read: true },
      {
        where: {
          user_id: req.user.users_id,
          is_read: false,
        },
      }
    );

    res.status(200).json({
      message: 'Notifications marked as read',
      updated_count: updatedCount,
    });
  } catch (error) {
    sendServerError(res, 'Failed to mark notifications as read', error);
  }
};

// PUT mark a notification as read
const markAsRead = async (req, res) => {
  try {
    const notificationId = Number(req.params.id);
    if (!Number.isInteger(notificationId) || notificationId <= 0) {
      return res.status(400).json({ message: 'A valid notification id is required' });
    }

    const notification = await Notification.findByPk(notificationId);

    if (!notification) {
      return res.status(404).json({ message: 'Notification not found' });
    }

    if (Number(notification.user_id) !== Number(req.user.users_id)) {
      return res.status(403).json({ message: 'You do not have permission to modify this notification' });
    }

    notification.is_read = true;
    await notification.save();

    res.status(200).json(notification);
  } catch (error) {
    sendServerError(res, 'Failed to mark notification as read', error);
  }
};

module.exports = { getNotifications, getUnreadCount, markAllAsRead, markAsRead };
