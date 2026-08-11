const path = require('path');
const multer = require('multer');
const fs = require('fs/promises');

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

function matchesDeclaredImageType(buffer, mimeType) {
  if (mimeType === 'image/jpeg') {
    return buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff;
  }
  if (mimeType === 'image/png') {
    return buffer.length >= 8 && buffer.subarray(0, 8).equals(
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
    );
  }
  if (mimeType === 'image/webp') {
    return buffer.length >= 12 &&
      buffer.subarray(0, 4).toString('ascii') === 'RIFF' &&
      buffer.subarray(8, 12).toString('ascii') === 'WEBP';
  }
  return false;
}

const verifyUploadedImage = async (req, res, next) => {
  if (!req.file) return next();

  try {
    const handle = await fs.open(req.file.path, 'r');
    const buffer = Buffer.alloc(12);
    const { bytesRead } = await handle.read(buffer, 0, buffer.length, 0);
    await handle.close();

    if (!matchesDeclaredImageType(buffer.subarray(0, bytesRead), req.file.mimetype)) {
      await fs.unlink(req.file.path).catch(() => {});
      req.file = undefined;
      return res.status(400).json({ message: 'Uploaded file content does not match its image type' });
    }
    next();
  } catch (error) {
    await fs.unlink(req.file.path).catch(() => {});
    req.file = undefined;
    next(error);
  }
};

module.exports = { uploadItemImage, verifyUploadedImage };
