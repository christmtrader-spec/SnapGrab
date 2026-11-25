package com.snapgrab.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Foreground service running the download queue (2 workers), with progress and
 * completion notifications. Broadcasts ACTION_REFRESH when a batch finishes.
 */
public class DownloadService extends Service {

    public static final String ACTION_ENQUEUE = "com.snapgrab.app.ENQUEUE";
    public static final String ACTION_REFRESH = "com.snapgrab.app.REFRESH";
    public static final String EXTRA_ITEMS = "items";

    private static final String CH_PROGRESS = "snapgrab_downloads";
    private static final String CH_DONE = "snapgrab_done";
    private static final int FG_ID = 11;

    private ExecutorService pool;
    private NotificationManager nm;
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicInteger notifIds = new AtomicInteger(100);

    @Override
    public void onCreate() {
        super.onCreate();
        pool = Executors.newFixedThreadPool(2);
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel p = new NotificationChannel(CH_PROGRESS,
                    getString(R.string.ch_downloads), NotificationManager.IMPORTANCE_LOW);
            p.setShowBadge(false);
            nm.createNotificationChannel(p);
            NotificationChannel d = new NotificationChannel(CH_DONE,
                    getString(R.string.ch_done), NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(d);
        }
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        final List<MediaItem> items = parseItems(intent);
        if (items == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        startForeground(FG_ID, buildProgressNotification(
                getString(R.string.notif_summary, items.size())));

        for (final MediaItem item : items) {
            pending.incrementAndGet();
            try {
                pool.execute(new Runnable() {
                    public void run() {
                        runOne(item, startId);
                    }
                });
            } catch (RejectedExecutionException e) {
                pending.decrementAndGet();
            }
        }
        return START_NOT_STICKY;
    }

    private List<MediaItem> parseItems(Intent intent) {
        if (intent == null || !ACTION_ENQUEUE.equals(intent.getAction())) return null;
        String json = intent.getStringExtra(EXTRA_ITEMS);
        if (json == null || json.length() == 0) return null;
        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) return null;
            List<MediaItem> items = new ArrayList<MediaItem>();
            for (int i = 0; i < arr.length(); i++) {
                items.add(MediaItem.fromJson(arr.getJSONObject(i)));
            }
            return items;
        } catch (Exception e) {
            return null;
        }
    }

    private void runOne(final MediaItem item, final int startId) {
        try {
            Downloader.Result r = Downloader.download(this, item, new Downloader.Progress() {
                public void onProgress(long done, long total) {
                    showProgress(item);
                }
            });
            HistoryDb.get(DownloadService.this).add(r.name, item.url,
                    r.uri == null ? null : r.uri.toString(), r.mime, r.size, "DONE");
            notifyDone(r.name);
        } catch (Exception e) {
            HistoryDb.get(DownloadService.this).add(item.url, item.url, null, null, 0, "FAILED");
            notifyFailed(Extractor.friendly(e));
        } finally {
            if (pending.decrementAndGet() == 0) {
                sendBroadcast(new Intent(ACTION_REFRESH).setPackage(getPackageName()));
                stopForeground(true);
                stopSelf(startId);
            }
        }
    }

    private void showProgress(MediaItem item) {
        nm.notify(FG_ID, buildProgressNotification(
                getString(R.string.notif_downloading, shortName(item.url))));
    }

    private Notification buildProgressNotification(String text) {
        Notification.Builder b = builder(CH_PROGRESS);
        b.setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppIntent());
        b.setProgress(0, 0, true);
        return b.build();
    }

    private void notifyDone(String name) {
        Notification.Builder b = builder(CH_DONE);
        b.setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(getString(R.string.dl_complete))
                .setContentText(getString(R.string.notif_saved, name))
                .setAutoCancel(true)
                .setContentIntent(openAppIntent());
        nm.notify(notifIds.incrementAndGet(), b.build());
    }

    private void notifyFailed(String why) {
        Notification.Builder b = builder(CH_DONE);
        b.setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(getString(R.string.download_failed, why))
                .setAutoCancel(true)
                .setContentIntent(openAppIntent());
        nm.notify(notifIds.incrementAndGet(), b.build());
    }

    private PendingIntent openAppIntent() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification.Builder builder(String ch) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(this, ch);
        return new Notification.Builder(this);
    }

    private static String shortName(String url) {
        if (url == null) return "";
        try {
            String u = url;
            int q = u.indexOf('?');
            if (q >= 0) u = u.substring(0, q);
            int slash = u.lastIndexOf('/');
            if (slash >= 0 && slash < u.length() - 1) {
                String s = u.substring(slash + 1);
                return s.length() > 40 ? s.substring(0, 40) : s;
            }
        } catch (Exception ignored) {
        }
        return url;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pool != null) pool.shutdownNow();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void enqueue(Context ctx, List<MediaItem> items) {
        if (items == null || items.isEmpty()) return;
        JSONArray arr = new JSONArray();
        for (MediaItem m : items) arr.put(m.toJson());
        Intent i = new Intent(ctx, DownloadService.class);
        i.setAction(ACTION_ENQUEUE);
        i.putExtra(EXTRA_ITEMS, arr.toString());
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception ignored) {
        }
    }
}
