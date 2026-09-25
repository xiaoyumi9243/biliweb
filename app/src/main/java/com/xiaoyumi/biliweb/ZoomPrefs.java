package com.xiaoyumi.biliweb;

import android.content.Context;
final class ZoomPrefs {
    static final int DEFAULT_ZOOM = 100;
    static final int MIN_ZOOM = 50;
    static final int MAX_ZOOM = 200;

    private static final String FILE = "biliweb_settings";
    private static final String KEY_ZOOM = "text_zoom";

    private ZoomPrefs() {
    }

    static int get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getInt(KEY_ZOOM, DEFAULT_ZOOM);
    }

    static void set(Context context, int zoom) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putInt(KEY_ZOOM, zoom).apply();
    }
}
