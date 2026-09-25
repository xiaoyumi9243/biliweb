package com.xiaoyumi.biliweb;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
final class OrientationPrefs {

    static final int MODE_DEFAULT = 0;
    static final int MODE_LANDSCAPE = 1;

    private static final String FILE = "biliweb_settings";
    private static final String KEY_MODE = "orientation_mode";

    private OrientationPrefs() {
    }

    static int get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getInt(KEY_MODE, MODE_DEFAULT);
    }

    static void set(Context context, int mode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putInt(KEY_MODE, mode).apply();
    }
    static void applyTo(Activity activity) {
        activity.setRequestedOrientation(get(activity) == MODE_LANDSCAPE
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
}
