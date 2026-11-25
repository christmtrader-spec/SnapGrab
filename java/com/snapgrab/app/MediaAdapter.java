package com.snapgrab.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * Adapter for the extracted-media lists (Download panel + Share dialog).
 */
public class MediaAdapter extends BaseAdapter {

    public interface OnSave {
        void onSave(MediaItem m);
    }

    private final Context ctx;
    private final List<MediaItem> items;
    private final OnSave onSave;

    public MediaAdapter(Context ctx, List<MediaItem> items, OnSave onSave) {
        this.ctx = ctx;
        this.items = items;
        this.onSave = onSave;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(ctx).inflate(R.layout.item_media, parent, false);
        }
        final MediaItem m = items.get(position);

        ImageView iv = (ImageView) v.findViewById(R.id.iv_type);
        iv.setImageResource(Assets.iconFor(m.type));
        iv.setImageTintList(ColorStateList.valueOf(Assets.colorFor(m.type)));

        TextView name = (TextView) v.findViewById(R.id.tv_name);
        name.setText(ctx.getString(Assets.labelFor(m.type)) + " " + (position + 1));

        TextView url = (TextView) v.findViewById(R.id.tv_url);
        String shortUrl = m.url;
        try {
            String p = Uri.parse(m.url).getPath();
            if (p != null && p.length() > 1) shortUrl = p;
        } catch (Exception ignored) {
        }
        url.setText(shortUrl);

        TextView save = (TextView) v.findViewById(R.id.btn_item_save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onSave != null) onSave.onSave(m);
            }
        });
        return v;
    }
}
