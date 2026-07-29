const Notification = require('../models/notification');

// GET the logged-in user's notifications
const getNotifications = async (req, res) => {
  try {
    const where = { user_id: req.user.users_id };
    if (req.query.unread_only === 'true') {
      where.is_read = false;
    }

    const notifications = await Notification.findAll({
      where,
      order: [['created_at', 'DESC']],
    });
    res.status(200).json(notifications);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch notifications', error: error.message });
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
    res.status(500).json({ message: 'Failed to fetch unread notification count', error: error.message });
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
    res.status(500).json({ message: 'Failed to mark notifications as read', error: error.message });
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
    res.status(500).json({ message: 'Failed to mark notification as read', error: error.message });
  }
};

module.exports = { getNotifications, getUnreadCount, markAllAsRead, markAsRead };
