package com.snapgrab.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Download history stored in a small SQLite database.
 */
public class HistoryDb extends SQLiteOpenHelper {

    public static class Row {
        public long id;
        public String name;
        public String url;
        public String uri;
        public String mime;
        public long size;
        public String status;
        public long ts;
    }

    private static final String DB_NAME = "snapgrab.db";
    private static final String[] PROJECTION =
            {"id", "name", "url", "uri", "mime", "size", "status", "ts"};
    private static HistoryDb sInstance;

    public static synchronized HistoryDb get(Context c) {
        if (sInstance == null) sInstance = new HistoryDb(c.getApplicationContext());
        return sInstance;
    }

    private HistoryDb(Context c) {
        super(c, DB_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT, url TEXT, uri TEXT, mime TEXT, size INTEGER, status TEXT, ts INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public void add(String name, String url, String uri, String mime, long size, String status) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("url", url);
        cv.put("uri", uri);
        cv.put("mime", mime);
        cv.put("size", size);
        cv.put("status", status);
        cv.put("ts", System.currentTimeMillis());
        getWritableDatabase().insert("history", null, cv);
    }

    public List<Row> list(int limit) {
        List<Row> out = new ArrayList<Row>();
        Cursor c = getReadableDatabase().query("history", PROJECTION, null, null, null, null,
                "id DESC", String.valueOf(limit));
        try {
            while (c.moveToNext()) {
                Row r = new Row();
                r.id = c.getLong(0);
                r.name = c.getString(1);
                r.url = c.getString(2);
                r.uri = c.getString(3);
                r.mime = c.getString(4);
                r.size = c.getLong(5);
                r.status = c.getString(6);
                r.ts = c.getLong(7);
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public void delete(long id) {
        getWritableDatabase().delete("history", "id=?", new String[]{String.valueOf(id)});
    }

    public void clear() {
        getWritableDatabase().delete("history", null, null);
    }
}
