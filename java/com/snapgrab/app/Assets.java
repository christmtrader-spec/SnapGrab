package com.snapgrab.app;

public class Assets {
    public static int iconFor(String type) {
        if ("image".equals(type)) return R.drawable.ic_image;
        if ("video".equals(type)) return R.drawable.ic_video;
        if ("audio".equals(type)) return R.drawable.ic_music;
        return R.drawable.ic_doc;
    }

    public static int colorFor(String type) {
        if ("image".equals(type)) return 0xFFFF3355;
        if ("video".equals(type)) return 0xFF7C5CFF;
        if ("audio".equals(type)) return 0xFFFFB020;
        return 0xFF35C4CF;
    }

    public static int labelFor(String type) {
        if ("image".equals(type)) return R.string.type_image;
        if ("video".equals(type)) return R.string.type_video;
        if ("audio".equals(type)) return R.string.type_audio;
        return R.string.type_doc;
    }
}
