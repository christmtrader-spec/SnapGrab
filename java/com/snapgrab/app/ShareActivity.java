package com.snapgrab.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Receives links shared from Instagram / Pinterest / browsers,
 * extracts media and lets the user save it in one tap.
 */
public class ShareActivity extends Activity implements MediaAdapter.OnSave {

    private static final int REQ_STORAGE = 51;

    private MediaAdapter adapter;
    private final List<MediaItem> items = new ArrayList<MediaItem>();
    private ProgressBar pb;
    private TextView status;
    private ListView list;
    private TextView btnAll;
    private List<MediaItem> pendingItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

        pb = (ProgressBar) findViewById(R.id.pb_share);
        status = (TextView) findViewById(R.id.tv_share_status);
        list = (ListView) findViewById(R.id.list_share);
        btnAll = (TextView) findViewById(R.id.btn_share_all);

        findViewById(R.id.btn_share_cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        btnAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                saveAll();
            }
        });

        String text = getIntent() == null ? null : getIntent().getStringExtra(Intent.EXTRA_TEXT);
        final String url = Extractor.normalize(text);
        if (url == null) {
            pb.setVisibility(View.GONE);
            status.setText(R.string.not_a_url);
            return;
        }

        String direct = MediaItem.extType(url);
        if (direct != null) {
            items.add(new MediaItem(url, direct, url));
            showItems();
            return;
        }

        Extractor.extractAsync(url, new Extractor.Callback() {
            public void onDone(final List<MediaItem> result, final String error) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        pb.setVisibility(View.GONE);
                        if (error != null) {
                            if (error.contains("LOGIN_WALL")) {
                                status.setText(R.string.login_wall);
                            } else {
                                status.setText(getString(R.string.error_extract, error));
                            }
                            return;
                        }
                        if (result == null || result.isEmpty()) {
                            status.setText(R.string.nothing_found);
                            return;
                        }
                        items.addAll(result);
                        showItems();
                    }
                });
            }
        });
    }

    private void showItems() {
        status.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
        adapter = new MediaAdapter(this, items, this);
        list.setAdapter(adapter);
        if (items.size() > 1) {
            btnAll.setText(getString(R.string.btn_save_all, items.size()));
            btnAll.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onSave(MediaItem m) {
        List<MediaItem> one = new ArrayList<MediaItem>();
        one.add(m);
        enqueueChecked(one);
    }

    private void saveAll() {
        enqueueChecked(items);
    }

    private void enqueueChecked(List<MediaItem> its) {
        if (Build.VERSION.SDK_INT >= 29
                || checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            doEnqueue(its);
            return;
        }
        pendingItems = its;
        requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQ_STORAGE);
    }

    private void doEnqueue(List<MediaItem> its) {
        DownloadService.enqueue(this, its);
        Toast.makeText(this, its.size() == 1
                        ? getString(R.string.added_to_queue)
                        : getString(R.string.save_all_added, its.size()),
                Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && pendingItems != null) {
                doEnqueue(pendingItems);
            } else {
                pendingItems = null;
                Toast.makeText(this, R.string.perm_needed, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
