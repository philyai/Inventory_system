const LoginSession = require('../models/loginSession');

function isMissingLoginSessionsTable(error) {
  let currentError = error;
  while (currentError) {
    if (
      typeof currentError.message === 'string' &&
      currentError.message.toLowerCase().includes('login_sessions') &&
      (
        currentError.message.toLowerCase().includes('invalid object') ||
        currentError.message.toLowerCase().includes('does not exist')
      )
    ) {
      return true;
    }
    currentError = currentError.parent || currentError.original;
  }
  return false;
}

const getActivityLogs = async (req, res) => {
  try {
    const requestedLimit = Number(req.query.limit || 50);
    if (!Number.isInteger(requestedLimit) || requestedLimit < 1 || requestedLimit > 100) {
      return res.status(400).json({ message: 'limit must be an integer from 1 to 100' });
    }

    const sessions = await LoginSession.findAll({
      where: { users_id: req.user.users_id },
      attributes: [
        'login_session_id',
        'login_time',
        'logout_time',
        'device_info',
        'ip_address',
        'status',
      ],
      order: [['login_time', 'DESC']],
      limit: requestedLimit,
    });

    res.status(200).json(sessions);
  } catch (error) {
    if (isMissingLoginSessionsTable(error)) {
      return res.status(503).json({
        message: 'Activity logs require the login_sessions database table.',
      });
    }
    res.status(500).json({ message: 'Failed to fetch activity logs', error: error.message });
  }
};

module.exports = { getActivityLogs };
