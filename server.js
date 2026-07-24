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


const app = express();
app.use(cors());
app.use(express.json());
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
app.set('trust proxy', 1);
app.use('/items', ItemRoutes);
app.use('/dashboard', dashboardRoutes);
app.use('/movements', itemMovementRoutes);
app.use('/categories', categoryRoutes);
app.use('/reports', reportRoutes);
app.use('/disposals', disposalRoutes);
app.use('/profile', profileRoutes);
app.use('/locations', locationRoutes);
app.use('/notifications', notificationRoutes);

require('./models/foreignkey');

app.use('/auth', authRoutes);

const port = process.env.PORT || 3001;

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
    app.listen(port, () => {
      console.log(`Server running on http://localhost:${port}`);
    });
  } catch (error) {
    console.log("Database connection failed! Server not started.");
    console.log(error.message);
    process.exit(1);
  }
};

startServer();
