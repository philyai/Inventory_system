const path = require('path');
const multer = require('multer');

const uploadDirectory = path.join(__dirname, '..', 'uploads', 'items');
const extensionsByMimeType = {
  'image/jpeg': '.jpg',
  'image/png': '.png',
  'image/webp': '.webp',
};

const storage = multer.diskStorage({
  destination: (req, file, callback) => callback(null, uploadDirectory),
  filename: (req, file, callback) => {
    const extension = extensionsByMimeType[file.mimetype];
    const uniqueName = `${Date.now()}-${Math.round(Math.random() * 1e9)}${extension}`;
    callback(null, uniqueName);
  },
});

const uploadItemImage = multer({
  storage,
  limits: { fileSize: 5 * 1024 * 1024 },
  fileFilter: (req, file, callback) => {
    if (!extensionsByMimeType[file.mimetype]) {
      return callback(new Error('Only JPG, PNG, and WebP images are allowed'));
    }
    callback(null, true);
  },
});

module.exports = { uploadItemImage };
