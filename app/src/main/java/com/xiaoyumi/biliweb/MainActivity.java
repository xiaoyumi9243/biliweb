package com.xiaoyumi.biliweb;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class MainActivity extends Activity {
    private static final String HOME_URL = "https://www.bilibili.com/";
    private static final int MAX_LIVE_VIEWS = 5;
    private static final int MAX_HISTORY = 100;
    private static final String STATE_URL = "biliweb_state_url";
    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_WEB_PERMISSION = 1002;
    private static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final boolean PROGRESS_ONLY_FIRST_LOAD = false;

    private FrameLayout rootView;
    private FrameLayout container;
    private ProgressBar progressBar;

    private final List<Page> pages = new ArrayList<Page>();
    private WebView webView;
    private VirtualMouseView mouseView;
    private View floatBall;
    private ImageView ballIcon;
    private TextView ballBadge;
    private PermissionRequest pendingWebPermission;
    private ValueCallback<Uri[]> pendingFileCallback;
    private static final class Page {
        String url;
        WebView view;

        Page(String url, WebView view) {
            this.url = url;
            this.view = view;
        }
    }
    private boolean firstLoadFinished = false;
    private String appliedLanguage;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OrientationPrefs.applyTo(this);  
        setContentView(R.layout.activity_main);

        rootView = (FrameLayout) findViewById(R.id.root);
        container = (FrameLayout) findViewById(R.id.container);
        progressBar = (ProgressBar) findViewById(R.id.progress);

        appliedLanguage = AppLanguage.get(this);
        EdgeToEdge.apply(this, rootView);
        registerBackCallback();
        setupFloatBall();
        setupVirtualMouse();

        String startUrl = HOME_URL;
        if (savedInstanceState != null) {
            String saved = savedInstanceState.getString(STATE_URL);
            if (saved != null && saved.length() > 0) {
                startUrl = saved;
            }
        }
        openNewPage(startUrl);
    }
    private void loadUrl(WebView view, String url) {
        String acceptLanguage = AppLanguage.acceptLanguage(this);
        if (acceptLanguage == null) {
            view.loadUrl(url);
            return;
        }
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept-Language", acceptLanguage);
        view.loadUrl(url, headers);
    }
    private void openNewPage(String url) {
        if (webView != null) {
            updateCurrentUrl();
            pauseMedia(webView);      
            webView.setVisibility(View.GONE);
            webView.onPause();         
        }
        WebView view = createWebView();
        view.setVisibility(View.VISIBLE);
        container.addView(view);
        pages.add(new Page(url, view));
        webView = view;
        loadUrl(view, url);
        trimLiveViews();
        trimHistory();
    }
    private boolean popPage() {
        if (pages.size() <= 1) {
            return false;
        }
        Page current = pages.remove(pages.size() - 1);
        if (current.view != null) {
            removeAndDestroy(current.view);
            current.view = null;
        }

        Page top = pages.get(pages.size() - 1);
        if (top.view == null) {
            top.view = createWebView();
            container.addView(top.view);
            loadUrl(top.view, top.url);
        }
        top.view.setVisibility(View.VISIBLE);
        top.view.onResume();
        webView = top.view;
        trimLiveViews();
        return true;
    }

    private void pauseMedia(WebView view) {
        try {
            view.evaluateJavascript(
                    "(function(){var m=document.querySelectorAll('video,audio');"
                            + "for(var i=0;i<m.length;i++){try{m[i].pause();}catch(e){}}})()",
                    null);
        } catch (Exception ignored) {
        }
    }
    private void updateCurrentUrl() {
        if (webView == null || pages.isEmpty()) {
            return;
        }
        String real = webView.getUrl();
        if (real != null && real.length() > 0) {
            pages.get(pages.size() - 1).url = real;
        }
    }
    private void trimLiveViews() {
        int live = 0;
        for (Page p : pages) {
            if (p.view != null) {
                live++;
            }
        }
        for (Page p : pages) {
            if (live <= MAX_LIVE_VIEWS) {
                break;
            }
            if (p.view != null && p.view != webView) {
                removeAndDestroy(p.view);
                p.view = null;
                live--;
            }
        }
    }
    private void trimHistory() {
        while (pages.size() > MAX_HISTORY) {
            Page oldest = pages.remove(0);
            if (oldest.view != null) {
                removeAndDestroy(oldest.view);
                oldest.view = null;
            }
        }
    }

    private void removeAndDestroy(WebView view) {
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) {
            parent.removeView(view);
        }
        view.stopLoading();
        view.setWebChromeClient(null);
        view.setWebViewClient(null);
        view.destroy();
    }
    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView() {
        WebView view = new WebView(this);
        view.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        WebSettings settings = view.getSettings();
        settings.setUserAgentString(DESKTOP_USER_AGENT);
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setDisplayZoomControls(false);
        applyZoomSupport(view);
        settings.setAllowFileAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return handleNavigation(request.isForMainFrame(), request.getUrl().toString());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return handleNavigation(true, url);
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                PageZoom.apply(v, ZoomPrefs.get(MainActivity.this));
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                PageZoom.apply(v, ZoomPrefs.get(MainActivity.this));
            }
        });

        view.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(WebView v, int newProgress) {
                if (progressBar == null) {
                    return;
                }
                if (PROGRESS_ONLY_FIRST_LOAD && firstLoadFinished) {
                    return;
                }
                if (newProgress >= 100) {
                    firstLoadFinished = true;
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                }
            }

      
            @Override
            public boolean onJsAlert(WebView v, String url, String message, JsResult result) {
                if (isFinishing()) {
                    result.cancel();
                    return true;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(pageTitle(url))
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String message, JsResult result) {
                if (isFinishing()) {
                    result.cancel();
                    return true;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(pageTitle(url))
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                        .setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView v, String url, String message, String defaultValue,
                                      JsPromptResult result) {
                if (isFinishing()) {
                    result.cancel();
                    return true;
                }
                EditText input = new EditText(MainActivity.this);
                input.setText(defaultValue == null ? "" : defaultValue);
                input.setSelection(input.getText().length());

                int pad = (int) (getResources().getDisplayMetrics().density * 20);
                FrameLayout box = new FrameLayout(MainActivity.this);
                box.setPadding(pad, pad / 2, pad, 0);
                box.addView(input);

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(pageTitle(url))
                        .setMessage(message)
                        .setView(box)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok,
                                (d, w) -> result.confirm(input.getText().toString()))
                        .setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                handleWebPermission(request);
            }
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                return openFileChooser(filePathCallback, fileChooserParams);
            }
        });
        return view;
    }
    private boolean handleNavigation(boolean isMainFrame, String url) {
        if (url == null) {
            return false;
        }
        boolean isHttp = url.startsWith("http://") || url.startsWith("https://");
        if (isHttp) {
            if (!isMainFrame) {
                return false;
            }
            openNewPage(url);
            return true;
        }
        if (!isMainFrame) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ignored) {
        }
        return true;
    }
    private String pageTitle(String url) {
        String host = null;
        if (url != null) {
            try {
                host = Uri.parse(url).getHost();
            } catch (Exception ignored) {
            }
        }
        return host != null ? host : getString(R.string.app_name);
    }
    private void handleWebPermission(PermissionRequest request) {
        List<String> runtimePerms = new ArrayList<String>();
        for (String res : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) {
                runtimePerms.add(Manifest.permission.CAMERA);
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) {
                runtimePerms.add(Manifest.permission.RECORD_AUDIO);
            }
        }

        if (runtimePerms.isEmpty()) {
            request.grant(request.getResources());
            return;
        }

        if (Build.VERSION.SDK_INT >= 23) {
            boolean allGranted = true;
            for (String perm : runtimePerms) {
                if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                request.grant(request.getResources());
                return;
            }
            if (pendingWebPermission != null) {
                pendingWebPermission.deny();
            }
            pendingWebPermission = request;
            requestPermissions(runtimePerms.toArray(new String[0]), REQ_WEB_PERMISSION);
        } else {
            request.grant(request.getResources());
        }
    }
    @Override
    @SuppressLint("NewApi")
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQ_WEB_PERMISSION || pendingWebPermission == null) {
            return;
        }
        PermissionRequest request = pendingWebPermission;
        pendingWebPermission = null;

        boolean allGranted = grantResults.length > 0;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (allGranted) {
            request.grant(request.getResources());
        } else {
            request.deny();
        }
    }
    private boolean openFileChooser(ValueCallback<Uri[]> callback,
                                    WebChromeClient.FileChooserParams params) {
        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(null);
            pendingFileCallback = null;
        }
        pendingFileCallback = callback;
        try {
            startActivityForResult(params.createIntent(), REQ_FILE_CHOOSER);
            return true;
        } catch (Exception e) {
            pendingFileCallback = null;
            callback.onReceiveValue(null);
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE_CHOOSER || pendingFileCallback == null) {
            return;
        }
        ValueCallback<Uri[]> callback = pendingFileCallback;
        pendingFileCallback = null;
        callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
    }
    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::onBackInvoked);
        }
    }

    private void onBackInvoked() {
        if (!handleBack()) {
            finish();
        }
    }

    @Override
    @SuppressLint("GestureBackNavigation")
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && handleBack()) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    private boolean handleBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return popPage();
    }
    private void setupVirtualMouse() {
        mouseView = new VirtualMouseView(this, () -> webView);
        rootView.addView(mouseView, 2, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        applyMouseMode();
    }
    private void applyMouseMode() {
        if (mouseView == null) {
            return;
        }
        boolean enabled = MousePointerPrefs.isEnabled(this);
        mouseView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        mouseView.setCursorScalePercent(MousePointerPrefs.getCursorScale(this));
        updateFloatBallAppearance();
        for (Page p : pages) {
            if (p.view != null) {
                applyZoomSupport(p.view);
            }
        }
    }
    private void applyZoomSupport(WebView view) {
        boolean mouseOn = MousePointerPrefs.isEnabled(this);
        WebSettings settings = view.getSettings();
        settings.setSupportZoom(!mouseOn);
        settings.setBuiltInZoomControls(!mouseOn);
        settings.setDisplayZoomControls(false);
    }
    private void setupFloatBall() {
        final View ball = findViewById(R.id.float_ball);
        floatBall = ball;
        ballIcon = (ImageView) findViewById(R.id.float_ball_icon);
        ballBadge = (TextView) findViewById(R.id.float_ball_badge);

        ball.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        ball.setOnTouchListener(new View.OnTouchListener() {
            private final int slop =
                    ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
            private final Runnable longPressAction = new Runnable() {
                @Override
                public void run() {
                    if (dragging) {
                        return;
                    }
                    longPressed = true;
                    toggleVirtualMouse();
                }
            };
            private float downRawX, downRawY, startBallX, startBallY;
            private boolean dragging;
            private boolean longPressed;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startBallX = v.getX();
                        startBallY = v.getY();
                        dragging = false;
                        longPressed = false;
                        if (MousePointerPrefs.isQuickToggleEnabled(MainActivity.this)) {
                            v.postDelayed(longPressAction, ViewConfiguration.getLongPressTimeout());
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (!dragging && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                            dragging = true;
                            v.removeCallbacks(longPressAction);
                        }
                        if (dragging) {
                            moveBall(v, startBallX + dx, startBallY + dy);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        v.removeCallbacks(longPressAction);
                        if (!dragging && !longPressed) {
                            v.performClick();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        v.removeCallbacks(longPressAction);
                        return true;

                    default:
                        return false;
                }
            }
        });

        updateFloatBallAppearance();
    }
    private void updateFloatBallAppearance() {
        if (floatBall == null) {
            return;
        }
        float scale = FloatBallPrefs.getSize(this) / 100f;
        floatBall.setScaleX(scale);
        floatBall.setScaleY(scale);
        floatBall.setAlpha(FloatBallPrefs.getOpacity(this) / 100f);

        if (ballIcon == null || ballBadge == null) {
            return;
        }
        boolean mouseOn = MousePointerPrefs.isEnabled(this);
        ballIcon.setImageResource(mouseOn
                ? R.drawable.ic_mouse_cursor_white
                : R.drawable.ic_settings);
        ballBadge.setVisibility(mouseOn ? View.VISIBLE : View.GONE);
    }
    private void toggleVirtualMouse() {
        boolean enabled = !MousePointerPrefs.isEnabled(this);
        MousePointerPrefs.setEnabled(this, enabled);
        applyMouseMode();

        if (floatBall != null) {
            floatBall.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        Toast.makeText(this,
                enabled ? R.string.mouse_toggled_on : R.string.mouse_toggled_off,
                Toast.LENGTH_SHORT).show();
    }
    private void moveBall(View ball, float x, float y) {
        float offsetX = ball.getWidth() * (ball.getScaleX() - 1f) / 2f;
        float offsetY = ball.getHeight() * (ball.getScaleY() - 1f) / 2f;
        float minX = rootView.getPaddingLeft() + offsetX;
        float minY = rootView.getPaddingTop() + offsetY;
        float maxX = rootView.getWidth() - rootView.getPaddingRight() - ball.getWidth() - offsetX;
        float maxY = rootView.getHeight() - rootView.getPaddingBottom() - ball.getHeight() - offsetY;
        ball.setX(Math.max(minX, Math.min(x, maxX)));
        ball.setY(Math.max(minY, Math.min(y, maxY)));
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (!AppLanguage.get(this).equals(appliedLanguage)) {
            recreate();
            return;
        }
        OrientationPrefs.applyTo(this);
        applyMouseMode();
        int zoom = ZoomPrefs.get(this);
        for (Page p : pages) {
            if (p.view != null) {
                PageZoom.apply(p.view, zoom);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (floatBall != null) {
            moveBall(floatBall, floatBall.getX(), floatBall.getY());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null && webView.getUrl() != null) {
            outState.putString(STATE_URL, webView.getUrl());
        }
    }

    @Override
    protected void onDestroy() {
        for (Page p : pages) {
            if (p.view != null) {
                removeAndDestroy(p.view);
                p.view = null;
            }
        }
        pages.clear();
        webView = null;
        super.onDestroy();
    }
}
