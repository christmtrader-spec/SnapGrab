package com.snapgrab.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements MediaAdapter.OnSave {

    private static final int REQ_STORAGE = 41;
    private static final int REQ_NOTIF = 42;

    private FrameLayout container;

    private ListView panelDownload;
    private EditText etUrl;
    private View headProgress;
    private TextView headEmpty;
    private Button btnSaveAll;
    private MediaAdapter mediaAdapter;
    private final List<MediaItem> lastResults = new ArrayList<MediaItem>();

    private LinearLayout panelBrowser;
    private EditText etBrowser;
    private WebView web;

    private ListView panelHistory;
    private TextView histEmpty;
    private HistoryAdapter historyAdapter;
    private final List<HistoryDb.Row> historyRows = new ArrayList<HistoryDb.Row>();

    private int currentTab = 0;
    private Runnable pendingAction;
    private BroadcastReceiver refreshReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        container = (FrameLayout) findViewById(R.id.container);

        setupDownloadPanel();
        setupBrowserPanel();
        setupHistoryPanel();

        findViewById(R.id.tab_download).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectTab(0);
            }
        });
        findViewById(R.id.tab_browser).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectTab(1);
            }
        });
        findViewById(R.id.tab_history).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectTab(2);
            }
        });

        selectTab(0);
        requestNotifPermissionIfNeeded();
    }

    // ------------------------------------------------------------------ tabs

    private void selectTab(int t) {
        currentTab = t;
        panelDownload.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        panelBrowser.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        panelHistory.setVisibility(t == 2 ? View.VISIBLE : View.GONE);
        setTabSelected(R.id.tab_download, R.id.iv_tab_download, R.id.tv_tab_download, t == 0);
        setTabSelected(R.id.tab_browser, R.id.iv_tab_browser, R.id.tv_tab_browser, t == 1);
        setTabSelected(R.id.tab_history, R.id.iv_tab_history, R.id.tv_tab_history, t == 2);
        if (t == 2) refreshHistory();
        hideKeyboard();
    }

    private void setTabSelected(int tabId, int ivId, int tvId, boolean sel) {
        findViewById(tabId).setSelected(sel);
        ImageView iv = (ImageView) findViewById(ivId);
        iv.setImageTintList(ColorStateList.valueOf(sel ? 0xFFFF3355 : 0xFF9A9AA3));
        TextView tv = (TextView) findViewById(tvId);
        tv.setTextColor(sel ? 0xFFFF3355 : 0xFF9A9AA3);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View f = getCurrentFocus();
            if (imm != null && f != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------- download panel

    private void setupDownloadPanel() {
        panelDownload = (ListView) getLayoutInflater().inflate(
                R.layout.panel_download, container, false);
        container.addView(panelDownload);

        View head = getLayoutInflater().inflate(R.layout.head_download, panelDownload, false);
        panelDownload.addHeaderView(head, null, false);
        headProgress = getLayoutInflater().inflate(R.layout.head_progress, panelDownload, false);
        panelDownload.addHeaderView(headProgress, null, false);
        btnSaveAll = (Button) getLayoutInflater().inflate(R.layout.head_saveall, panelDownload, false);
        panelDownload.addHeaderView(btnSaveAll, null, false);
        View he = getLayoutInflater().inflate(R.layout.head_empty, panelDownload, false);
        panelDownload.addHeaderView(he, null, false);
        headEmpty = (TextView) he;

        etUrl = (EditText) head.findViewById(R.id.et_url);
        mediaAdapter = new MediaAdapter(this, lastResults, this);
        panelDownload.setAdapter(mediaAdapter);

        head.findViewById(R.id.btn_paste).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pasteFromClipboard();
            }
        });
        head.findViewById(R.id.btn_extract).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startExtract(etUrl.getText().toString());
            }
        });
        etUrl.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    startExtract(etUrl.getText().toString());
                    return true;
                }
                return false;
            }
        });
        btnSaveAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                enqueueList(lastResults);
            }
        });
    }

    private void pasteFromClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return;
            ClipData cd = cm.getPrimaryClip();
            if (cd == null || cd.getItemCount() == 0) return;
            CharSequence t = cd.getItemAt(0).getText();
            if (t != null && t.length() > 0) etUrl.setText(t.toString().trim());
        } catch (Exception ignored) {
        }
    }

    private void startExtract(final String raw) {
        final String url = Extractor.normalize(raw);
        if (url == null) {
            toast(getString(R.string.not_a_url));
            return;
        }
        ensureStoragePermission(new Runnable() {
            public void run() {
                doExtract(url);
            }
        });
    }

    private void doExtract(String url) {
        lastResults.clear();
        mediaAdapter.notifyDataSetChanged();
        headEmpty.setVisibility(View.GONE);
        btnSaveAll.setVisibility(View.GONE);
        headProgress.setVisibility(View.VISIBLE);
        panelDownload.smoothScrollToPosition(0);

        Extractor.extractAsync(url, new Extractor.Callback() {
            public void onDone(final List<MediaItem> items, final String error) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        headProgress.setVisibility(View.GONE);
                        if (error != null) {
                            if (error.contains("LOGIN_WALL")) {
                                headEmpty.setText(R.string.login_wall);
                            } else {
                                headEmpty.setText(getString(R.string.error_extract, error));
                            }
                            headEmpty.setVisibility(View.VISIBLE);
                            return;
                        }
                        if (items == null || items.isEmpty()) {
                            headEmpty.setText(R.string.nothing_found);
                            headEmpty.setVisibility(View.VISIBLE);
                            return;
                        }
                        lastResults.addAll(items);
                        mediaAdapter.notifyDataSetChanged();
                        if (items.size() > 1) {
                            btnSaveAll.setText(getString(R.string.btn_save_all, items.size()));
                            btnSaveAll.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onSave(MediaItem m) {
        List<MediaItem> one = new ArrayList<MediaItem>();
        one.add(m);
        enqueueList(one);
    }

    private void enqueueList(final List<MediaItem> items) {
        if (items == null || items.isEmpty()) return;
        ensureStoragePermission(new Runnable() {
            public void run() {
                DownloadService.enqueue(MainActivity.this, items);
                toast(items.size() == 1
                        ? getString(R.string.added_to_queue)
                        : getString(R.string.save_all_added, items.size()));
            }
        });
    }

    // ------------------------------------------------------- browser panel

    private void setupBrowserPanel() {
        panelBrowser = (LinearLayout) getLayoutInflater().inflate(
                R.layout.panel_browser, container, false);
        container.addView(panelBrowser);
        panelBrowser.setVisibility(View.GONE);

        web = (WebView) panelBrowser.findViewById(R.id.web);
        etBrowser = (EditText) panelBrowser.findViewById(R.id.et_browser);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setUserAgentString(Extractor.UA);
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null) etBrowser.setText(url);
            }
        });

        web.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                onSave(new MediaItem(url, MediaItem.typeForUrl(url), url));
            }
        });

        web.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                WebView.HitTestResult r = web.getHitTestResult();
                if (r == null) return false;
                int t = r.getType();
                if (t == WebView.HitTestResult.IMAGE_TYPE
                        || t == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    final String u = r.getExtra();
                    if (u != null && u.startsWith("http")) {
                        confirmSave(u);
                        return true;
                    }
                }
                return false;
            }
        });

        panelBrowser.findViewById(R.id.btn_go).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                loadBrowserUrl(etBrowser.getText().toString());
            }
        });
        etBrowser.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    loadBrowserUrl(etBrowser.getText().toString());
                    return true;
                }
                return false;
            }
        });
        panelBrowser.findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (web.canGoBack()) web.goBack();
            }
        });
        panelBrowser.findViewById(R.id.btn_fwd).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (web.canGoForward()) web.goForward();
            }
        });
        panelBrowser.findViewById(R.id.btn_reload).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                web.reload();
            }
        });
        panelBrowser.findViewById(R.id.btn_open_ext).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String url = web.getUrl();
                if (url != null) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        panelBrowser.findViewById(R.id.btn_save_media).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                savePageMedia();
            }
        });
    }

    private void loadBrowserUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.length() == 0) return;
        String norm = Extractor.normalize(url);
        if (norm != null) {
            url = norm;
        } else if (url.contains(" ") || !url.contains(".")) {
            try {
                url = "https://www.google.com/search?q=" + URLEncoder.encode(url, "UTF-8");
            } catch (Exception e) {
                return;
            }
        } else {
            url = "https://" + url;
        }
        web.loadUrl(url);
        hideKeyboard();
    }

    private void confirmSave(final String url) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.type_image) + " — " + url)
                .setPositiveButton(R.string.btn_save, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        onSave(new MediaItem(url, "image", web.getUrl()));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Extract media from the HTML the WebView already loaded (keeps the user's login). */
    private void savePageMedia() {
        final String url = web.getUrl();
        if (url == null) return;
        toast(getString(R.string.extracting));
        web.evaluateJavascript("(function(){return document.documentElement.outerHTML})()",
                new ValueCallback<String>() {
                    public void onReceiveValue(String value) {
                        String html = value;
                        if (html != null && html.length() > 1) {
                            try {
                                JSONArray arr = new JSONArray("[" + html + "]");
                                Object o = arr.get(0);
                                html = (o == null || o == JSONObject_NULL) ? null : o.toString();
                            } catch (Exception ignored) {
                            }
                        }
                        final String h = html == null ? "" : html;
                        Extractor.parseAsync(h, url, new Extractor.Callback() {
                            public void onDone(final List<MediaItem> items, final String error) {
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        showResultsDialog(items, error);
                                    }
                                });
                            }
                        });
                    }
                });
    }

    private static final Object JSONObject_NULL = org.json.JSONObject.NULL;

    private void showResultsDialog(final List<MediaItem> items, final String error) {
        if (error != null || items == null || items.isEmpty()) {
            String msg = error != null
                    ? getString(R.string.error_extract, error)
                    : getString(R.string.nothing_found);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(msg)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }
        CharSequence[] names = new CharSequence[items.size()];
        for (int i = 0; i < items.size(); i++) {
            MediaItem m = items.get(i);
            names[i] = getString(Assets.labelFor(m.type)) + " " + (i + 1);
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.media_found, items.size()))
                .setItems(names, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        onSave(items.get(which));
                    }
                })
                .setPositiveButton(R.string.btn_save_all_plain, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        enqueueList(items);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------- history panel

    private void setupHistoryPanel() {
        panelHistory = (ListView) getLayoutInflater().inflate(
                R.layout.panel_history, container, false);
        container.addView(panelHistory);
        panelHistory.setVisibility(View.GONE);

        View head = getLayoutInflater().inflate(R.layout.head_history, panelHistory, false);
        panelHistory.addHeaderView(head, null, false);
        View he = getLayoutInflater().inflate(R.layout.head_empty, panelHistory, false);
        panelHistory.addFooterView(he, null, false);
        histEmpty = (TextView) he;
        histEmpty.setText(R.string.history_empty);

        historyAdapter = new HistoryAdapter();
        panelHistory.setAdapter(historyAdapter);

        head.findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(R.string.confirm_clear)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                HistoryDb.get(MainActivity.this).clear();
                                refreshHistory();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
        panelHistory.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int pos = position - panelHistory.getHeaderViewsCount();
                if (pos >= 0 && pos < historyRows.size()) openHistoryRow(historyRows.get(pos));
            }
        });
        panelHistory.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final int pos = position - panelHistory.getHeaderViewsCount();
                if (pos < 0 || pos >= historyRows.size()) return false;
                final HistoryDb.Row row = historyRows.get(pos);
                List<String> opts = new ArrayList<String>();
                opts.add(getString(R.string.open));
                opts.add(getString(R.string.share));
                opts.add(getString(R.string.delete));
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(row.name)
                        .setItems(opts.toArray(new CharSequence[0]),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int which) {
                                        if (which == 0) openHistoryRow(row);
                                        else if (which == 1) shareRow(row);
                                        else {
                                            HistoryDb.get(MainActivity.this).delete(row.id);
                                            refreshHistory();
                                        }
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return true;
            }
        });
    }

    private void refreshHistory() {
        historyRows.clear();
        historyRows.addAll(HistoryDb.get(this).list(200));
        historyAdapter.notifyDataSetChanged();
        histEmpty.setVisibility(historyRows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openHistoryRow(HistoryDb.Row row) {
        if (row.uri == null || "FAILED".equals(row.status)) {
            toast(getString(R.string.download_failed, getString(R.string.status_failed)));
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(row.uri), row.mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            toast(getString(R.string.open_failed));
        }
    }

    private void shareRow(HistoryDb.Row row) {
        if (row.uri == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(row.mime == null ? "*/*" : row.mime);
            i.putExtra(Intent.EXTRA_STREAM, Uri.parse(row.uri));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, getString(R.string.share)));
        } catch (Exception e) {
            toast(getString(R.string.open_failed));
        }
    }

    private class HistoryAdapter extends BaseAdapter {
        public int getCount() {
            return historyRows.size();
        }

        public Object getItem(int position) {
            return historyRows.get(position);
        }

        public long getItemId(int position) {
            return historyRows.get(position).id;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = getLayoutInflater().inflate(R.layout.item_history, parent, false);
            }
            HistoryDb.Row r = historyRows.get(position);
            String type = "doc";
            if (r.mime != null) {
                String t = MediaItem.typeForMime(r.mime);
                if (t != null) type = t;
            }
            ImageView iv = (ImageView) v.findViewById(R.id.iv_hist);
            iv.setImageResource(Assets.iconFor(type));
            iv.setImageTintList(ColorStateList.valueOf(Assets.colorFor(type)));

            TextView name = (TextView) v.findViewById(R.id.tv_hist_name);
            name.setText(r.name);

            TextView meta = (TextView) v.findViewById(R.id.tv_hist_meta);
            if ("FAILED".equals(r.status)) {
                meta.setText(R.string.status_failed);
                meta.setTextColor(0xFFFF3355);
            } else {
                String date = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                        .format(new Date(r.ts));
                String s = date;
                if (r.size > 0) s += " • " + humanSize(r.size);
                meta.setText(s);
                meta.setTextColor(0xFF9A9AA3);
            }
            return v;
        }
    }

    static String humanSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        return String.format(Locale.US, "%.1f MB", b / 1048576.0);
    }

    // ------------------------------------------------------------ lifecycle

    private void ensureStoragePermission(Runnable next) {
        if (Build.VERSION.SDK_INT >= 29) {
            next.run();
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            next.run();
            return;
        }
        pendingAction = next;
        requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQ_STORAGE);
    }

    private void requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
                            REQ_NOTIF);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingAction != null) {
                    Runnable r = pendingAction;
                    pendingAction = null;
                    r.run();
                }
            } else {
                pendingAction = null;
                toast(getString(R.string.perm_needed));
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        refreshReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (currentTab == 2) refreshHistory();
            }
        };
        IntentFilter f = new IntentFilter(DownloadService.ACTION_REFRESH);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(refreshReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(refreshReceiver, f);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (refreshReceiver != null) {
            try {
                unregisterReceiver(refreshReceiver);
            } catch (Exception ignored) {
            }
            refreshReceiver = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData cd = cm.getPrimaryClip();
                if (cd != null && cd.getItemCount() > 0) {
                    CharSequence t = cd.getItemAt(0).getText();
                    if (t != null) {
                        String s = t.toString().trim();
                        if (Extractor.normalize(s) != null
                                && etUrl.getText().toString().trim().length() == 0) {
                            etUrl.setText(s);
                            toast(getString(R.string.clipboard_found));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        if (currentTab == 1 && web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
