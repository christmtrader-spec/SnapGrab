package com.snapgrab.app;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Saves a stream into Download/SnapGrab/<Folder>.
 * API 29+: MediaStore.Downloads (no storage permission needed).
 * API 23-28: direct file write into the public Downloads dir (WRITE_EXTERNAL_STORAGE
 * is requested by the activity before enqueueing).
 */
public class Storage {

    public interface Progress {
        void onProgress(long done, long total);
    }

    public static class Saved {
        public Uri uri;
        public long size;
        public String path;
    }

    public static Saved save(Context ctx, String folder, String name, String mime,
                             InputStream in, Progress cb) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            return saveMediaStore(ctx, folder, name, mime, in, cb);
        }
        return saveLegacy(ctx, folder, name, in, cb);
    }

    private static Saved saveMediaStore(Context ctx, String folder, String name, String mime,
                                        InputStream in, Progress cb) throws Exception {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        if (mime != null && mime.length() > 0) cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/SnapGrab/" + folder);
        cv.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) throw new IllegalStateException("MediaStore insert failed");

        Saved s = new Saved();
        OutputStream os = null;
        try {
            os = ctx.getContentResolver().openOutputStream(uri);
            if (os == null) throw new IllegalStateException("Cannot open output stream");
            s.size = pump(in, os, cb);
        } catch (Exception e) {
            try {
                ctx.getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {
            }
            throw e;
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception ignored) {
                }
            }
        }

        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        ctx.getContentResolver().update(uri, done, null, null);

        s.uri = uri;
        s.path = "Download/SnapGrab/" + folder + "/" + name;
        return s;
    }

    private static Saved saveLegacy(Context ctx, String folder, String name,
                                    InputStream in, Progress cb) throws Exception {
        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(base, "SnapGrab/" + folder);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create folder " + dir);
        }
        File f = new File(dir, name);
        FileOutputStream os = new FileOutputStream(f);
        Saved s = new Saved();
        try {
            s.size = pump(in, os, cb);
        } finally {
            try {
                os.close();
            } catch (Exception ignored) {
            }
        }
        MediaScannerConnection.scanFile(ctx, new String[]{f.getAbsolutePath()}, null, null);
        s.uri = Uri.parse("content://com.snapgrab.app.files/" + folder + "/" + name);
        s.path = "Download/SnapGrab/" + folder + "/" + name;
        return s;
    }

    static long pump(InputStream in, OutputStream os, Progress cb) throws java.io.IOException {
        byte[] b = new byte[8192];
        long total = 0;
        int n;
        long last = System.currentTimeMillis();
        while ((n = in.read(b)) != -1) {
            os.write(b, 0, n);
            total += n;
            long now = System.currentTimeMillis();
            if (cb != null && now - last > 300) {
                cb.onProgress(total, -1);
                last = now;
            }
        }
        os.flush();
        if (cb != null) cb.onProgress(total, -1);
        return total;
    }
}
