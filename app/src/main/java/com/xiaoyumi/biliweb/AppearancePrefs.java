package com.xiaoyumi.biliweb;

import android.content.Context;

/**
 * 外观设置：主界面用哪种方案。
 *
 * - 全屏（默认）：只有网页内容和悬浮球，和之前一样。
 * - 顶部导航栏：顶部显示后退 / 前进 / 刷新 / 地址栏 / 标签页 / 设置按钮，不再显示悬浮球。
 */
final class AppearancePrefs {

    static final int MODE_FULLSCREEN = 0;
    static final int MODE_TOOLBAR = 1;

    private static final String FILE = "biliweb_settings";
    private static final String KEY_MODE = "appearance_mode";

    private AppearancePrefs() {
    }

    static int get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getInt(KEY_MODE, MODE_FULLSCREEN);
    }

    static void set(Context context, int mode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putInt(KEY_MODE, mode).apply();
    }
}
