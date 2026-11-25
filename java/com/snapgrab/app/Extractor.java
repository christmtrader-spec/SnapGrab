package com.snapgrab.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts downloadable media URLs from Instagram / Pinterest / generic pages.
 * Strategy: og: + twitter: meta tags, common JSON blobs (video_url, display_url,
 * video_versions, image_versions2, video_list, contentUrl), media element tags,
 * images from known CDNs, and plain links to downloadable files.
 */
public class Extractor {

    public interface Callback {
        void onDone(List<MediaItem> items, String error);
    }

    public static final String UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    private static final Pattern META_RE = Pattern.compile("<meta[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_RE = Pattern.compile("<img[^>]+src\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF_RE = Pattern.compile("<a[^>]+href\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO_SRC_RE = Pattern.compile("<video[^>]+src\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUDIO_SRC_RE = Pattern.compile("<audio[^>]+src\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNICODE_RE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    // --- async entry points -------------------------------------------------

    public static void extractAsync(final String rawUrl, final Callback cb) {
        POOL.execute(new Runnable() {
            public void run() {
                try {
                    cb.onDone(extract(rawUrl), null);
                } catch (Throwable t) {
                    cb.onDone(null, friendly(t));
                }
            }
        });
    }

    /** Parse HTML that the caller already fetched (e.g. WebView document HTML). */
    public static void parseAsync(final String html, final String baseUrl, final Callback cb) {
        POOL.execute(new Runnable() {
            public void run() {
                try {
                    cb.onDone(parseHtml(html, baseUrl, true), null);
                } catch (Throwable t) {
                    cb.onDone(null, friendly(t));
                }
            }
        });
    }

    public static String friendly(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.length() == 0) m = t.getClass().getSimpleName();
        return m.length() > 160 ? m.substring(0, 160) : m;
    }

    // --- url helpers --------------------------------------------------------

    public static String normalize(String u) {
        if (u == null) return null;
        u = u.trim();
        if (u.length() == 0) return null;
        if (u.startsWith("www.")) u = "https://" + u;
        if (!u.startsWith("http://") && !u.startsWith("https://")) return null;
        return u;
    }

    public static String absolutize(String base, String href) {
        if (href == null) return null;
        href = href.trim();
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("//")) return "https:" + href;
        if (base == null) return null;
        try {
            URL b = new URL(base);
            if (href.startsWith("/")) return b.getProtocol() + "://" + b.getAuthority() + href;
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Unescape JSON-ish and HTML escapes found inside page sources. */
    public static String unescape(String s) {
        if (s == null) return null;
        s = s.replace("\\/", "/");
        s = s.replace("\\u0026", "&");
        s = s.replace("&amp;", "&");
        Matcher m = UNICODE_RE.matcher(s);
        if (m.find()) {
            StringBuffer sb = new StringBuffer();
            m.reset();
            while (m.find()) {
                String rep = m.group(0);
                try {
                    rep = new String(Character.toChars(Integer.parseInt(m.group(1), 16)));
                } catch (Exception ignored) {
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            }
            m.appendTail(sb);
            s = sb.toString();
        }
        s = s.replace("\\\"", "\"");
        return s;
    }

    // --- extraction ---------------------------------------------------------

    public static List<MediaItem> extract(String rawUrl) throws Exception {
        String url = normalize(rawUrl);
        if (url == null) throw new IllegalArgumentException("Invalid URL");

        String direct = MediaItem.extType(url);
        if (direct != null) {
            List<MediaItem> one = new ArrayList<MediaItem>();
            one.add(new MediaItem(url, direct, url));
            return one;
        }

        String html = httpGet(url, url, 8 * 1024 * 1024);
        if (html == null || html.length() == 0) throw new IllegalStateException("Empty response from server");
        List<MediaItem> items = parseHtml(html, url, false);
        if (items.isEmpty() && url.toLowerCase().contains("pinterest.com")) {
            items = pinterestApi(url);
        }
        if (items.isEmpty()) {
            String low = html.toLowerCase();
            if (low.contains("login") && low.contains("instagram")) {
                throw new IllegalStateException("LOGIN_WALL");
            }
        }
        return items;
    }

    /**
     * Fallback for Pinterest: the page shell sometimes has no SSR data; the same
     * data is available through the internal PinResource endpoint.
     */
    private static List<MediaItem> pinterestApi(String url) throws Exception {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("pin/(\\d+)")
                .matcher(url);
        if (!m.find()) return new ArrayList<MediaItem>();
        String id = m.group(1);
        String data = "{\"options\":{\"id\":\"" + id
                + "\",\"field_set_key\":\"unauth_react_main_pin\",\"noCache\":false},\"context\":{}}";
        String api = "https://www.pinterest.com/resource/PinResource/get/?source_url="
                + java.net.URLEncoder.encode("/pin/" + id + "/", "UTF-8")
                + "&data=" + java.net.URLEncoder.encode(data, "UTF-8");
        String json = httpGet(api, url, 4 * 1024 * 1024);
        if (json == null || json.length() == 0) return new ArrayList<MediaItem>();
        return parseHtml(json, url, false);
    }

    public static List<MediaItem> parseHtml(String html, String baseUrl, boolean fromBrowser) {
        LinkedHashMap<String, MediaItem> out = new LinkedHashMap<String, MediaItem>();
        if (html == null || html.length() == 0) return new ArrayList<MediaItem>();

        // 1) og:* / twitter:* meta tags
        Matcher meta = META_RE.matcher(html);
        while (meta.find()) {
            String tag = meta.group();
            String prop = attr(tag, "property");
            if (prop == null) prop = attr(tag, "name");
            String content = attr(tag, "content");
            if (prop == null || content == null) continue;
            prop = prop.toLowerCase().trim();
            String type = null;
            if (prop.equals("og:video") || prop.equals("og:video:url")
                    || prop.equals("og:video:secure_url") || prop.equals("twitter:player:stream")) {
                type = "video";
            } else if (prop.equals("og:image") || prop.equals("og:image:secure_url")
                    || prop.equals("twitter:image") || prop.equals("twitter:image:src")) {
                type = "image";
            }
            if (type != null && content.startsWith("http")) {
                add(out, unescape(content), type, baseUrl);
            }
        }

        // 2) JSON blobs used by Instagram / Pinterest / schema.org
        jsonGrab(html, "\"video_url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", "video", baseUrl, out, false);
        jsonGrab(html, "\"display_url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", "image", baseUrl, out, false);
        jsonGrab(html, "\"video_versions\"\\s*:\\s*\\[\\s*\\{[^{]*?\"url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", "video", baseUrl, out, false);
        jsonGrab(html, "\"image_versions2\"\\s*:\\s*\\{[^{]*?\"candidates\"\\s*:\\s*\\[\\s*\\{[^{]*?\"url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", "image", baseUrl, out, false);
        jsonGrab(html, "\"video_list\"\\s*:\\s*\\{\\s*\"[^\"]+\"\\s*:\\s*\\{[^{]*?\"url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", "video", baseUrl, out, true);
        jsonGrab(html, "\"images\"\\s*:\\s*\\{\\s*\"orig\"\\s*:\\s*\\{\\s*\"url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", "image", baseUrl, out, true);
        jsonGrab(html, "\"image_large_url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", "image", baseUrl, out, false);
        jsonGrab(html, "\"image_medium_url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", "image", baseUrl, out, false);
        jsonGrab(html, "\"contentUrl\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", null, baseUrl, out, false);
        jsonGrab(html, "\"downloadUrl\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", null, baseUrl, out, false);

        // 3) media element tags
        tagGrab(html, VIDEO_SRC_RE, "video", baseUrl, out);
        tagGrab(html, AUDIO_SRC_RE, "audio", baseUrl, out);

        // 4) <img> sources on known media CDNs (works for SPA pages loaded in the browser)
        Matcher img = IMG_RE.matcher(html);
        while (img.find()) {
            String u = absolutize(baseUrl, unescape(img.group(1)));
            if (u == null) continue;
            String low = u.toLowerCase();
            if (low.contains("cdninstagram") || low.contains("fbcdn") || low.contains("pinimg.com")
                    || low.contains("scontent") || low.contains("akamaihd")) {
                add(out, u, "image", baseUrl);
            }
        }

        // 5) plain links to downloadable files (docs, music, archives...)
        Matcher a = HREF_RE.matcher(html);
        while (a.find()) {
            String u = absolutize(baseUrl, unescape(a.group(1)));
            if (u == null) continue;
            String t = MediaItem.extType(u);
            if (t != null && !"image".equals(t)) add(out, u, t, baseUrl);
        }

        // 6) direct URL fallback
        if (out.isEmpty()) {
            String t = MediaItem.extType(baseUrl);
            if (t != null) add(out, baseUrl, t, baseUrl);
        }

        ArrayList<MediaItem> list = new ArrayList<MediaItem>(out.values());

        // 7) Pinterest: upgrade thumbnail URLs to full-size originals
        for (MediaItem m : list) {
            if ("image".equals(m.type) && m.url != null && m.url.contains("pinimg.com")
                    && !m.url.contains("/originals/")) {
                String up = m.url.replaceFirst("/\\d+x\\d*/", "/originals/");
                if (up.equals(m.url)) up = m.url.replaceFirst("/\\d+x/", "/originals/");
                if (!up.equals(m.url)) {
                    m.fallbackUrl = m.url;
                    m.url = up;
                }
            }
        }
        return list;
    }

    private static void add(LinkedHashMap<String, MediaItem> out, String url, String type, String base) {
        if (url == null) return;
        url = url.trim();
        if (url.length() < 12) return;
        if (!url.startsWith("http")) return;
        String low = url.toLowerCase();
        if (low.contains(".m3u8") || low.contains(".mpd")) return; // streams not supported
        if (low.contains("/emoji/") || low.contains("favicon")) return;
        if (out.containsKey(url)) return;
        out.put(url, new MediaItem(url, type, base));
    }

    private static void jsonGrab(String html, String re, String type, String base,
                                 LinkedHashMap<String, MediaItem> out, boolean all) {
        try {
            Matcher m = Pattern.compile(re, Pattern.CASE_INSENSITIVE).matcher(html);
            int count = 0;
            while (m.find() && (all || count == 0)) {
                count++;
                String u = unescape(m.group(1));
                if (type != null) {
                    add(out, u, type, base);
                } else {
                    String t = MediaItem.extType(u);
                    if (t != null) add(out, u, t, base);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void tagGrab(String html, Pattern re, String type, String base,
                                LinkedHashMap<String, MediaItem> out) {
        try {
            Matcher m = re.matcher(html);
            while (m.find()) {
                String u = absolutize(base, unescape(m.group(1)));
                if (u == null) continue;
                String t = MediaItem.extType(u);
                add(out, u, t != null ? t : type, base);
            }
        } catch (Exception ignored) {
        }
    }

    private static String attr(String tag, String name) {
        try {
            Pattern p = Pattern.compile(name + "\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(tag);
            if (!m.find()) return null;
            return m.group(2) != null ? m.group(2) : m.group(3);
        } catch (Exception e) {
            return null;
        }
    }

    // --- network ------------------------------------------------------------

    public static String httpGet(String urlStr, String referer, int maxBytes) throws Exception {
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection c = null;
            try {
                URL u = new URL(urlStr);
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                if (referer != null) c.setRequestProperty("Referer", referer);
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    String next = (loc != null && loc.length() > 0) ? absolutize(urlStr, unescape(loc)) : null;
                    if (next != null) {
                        urlStr = next;
                        continue;
                    }
                    throw new IllegalStateException("HTTP " + code);
                }
                if (code != 200) throw new IllegalStateException("HTTP " + code);
                InputStream in = c.getInputStream();
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n, total = 0;
                while ((n = in.read(b)) != -1) {
                    total += n;
                    if (total > maxBytes) break;
                    buf.write(b, 0, n);
                }
                try {
                    in.close();
                } catch (Exception ignored) {
                }
                return new String(buf.toByteArray(), "UTF-8");
            } finally {
                if (c != null) c.disconnect();
            }
        }
        throw new IllegalStateException("Too many redirects");
    }
}
