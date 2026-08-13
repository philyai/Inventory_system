package com.inventorysystem.offline;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class PendingImageStore {
    private static final long MAX_BYTES = 5L * 1024L * 1024L;
    private PendingImageStore() {}

    public static StoredImage copy(Context context, Uri source, String requestId) throws IOException {
        if (source == null) return null;
        String mime = context.getContentResolver().getType(source);
        if (mime == null) mime = "image/jpeg";
        if (!mime.equals("image/jpeg") && !mime.equals("image/png") && !mime.equals("image/webp")) {
            throw new IOException("Only JPG, PNG, or WebP images are allowed");
        }
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (extension == null) extension = "jpg";
        File directory = new File(context.getFilesDir(), "pending_items");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Unable to create pending image directory");
        File destination = new File(directory, requestId + "." + extension);
        long total = 0;
        try (InputStream input = context.getContentResolver().openInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) throw new IOException("Unable to read selected image");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) throw new IOException("Image must be 5 MB or smaller");
                output.write(buffer, 0, read);
            }
        } catch (IOException exception) {
            destination.delete();
            throw exception;
        }
        return new StoredImage(destination.getAbsolutePath(), mime);
    }

    public static boolean delete(String path) { return path == null || new File(path).delete() || !new File(path).exists(); }

    public static final class StoredImage {
        public final String path;
        public final String mimeType;
        StoredImage(String path, String mimeType) { this.path = path; this.mimeType = mimeType; }
    }
}