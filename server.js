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

const startServer = async () => {
  try {
    await sequelize.authenticate();
    console.log("Database connected successfully!");
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
