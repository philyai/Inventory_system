require('dotenv').config();
const express = require('express');
const cors = require('cors');
const path = require('path');
const sequelize = require('./models/index');
const dashboardRoutes = require('./routes/dashboardRoutes');
const ItemRoutes = require('./routes/itemRoutes');
const authRoutes = require('./routes/authRoutes');
const itemMovementRoutes = require('./routes/itemMovementRoutes');
const categoryRoutes = require('./routes/categoryRoutes');
const reportRoutes = require('./routes/reportRoutes');
const disposalRoutes = require('./routes/disposalRoutes');
const profileRoutes = require('./routes/profileRoutes');
const locationRoutes = require('./routes/locationRoutes');
const notificationRoutes = require('./routes/notificationRoutes');
const activityLogRoutes = require('./routes/activityLogRoutes');
const systemRoutes = require('./routes/systemRoutes');


const app = express();
app.use(cors());
app.use(express.json());
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
app.set('trust proxy', 1);
app.get('/health', async (req, res) => {
  try {
    await sequelize.authenticate();
    res.status(200).json({ status: 'ok', database: 'connected' });
  } catch (error) {
    res.status(503).json({ status: 'degraded', database: 'unavailable' });
  }
});
app.use('/items', ItemRoutes);
app.use('/dashboard', dashboardRoutes);
app.use('/movements', itemMovementRoutes);
app.use('/categories', categoryRoutes);
app.use('/reports', reportRoutes);
app.use('/disposals', disposalRoutes);
app.use('/profile', profileRoutes);
app.use('/locations', locationRoutes);
app.use('/notifications', notificationRoutes);
app.use('/activity-logs', activityLogRoutes);
app.use('/system', systemRoutes);

require('./models/foreignkey');

app.use('/auth', authRoutes);

const port = Number(process.env.PORT || 3001);
const host = process.env.HOST || '0.0.0.0';

if (!Number.isInteger(port) || port <= 0 || port > 65535) {
  console.error('PORT must be a positive integer between 1 and 65535');
  process.exit(1);
}

app.use((req, res) => {
  res.status(404).json({ message: 'Route not found' });
});

app.use((error, req, res, next) => {
  if (error.name === 'MulterError') {
    const message = error.code === 'LIMIT_FILE_SIZE'
      ? 'Image must not exceed 5 MB'
      : error.message;
    return res.status(400).json({ message });
  }

  if (error.message === 'Only JPG, PNG, and WebP images are allowed') {
    return res.status(400).json({ message: error.message });
  }

  console.error('Unhandled request error:', error);
  res.status(500).json({ message: 'Internal server error' });
});

const connectDatabase = async () => {
  const hosts = [...new Set([
    process.env.DB_HOST,
    process.env.DB_FALLBACK_HOST,
  ].filter(Boolean))];

  let lastError;
  for (const host of hosts) {
    sequelize.options.host = host;
    sequelize.config.host = host;
    sequelize.connectionManager.config.host = host;

    try {
      await sequelize.authenticate();
      console.log(`Database connected successfully using ${host}!`);
      return;
    } catch (error) {
      lastError = error;
      console.log(`Database connection failed using ${host}: ${error.message}`);
    }
  }

  throw lastError || new Error('No database host is configured');
};

const startServer = async () => {
  try {
    await connectDatabase();
    app.listen(port, host, () => {
      console.log(`Server running on http://${host}:${port}`);
    });
  } catch (error) {
    console.log("Database connection failed! Server not started.");
    console.log(error.message);
    process.exit(1);
  }
};

startServer();
