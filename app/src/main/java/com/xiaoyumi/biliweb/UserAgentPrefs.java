package com.xiaoyumi.biliweb;

import android.content.Context;

import java.util.regex.Pattern;
final class UserAgentPrefs {

    static final int MODE_DESKTOP = 0;
    static final int MODE_MOBILE = 1;

    static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    static final String MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.004) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
    static final int UA_OK = 0;
    static final int UA_EMPTY = 1;
    static final int UA_BAD_CHARS = 3;
    static final int UA_NOT_UA = 4;
    private static final Pattern UA_TOKEN =
            Pattern.compile("[A-Za-z][A-Za-z0-9._-]*/[A-Za-z0-9._-]+");

    private static final String FILE = "biliweb_settings";
    private static final String KEY_MODE = "ua_mode";
    private static final String KEY_CUSTOM_ENABLED = "ua_custom_enabled";
    private static final String KEY_CUSTOM_TEXT = "ua_custom_text";

    private UserAgentPrefs() {
    }

    static int getMode(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getInt(KEY_MODE, MODE_DESKTOP);
    }

    static void setMode(Context context, int mode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putInt(KEY_MODE, mode).apply();
    }

    static boolean isCustomEnabled(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getBoolean(KEY_CUSTOM_ENABLED, false);
    }

    static void setCustomEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_CUSTOM_ENABLED, enabled).apply();
    }

    static String getCustomText(Context context) {
        String text = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(KEY_CUSTOM_TEXT, "");
        return text == null ? "" : text;
    }

    static void setCustomText(Context context, String text) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY_CUSTOM_TEXT, text == null ? "" : text).apply();
    }
    static int checkUserAgent(String ua) {
        if (ua == null) {
            return UA_EMPTY;
        }
        String text = ua.trim();
        if (text.length() == 0) {
            return UA_EMPTY;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                return UA_BAD_CHARS;
            }
        }
        if (!UA_TOKEN.matcher(text).find()) {
            return UA_NOT_UA;
        }
        return UA_OK;
    }
    static String resolveUserAgent(Context context) {
        if (isCustomEnabled(context)) {
            String custom = getCustomText(context);
            if (checkUserAgent(custom) == UA_OK) {
                return custom.trim();
            }
        }
        return getMode(context) == MODE_MOBILE ? MOBILE_UA : DESKTOP_UA;
    }
}
