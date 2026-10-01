package com.xiaoyumi.biliweb;

import android.content.Context;
final class DebugPrefs {

    private static final String FILE = "biliweb_settings";
    private static final String KEY_NO_MULTIPROCESS_WEBVIEW = "no_multiprocess_webview";

    private DebugPrefs() {
    }

    static boolean isNoMultiProcessWebView(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getBoolean(KEY_NO_MULTIPROCESS_WEBVIEW, false);
    }

    static void setNoMultiProcessWebView(Context context, boolean enabled) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_NO_MULTIPROCESS_WEBVIEW, enabled).apply();
    }
}
