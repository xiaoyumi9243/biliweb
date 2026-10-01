package com.xiaoyumi.biliweb;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import java.util.HashMap;
import java.util.Map;
public class WebViewCheckActivity extends Activity {

    private static final String CHECK_URL = "file:///android_asset/check.html";

    private FrameLayout rootView;
    private FrameLayout container;
    private WebView webView;
    private VirtualMouseView mouseView;
    private String appliedLanguage;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OrientationPrefs.applyTo(this);
        setContentView(R.layout.activity_webview_check);

        rootView = (FrameLayout) findViewById(R.id.check_root);
        container = (FrameLayout) findViewById(R.id.check_container);
        ((TextView) findViewById(R.id.top_title)).setText(R.string.webview_test_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        appliedLanguage = AppLanguage.get(this);
        EdgeToEdge.apply(this, rootView);
        registerBackCallback();

        webView = createWebView();
        container.addView(webView);
        setupMouse();
        loadCheckPage();
    }

    private void loadCheckPage() {
        String acceptLanguage = AppLanguage.acceptLanguage(this);
        if (acceptLanguage == null) {
            webView.loadUrl(CHECK_URL);
            return;
        }
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept-Language", acceptLanguage);
        webView.loadUrl(CHECK_URL, headers);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView() {
        WebView view = new WebView(this);
        view.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        WebSettings settings = view.getSettings();
        settings.setUserAgentString(UserAgentPrefs.resolveUserAgent(this));
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        applyZoomSupport(view);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                PageZoom.apply(v, ZoomPrefs.get(WebViewCheckActivity.this));
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                PageZoom.apply(v, ZoomPrefs.get(WebViewCheckActivity.this));
            }
        });
        return view;
    }

    private void setupMouse() {
        mouseView = new VirtualMouseView(this, () -> webView);
        container.addView(mouseView, new FrameLayout.LayoutParams(
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
        if (webView != null) {
            applyZoomSupport(webView);
        }
    }

    private void applyZoomSupport(WebView view) {
        boolean mouseOn = MousePointerPrefs.isEnabled(this);
        WebSettings settings = view.getSettings();
        settings.setSupportZoom(!mouseOn);
        settings.setBuiltInZoomControls(!mouseOn);
        settings.setDisplayZoomControls(false);
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
        return false;
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
        if (webView != null) {
            PageZoom.apply(webView, ZoomPrefs.get(this));
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
