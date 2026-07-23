const Notification = require('../models/notification');

// GET the logged-in user's notifications
const getNotifications = async (req, res) => {
  try {
    const notifications = await Notification.findAll({
      where: { user_id: req.user.users_id },
      order: [['created_at', 'DESC']],
    });
    res.status(200).json(notifications);
  } catch (error) {
    res.status(500).json({ message: 'Failed to fetch notifications', error: error.message });
  }
};

// PUT mark a notification as read
const markAsRead = async (req, res) => {
  try {
    const { id } = req.params;
    const notification = await Notification.findByPk(id);

    if (!notification) {
      return res.status(404).json({ message: 'Notification not found' });
    }

    if (notification.user_id !== req.user.users_id) {
      return res.status(403).json({ message: 'You do not have permission to modify this notification' });
    }

    notification.is_read = true;
    await notification.save();

    res.status(200).json(notification);
  } catch (error) {
    res.status(500).json({ message: 'Failed to mark notification as read', error: error.message });
  }
};

module.exports = { getNotifications, markAsRead };
