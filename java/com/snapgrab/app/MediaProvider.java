package com.snapgrab.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Serves files saved by the legacy (< API 29) path so other apps can open them
 * through a content:// URI (file:// URIs are blocked when leaving the app).
 * URI form: content://com.snapgrab.app.files/<Folder>/<name>
 */
public class MediaProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        List<String> segs = uri.getPathSegments();
        if (segs == null || segs.isEmpty()) throw new FileNotFoundException("Empty path");
        StringBuilder sb = new StringBuilder();
        for (String seg : segs) {
            if (seg.contains("..") || seg.contains("/") || seg.contains("\\")) {
                throw new FileNotFoundException("Bad path segment");
            }
            if (sb.length() > 0) sb.append('/');
            sb.append(seg);
        }
        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File f = new File(new File(base, "SnapGrab"), sb.toString());
        String canonical;
        try {
            canonical = f.getCanonicalPath();
        } catch (Exception e) {
            throw new FileNotFoundException("Bad path");
        }
        String root;
        try {
            root = new File(base, "SnapGrab").getCanonicalPath();
        } catch (Exception e) {
            throw new FileNotFoundException("Bad root");
        }
        if (!canonical.startsWith(root)) throw new FileNotFoundException("Outside sandbox");
        return new File(canonical);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(uri);
        if (!f.exists() || !f.isFile()) throw new FileNotFoundException(f.getName());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                String ext = name.substring(dot + 1).toLowerCase();
                try {
                    String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                    if (m != null) return m;
                } catch (Exception ignored) {
                }
            }
        }
        return "application/octet-stream";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
