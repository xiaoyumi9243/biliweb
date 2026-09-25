package com.xiaoyumi.biliweb;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;
final class AppLanguage {

    static final String AUTO = "auto";
    static final String CHINESE = "zh";
    static final String ENGLISH = "en";

    private static final String FILE = "biliweb_settings";
    private static final String KEY_LANGUAGE = "app_language";

    private AppLanguage() {
    }

    static String get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, AUTO);
    }

    static void set(Context context, String code) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, code).apply();
    }

    static Locale currentLocale(Context context) {
        String code = get(context);
        if (ENGLISH.equals(code)) {
            return Locale.ENGLISH;
        }
        if (CHINESE.equals(code)) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        return systemLocale();
    }

    static Locale systemLocale() {
        Configuration config = Resources.getSystem().getConfiguration();
        if (Build.VERSION.SDK_INT >= 24) {
            return config.getLocales().get(0);
        }
        return config.locale;
    }
    
    static String acceptLanguage(Context context) {
        String code = get(context);
        if (!CHINESE.equals(code) && !ENGLISH.equals(code)) {
            return null;
        }
        Locale locale = currentLocale(context);
        String language = locale.getLanguage();
        String region = locale.toString().replace('_', '-');
        return region + "," + language + ";q=0.9";
    }

    static Context wrap(Context context) {
        String code = get(context);
        if (!CHINESE.equals(code) && !ENGLISH.equals(code)) {
            Locale.setDefault(systemLocale());
            return context;
        }
        Locale locale = currentLocale(context);
        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        if (Build.VERSION.SDK_INT >= 24) {
            return context.createConfigurationContext(config);
        }
        context.getResources().updateConfiguration(config,
                context.getResources().getDisplayMetrics());
        return context;
    }

    static String displayName(Context context) {
        String code = get(context);
        if (CHINESE.equals(code)) {
            return context.getString(R.string.language_chinese);
        }
        if (ENGLISH.equals(code)) {
            return context.getString(R.string.language_english);
        }
        return context.getString(R.string.language_follow_system);
    }
}
