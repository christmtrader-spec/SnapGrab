package com.snapgrab.app;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Downloads one MediaItem through HttpURLConnection with browser-like headers
 * and saves it via Storage. Tries item.fallbackUrl when the main URL fails.
 */
public class Downloader {

    public interface Progress {
        void onProgress(long done, long total);
    }

    public static class Result {
        public Uri uri;
        public long size;
        public String mime;
        public String name;
        public String path;
    }

    public static Result download(Context ctx, MediaItem item, Progress cb) throws Exception {
        Exception last = null;
        String[] urls = item.fallbackUrl != null
                ? new String[]{item.url, item.fallbackUrl}
                : new String[]{item.url};
        for (int i = 0; i < urls.length; i++) {
            try {
                return downloadOne(ctx, item, urls[i], cb);
            } catch (Exception e) {
                last = e;
            }
        }
        throw (last != null) ? last : new IllegalStateException("Download failed");
    }

    private static Result downloadOne(Context ctx, MediaItem item, String urlStr, Progress cb)
            throws Exception {
        for (int hop = 0; hop < 4; hop++) {
            HttpURLConnection c = null;
            try {
                URL u = new URL(urlStr);
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", Extractor.UA);
                c.setRequestProperty("Accept", "*/*");
                if (item.pageUrl != null) c.setRequestProperty("Referer", item.pageUrl);

                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    String next = (loc != null && loc.length() > 0)
                            ? Extractor.absolutize(urlStr, Extractor.unescape(loc)) : null;
                    if (next != null) {
                        urlStr = next;
                        continue;
                    }
                    throw new IllegalStateException("HTTP " + code);
                }
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);

                String mime = c.getContentType();
                if (mime != null) {
                    int i = mime.indexOf(';');
                    if (i > 0) mime = mime.substring(0, i).trim();
                }
                if (mime != null && mime.toLowerCase().startsWith("text/html")
                        && !"doc".equals(item.type)) {
                    throw new IllegalStateException("Server returned a page, not media");
                }
                long total = c.getContentLength();
                if (total == 0) throw new IllegalStateException("Empty response");

                String type = MediaItem.typeForMime(mime);
                if (type == null) type = item.type == null ? "doc" : item.type;
                String ext = extFor(urlStr, mime);
                String name = "SnapGrab_"
                        + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date())
                        + ext;

                InputStream in = new BufferedInputStream(c.getInputStream(), 8192);
                Storage.Saved s;
                try {
                    s = Storage.save(ctx, MediaItem.folderFor(type), name, mime, in,
                            new Storage.Progress() {
                                public void onProgress(long done, long total) {
                                    if (cb != null) cb.onProgress(done, total);
                                }
                            });
                } finally {
                    try {
                        in.close();
                    } catch (Exception ignored) {
                    }
                }

                Result r = new Result();
                r.uri = s.uri;
                r.size = s.size;
                r.mime = mime;
                r.name = name;
                r.path = s.path;
                return r;
            } finally {
                if (c != null) c.disconnect();
            }
        }
        throw new IllegalStateException("Too many redirects");
    }

    static String extFor(String url, String mime) {
        if (url != null) {
            String u = url;
            int q = u.indexOf('?');
            if (q >= 0) u = u.substring(0, q);
            int slash = u.lastIndexOf('/');
            int dot = u.lastIndexOf('.');
            if (dot > slash && dot >= 0) {
                String e = u.substring(dot).toLowerCase();
                if (e.length() >= 2 && e.length() <= 6) return e;
            }
        }
        if (mime != null) {
            try {
                String e = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                if (e != null && e.length() > 0) return "." + e;
            } catch (Exception ignored) {
            }
            int i = mime.indexOf('/');
            if (i >= 0) {
                String sub = mime.substring(i + 1).replaceAll("[^a-z0-9]", "");
                if (sub.length() > 0 && sub.length() <= 5) return "." + sub;
            }
        }
        return ".bin";
    }
}
