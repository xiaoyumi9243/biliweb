package com.xiaoyumi.biliweb;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;
final class UpdateChecker {
    private static final String UPDATE_HOST_PATH = "xiaoyumi9243.wuaze.com/biliweb/cu.php";
    private static final String[] UPDATE_URLS = {
            "https://" + UPDATE_HOST_PATH,
            "http://" + UPDATE_HOST_PATH,
    };
    private static final int HTTP_TIMEOUT_MS = 6000;
    private static final int WEBVIEW_TIMEOUT_MS = 12000;
    private static final int WEBVIEW_MAX_TRIES = 5;
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Mobile Safari/537.36";
    static final class Result {
        boolean failed;
        String failReason = "";
        boolean upToDate;
        String latestVersion = "";
        String downloadUrl = "";
        String changelogUrl = "";
    }
    interface Callback {
        void onResult(Result result);
    }
    private UpdateChecker() {
    }
    static void check(final Activity activity, final Callback callback) {
        final String current = currentVersion(activity);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Result result = httpCheck(activity, current);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (!result.failed) {
                            callback.onResult(result);
                        } else if (activity.isFinishing()) {
                            callback.onResult(result);
                        } else {
                            new WebViewFetch(activity, current, callback, result.failReason).start();
                        }
                    }
                });
            }
        }, "biliweb-update-check").start();
    }
    static String currentVersion(android.content.Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }
    private static Result httpCheck(Context context, String currentVersion) {
        StringBuilder reasons = new StringBuilder();
        Result last = null;
        for (int i = 0; i < UPDATE_URLS.length; i++) {
            Result result = httpCheckOne(context, UPDATE_URLS[i], currentVersion);
            if (!result.failed) {
                return result;
            }
            if (reasons.length() > 0) {
                reasons.append(context.getString(R.string.update_fail_separator));
            }
            reasons.append(i == 0 ? "HTTPS " : "HTTP ").append(result.failReason);
            last = result;
        }
        if (last != null) {
            last.failReason = reasons.toString();
        }
        return last;
    }

    private static Result httpCheckOne(Context context, String api, String currentVersion) {
        Result result = new Result();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(api).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(HTTP_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", BROWSER_UA);
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");

            int code = connection.getResponseCode();
            InputStream stream = (code >= 200 && code < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = stream == null ? "" : readAll(stream);
            if (code < 200 || code >= 300) {
                return fail(result, context.getString(R.string.update_fail_http, code));
            }
            Result parsed = parseJson(body, currentVersion);
            return parsed != null ? parsed : fail(result, context.getString(R.string.update_fail_not_json));
        } catch (Exception e) {
            return fail(result, reasonOf(context, e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line).append('\n');
        }
        reader.close();
        return body.toString();
    }

    private static Result fail(Result result, String reason) {
        result.failed = true;
        result.failReason = reason;
        return result;
    }

    private static String reasonOf(Context context, Exception e) {
        if (e instanceof SocketTimeoutException) {
            return context.getString(R.string.update_fail_timeout);
        }
        if (e instanceof UnknownHostException) {
            return context.getString(R.string.update_fail_dns);
        }
        if (e instanceof SSLException) {
            return context.getString(R.string.update_fail_tls);
        }
        if (e instanceof IOException) {
            return context.getString(R.string.update_fail_network);
        }
        return e.getClass().getSimpleName();
    }

    private static final class WebViewFetch {
        private final Activity activity;
        private final String currentVersion;
        private final Callback callback;
        private final String httpFailReason;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final WebView web;
        private ViewGroup host;
        private int urlIndex;
        private int tries;
        private boolean done;

        WebViewFetch(Activity activity, String currentVersion, Callback callback, String httpFailReason) {
            this.activity = activity;
            this.currentVersion = currentVersion;
            this.callback = callback;
            this.httpFailReason = httpFailReason;
            this.web = new WebView(activity);
        }

        void start() {
            host = (ViewGroup) activity.findViewById(android.R.id.content);
            if (host == null) {
                callback.onResult(fail(new Result(), httpFailReason));
                return;
            }
            web.setLayoutParams(new FrameLayout.LayoutParams(1, 1));
            web.setAlpha(0f);
            host.addView(web);

            web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setUserAgentString(BROWSER_UA);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    handler.postDelayed(WebViewFetch.this::read, 600);
                }
            });
            startUrl();
        }
        private void startUrl() {
            tries = 0;
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(this::timeout, WEBVIEW_TIMEOUT_MS);
            web.loadUrl(UPDATE_URLS[urlIndex]);
        }

        private void read() {
            if (done) {
                return;
            }
            web.evaluateJavascript("(document.body ? document.body.innerText : '')", value -> {
                if (done) {
                    return;
                }
                Result result = parseJson(unquote(value), currentVersion);
                if (result != null) {
                    finish(result);
                } else if (++tries < WEBVIEW_MAX_TRIES) {
                    handler.postDelayed(this::read, 800);
                } else if (urlIndex + 1 < UPDATE_URLS.length) {
                    urlIndex++;
                    startUrl();
                } else {
                    Result failResult = new Result();
                    failResult.failed = true;
                    failResult.failReason = activity.getString(
                            R.string.update_fail_not_json_direct, httpFailReason);
                    finish(failResult);
                }
            });
        }

        private void timeout() {
            if (done) {
                return;
            }
            if (urlIndex + 1 < UPDATE_URLS.length) {
                urlIndex++;
                startUrl();
                return;
            }
            Result result = new Result();
            result.failed = true;
            result.failReason = activity.getString(R.string.update_fail_webview_timeout, httpFailReason);
            finish(result);
        }

        private void finish(Result result) {
            if (done) {
                return;
            }
            done = true;
            handler.removeCallbacksAndMessages(null);
            try {
                if (host != null) {
                    host.removeView(web);
                }
                web.stopLoading();
                web.setWebViewClient(null);
                web.destroy();
            } catch (Exception ignored) {
            }
            callback.onResult(result);
        }
    }
    private static Result parseJson(String body, String currentVersion) {
        if (body == null) {
            return null;
        }
        String text = body.trim();
        if (text.length() == 0) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(text);
            Result result = new Result();
            result.latestVersion = json.optString("latest_version", "");
            result.downloadUrl = json.optString("download_url", "");
            result.changelogUrl = json.optString("changelog_url", "");

            boolean disabled = json.optBoolean("is_disabled", false);
            result.upToDate = disabled
                    || result.latestVersion.length() == 0
                    || compareVersion(result.latestVersion, currentVersion) <= 0;
            return result;
        } catch (Exception e) {
            return null;
        }
    }
    private static String unquote(String jsValue) {
        if (jsValue == null || jsValue.length() == 0 || "null".equals(jsValue)) {
            return "";
        }
        try {
            return new JSONArray("[" + jsValue + "]").getString(0);
        } catch (Exception e) {
            return jsValue;
        }
    }
    static int compareVersion(String a, String b) {
        String[] pa = (a == null ? "" : a).split("\\.");
        String[] pb = (b == null ? "" : b).split("\\.");
        int count = Math.max(pa.length, pb.length);
        for (int i = 0; i < count; i++) {
            int va = i < pa.length ? leadingNumber(pa[i]) : 0;
            int vb = i < pb.length ? leadingNumber(pb[i]) : 0;
            if (va != vb) {
                return va > vb ? 1 : -1;
            }
        }
        return 0;
    }

    private static int leadingNumber(String part) {
        int value = 0;
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            value = value * 10 + (c - '0');
        }
        return value;
    }
}
