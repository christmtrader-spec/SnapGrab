package com.snapgrab.app;

import org.json.JSONObject;

/**
 * One downloadable media item found on a page or given as a direct URL.
 */
public class MediaItem {
    public String url;
    public String fallbackUrl; // tried if url fails (e.g. Pinterest originals 404 -> smaller size)
    public String type;        // image | video | audio | doc
    public String pageUrl;     // page the item was found on (used as Referer)

    public MediaItem() {
    }

    public MediaItem(String url, String type, String pageUrl) {
        this.url = url;
        this.type = type;
        this.pageUrl = pageUrl;
    }

    public static String folderFor(String type) {
        if ("image".equals(type)) return "Images";
        if ("video".equals(type)) return "Videos";
        if ("audio".equals(type)) return "Music";
        return "Docs";
    }

    public static String typeForMime(String mime) {
        if (mime == null) return null;
        mime = mime.toLowerCase();
        if (mime.startsWith("image/")) return "image";
        if (mime.startsWith("video/")) return "video";
        if (mime.startsWith("audio/")) return "audio";
        return null;
    }

    /** Type guessed from a known file extension, or null. */
    public static String extType(String url) {
        if (url == null) return null;
        String u = url.toLowerCase();
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        int h = u.indexOf('#');
        if (h >= 0) u = u.substring(0, h);
        int slash = u.lastIndexOf('/');
        int dot = u.lastIndexOf('.');
        if (dot < 0 || dot <= slash) return null;
        String e = u.substring(dot + 1);
        if (e.length() == 0 || e.length() > 5) return null;
        if (e.equals("jpg") || e.equals("jpeg") || e.equals("png") || e.equals("gif")
                || e.equals("webp") || e.equals("bmp") || e.equals("heic")) return "image";
        if (e.equals("mp4") || e.equals("mkv") || e.equals("webm") || e.equals("mov")
                || e.equals("m4v") || e.equals("3gp") || e.equals("avi") || e.equals("ts")) return "video";
        if (e.equals("mp3") || e.equals("m4a") || e.equals("aac") || e.equals("wav")
                || e.equals("ogg") || e.equals("opus") || e.equals("flac")) return "audio";
        if (e.equals("pdf") || e.equals("doc") || e.equals("docx") || e.equals("xls")
                || e.equals("xlsx") || e.equals("ppt") || e.equals("pptx") || e.equals("txt")
                || e.equals("csv") || e.equals("zip") || e.equals("rar") || e.equals("7z")
                || e.equals("epub") || e.equals("apk")) return "doc";
        return null;
    }

    /** Best-effort type for any URL (falls back to "doc"). */
    public static String typeForUrl(String url) {
        String t = extType(url);
        if (t != null) return t;
        String u = url == null ? "" : url.toLowerCase();
        if (u.contains("video") || u.contains(".m3u8") || u.contains("/vid")) return "video";
        return "doc";
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("url", url == null ? "" : url);
            if (fallbackUrl != null) o.put("fallback", fallbackUrl);
            o.put("type", type == null ? "doc" : type);
            if (pageUrl != null) o.put("page", pageUrl);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static MediaItem fromJson(JSONObject o) {
        MediaItem m = new MediaItem();
        m.url = o.optString("url", "");
        m.fallbackUrl = o.optString("fallback", null);
        String t = o.optString("type", "doc");
        m.type = t.length() == 0 ? "doc" : t;
        m.pageUrl = o.optString("page", null);
        return m;
    }
}
