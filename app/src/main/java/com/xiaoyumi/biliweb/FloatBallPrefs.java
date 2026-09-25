package com.xiaoyumi.biliweb;

import android.content.Context;
final class FloatBallPrefs {

    static final int MIN_SIZE = 50;
    static final int MAX_SIZE = 150;
    static final int DEFAULT_SIZE = 100;
    static final int MIN_OPACITY = 20;
    static final int MAX_OPACITY = 100;
    static final int DEFAULT_OPACITY = 100;

    private static final String FILE = "biliweb_settings";
    private static final String KEY_SIZE = "float_ball_size";
    private static final String KEY_OPACITY = "float_ball_opacity";

    private FloatBallPrefs() {
    }

    static int getSize(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getInt(KEY_SIZE, DEFAULT_SIZE);
    }

    static void setSize(Context context, int percent) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putInt(KEY_SIZE, percent).apply();
    }

    static int getOpacity(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getInt(KEY_OPACITY, DEFAULT_OPACITY);
    }

    static void setOpacity(Context context, int percent) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putInt(KEY_OPACITY, percent).apply();
    }
}
