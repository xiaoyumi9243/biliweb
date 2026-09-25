package com.xiaoyumi.biliweb;

import android.content.Context;

final class MousePointerPrefs {

    static final int MIN_CURSOR_SCALE = 50;
    static final int MAX_CURSOR_SCALE = 200;
    static final int DEFAULT_CURSOR_SCALE = 100;

    private static final String FILE = "biliweb_settings";
    private static final String KEY_ENABLED = "virtual_mouse";
    private static final String KEY_CURSOR_SCALE = "cursor_scale";
    private static final String KEY_QUICK_TOGGLE = "mouse_quick_toggle";

    private MousePointerPrefs() {
    }

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    static int getCursorScale(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getInt(KEY_CURSOR_SCALE, DEFAULT_CURSOR_SCALE);
    }

    static void setCursorScale(Context context, int percent) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putInt(KEY_CURSOR_SCALE, percent).apply();
    }

    static boolean isQuickToggleEnabled(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getBoolean(KEY_QUICK_TOGGLE, false);
    }

    static void setQuickToggleEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_QUICK_TOGGLE, enabled).apply();
    }
}
