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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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

    private static final boolean PROGRESS_ONLY_FIRST_LOAD = false;

    private FrameLayout rootView;
    private FrameLayout contentFrame;
    private FrameLayout container;
    private ProgressBar progressBar;

    private View topBar;
    private View topBarDivider;
    private ImageButton navBack;
    private ImageButton navForward;
    private ImageButton navRefresh;
    private ImageButton navTabs;
    private TextView navTabsBadge;
    private ImageButton navSettings;
    private EditText navAddress;

    private final List<Page> pages = new ArrayList<Page>();
    private int activeIndex = -1;
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
    private String appliedUserAgent;
    private boolean noMultiProcess;
    private int appearance;

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
        contentFrame = (FrameLayout) findViewById(R.id.content_frame);
        container = (FrameLayout) findViewById(R.id.container);
        progressBar = (ProgressBar) findViewById(R.id.progress);

        appearance = AppearancePrefs.get(this);
        appliedLanguage = AppLanguage.get(this);
        EdgeToEdge.apply(this, rootView);
        registerBackCallback();
        setupTopBar();
        setupFloatBall();
        setupVirtualMouse();
        applyAppearance();

        String startUrl = HOME_URL;
        if (savedInstanceState != null) {
            String saved = savedInstanceState.getString(STATE_URL);
            if (saved != null && saved.length() > 0) {
                startUrl = saved;
            }
        }
        noMultiProcess = DebugPrefs.isNoMultiProcessWebView(this);
        appliedUserAgent = UserAgentPrefs.resolveUserAgent(this);
        openNewPage(startUrl);

        // 顶栏地址输入框不要一进来就抢焦点（否则会弹输入法）
        rootView.setFocusableInTouchMode(true);
        rootView.requestFocus();
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
            if (noMultiProcess) {
                // 不使用多进程 WebView：不保留上一页，返回时重新加载
                if (activeIndex >= 0 && activeIndex < pages.size()) {
                    pages.get(activeIndex).view = null;
                }
                removeAndDestroy(webView);
            } else {
                webView.setVisibility(View.GONE);
                webView.onPause();
            }
        }
        WebView view = createWebView();
        view.setVisibility(View.VISIBLE);
        container.addView(view);
        pages.add(new Page(url, view));
        activeIndex = pages.size() - 1;
        webView = view;
        loadUrl(view, url);
        trimLiveViews();
        trimHistory();
        updateToolbarState();
        updateAddressBar();
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
        if (top.view == null || noMultiProcess) {
            // 不使用多进程 WebView：即使是缓存着的页面也重新加载
            if (top.view != null) {
                removeAndDestroy(top.view);
                top.view = null;
            }
            top.view = createWebView();
            container.addView(top.view);
            loadUrl(top.view, top.url);
        }
        top.view.setVisibility(View.VISIBLE);
        top.view.onResume();
        webView = top.view;
        activeIndex = pages.size() - 1;
        trimLiveViews();
        updateToolbarState();
        updateAddressBar();
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
        if (webView == null || activeIndex < 0 || activeIndex >= pages.size()) {
            return;
        }
        String real = webView.getUrl();
        if (real != null && real.length() > 0) {
            pages.get(activeIndex).url = real;
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
            if (activeIndex > 0) {
                activeIndex--;
            }
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
        settings.setUserAgentString(UserAgentPrefs.resolveUserAgent(this));
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
                // 服务器重定向不算「新开一个页面」：否则页面栈里会塞进同一页的重定向地址，
                // 返回时就会在原地打转（表现为「按返回=刷新」）
                if (Build.VERSION.SDK_INT >= 24 && request.isRedirect()) {
                    return false;
                }
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
                if (v == webView) {
                    updateToolbarState();
                    updateAddressBar();
                }
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                PageZoom.apply(v, ZoomPrefs.get(MainActivity.this));
                if (v == webView) {
                    updateCurrentUrl();
                    updateToolbarState();
                    updateAddressBar();
                }
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
            if (appearance == AppearancePrefs.MODE_TOOLBAR) {
                // 顶栏模式：导航留在当前标签页里，形成前进 / 后退历史
                return false;
            }
            // 目标就是当前地址（例如脚本把自己重定向回自身）时不新开页面，
            // 否则返回键会在同一个页面上反复「刷新」
            String current = webView != null ? webView.getUrl() : null;
            if (current != null && current.equals(url)) {
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

    // ==================== 外观 / 顶栏 ====================

    private void setupTopBar() {
        topBar = findViewById(R.id.main_top_bar);
        topBarDivider = findViewById(R.id.main_top_bar_divider);
        navBack = (ImageButton) findViewById(R.id.nav_back);
        navForward = (ImageButton) findViewById(R.id.nav_forward);
        navRefresh = (ImageButton) findViewById(R.id.nav_refresh);
        navTabs = (ImageButton) findViewById(R.id.nav_tabs);
        navTabsBadge = (TextView) findViewById(R.id.nav_tabs_badge);
        navSettings = (ImageButton) findViewById(R.id.nav_settings);
        navAddress = (EditText) findViewById(R.id.nav_address);

        navBack.setOnClickListener(v -> {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
            }
        });
        navForward.setOnClickListener(v -> {
            if (webView != null && webView.canGoForward()) {
                webView.goForward();
            }
        });
        navRefresh.setOnClickListener(v -> {
            if (webView != null) {
                webView.reload();
            }
        });
        navTabs.setOnClickListener(v -> showTabsDialog());
        navSettings.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        navSettings.setOnLongClickListener(v -> {
            toggleVirtualMouse();
            return true;
        });
        navAddress.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                navAddress.selectAll();
            }
        });
        navAddress.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigateAddress();
                return true;
            }
            return false;
        });
    }

    private void applyAppearance() {
        boolean toolbar = appearance == AppearancePrefs.MODE_TOOLBAR;
        if (topBar != null) {
            topBar.setVisibility(toolbar ? View.VISIBLE : View.GONE);
        }
        if (topBarDivider != null) {
            topBarDivider.setVisibility(toolbar ? View.VISIBLE : View.GONE);
        }
        if (floatBall != null) {
            floatBall.setVisibility(toolbar ? View.GONE : View.VISIBLE);
        }
        updateToolbarState();
        updateAddressBar();
    }

    private void updateToolbarState() {
        if (appearance != AppearancePrefs.MODE_TOOLBAR) {
            return;
        }
        boolean canBack = webView != null && webView.canGoBack();
        boolean canForward = webView != null && webView.canGoForward();
        if (navBack != null) {
            navBack.setEnabled(canBack);
            navBack.setAlpha(canBack ? 1f : 0.3f);
        }
        if (navForward != null) {
            navForward.setEnabled(canForward);
            navForward.setAlpha(canForward ? 1f : 0.3f);
        }
        if (navTabsBadge != null) {
            int count = pages.size();
            navTabsBadge.setVisibility(count > 1 ? View.VISIBLE : View.GONE);
            navTabsBadge.setText(String.valueOf(count));
        }
    }

    private void updateAddressBar() {
        if (navAddress == null || appearance != AppearancePrefs.MODE_TOOLBAR) {
            return;
        }
        if (navAddress.hasFocus()) {
            return;
        }
        String url = webView != null ? webView.getUrl() : null;
        if (url == null && activeIndex >= 0 && activeIndex < pages.size()) {
            url = pages.get(activeIndex).url;
        }
        navAddress.setText(url == null ? "" : url);
    }

    private void navigateAddress() {
        if (navAddress == null || webView == null) {
            return;
        }
        String text = navAddress.getText().toString().trim();
        if (text.length() == 0) {
            return;
        }
        if (!text.contains("://")) {
            text = "https://" + text;
        }
        navAddress.clearFocus();
        hideKeyboard();
        if (activeIndex >= 0 && activeIndex < pages.size()) {
            pages.get(activeIndex).url = text;
        }
        loadUrl(webView, text);
    }

    private void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && navAddress != null) {
            imm.hideSoftInputFromWindow(navAddress.getWindowToken(), 0);
        }
    }

    private void switchTab(int index) {
        if (index < 0 || index >= pages.size() || index == activeIndex) {
            return;
        }
        updateCurrentUrl();
        Page from = pages.get(activeIndex);
        if (from.view != null) {
            from.view.setVisibility(View.GONE);
            from.view.onPause();
        }
        activeIndex = index;
        Page to = pages.get(index);
        if (to.view == null) {
            to.view = createWebView();
            container.addView(to.view);
            loadUrl(to.view, to.url);
        }
        to.view.setVisibility(View.VISIBLE);
        to.view.onResume();
        webView = to.view;
        trimLiveViews();
        updateToolbarState();
        updateAddressBar();
    }

    private void closeTab(int index) {
        if (index < 0 || index >= pages.size()) {
            return;
        }
        boolean wasActive = index == activeIndex;
        Page removed = pages.remove(index);
        if (removed.view != null) {
            removeAndDestroy(removed.view);
            removed.view = null;
        }
        if (pages.isEmpty()) {
            webView = null;
            activeIndex = -1;
            openNewPage(HOME_URL);
            return;
        }
        if (index < activeIndex) {
            activeIndex--;
        } else if (wasActive) {
            activeIndex = Math.min(index, pages.size() - 1);
            Page to = pages.get(activeIndex);
            if (to.view == null) {
                to.view = createWebView();
                container.addView(to.view);
                loadUrl(to.view, to.url);
            }
            to.view.setVisibility(View.VISIBLE);
            to.view.onResume();
            webView = to.view;
        }
        trimLiveViews();
        updateToolbarState();
        updateAddressBar();
    }

    private void showTabsDialog() {
        updateCurrentUrl();

        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(8);
        list.setPadding(pad, pad, pad, pad);
        for (int i = 0; i < pages.size(); i++) {
            list.addView(buildTabRow(i));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.tabs_title)
                .setView(scroll)
                .setNeutralButton(R.string.tabs_new, null)
                .setPositiveButton(R.string.about_close, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                openNewPage(HOME_URL);
            });
            for (int i = 0; i < list.getChildCount(); i++) {
                final int index = i;
                View row = list.getChildAt(i);
                row.setOnClickListener(v -> {
                    switchTab(index);
                    dialog.dismiss();
                });
                if (row instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) row;
                    if (group.getChildCount() > 1) {
                        group.getChildAt(1).setOnClickListener(v -> {
                            dialog.dismiss();
                            closeTab(index);
                        });
                    }
                }
            }
        });
        dialog.show();
    }

    private View buildTabRow(int index) {
        Page page = pages.get(index);
        String title = page.view != null ? page.view.getTitle() : null;
        if (title == null || title.length() == 0) {
            title = page.url;
        }
        if (title == null || title.length() == 0) {
            title = getString(R.string.app_name);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));

        TextView label = new TextView(this);
        label.setText((index == activeIndex ? "● " : "○ ") + title);
        label.setTextSize(15);
        label.setTextColor(index == activeIndex ? 0xFFFB7299 : 0xFF212121);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(16);
        close.setTextColor(0xFF888888);
        close.setPadding(dp(12), dp(8), dp(8), dp(8));
        row.addView(close);
        return row;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
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
        if (appearance == AppearancePrefs.MODE_TOOLBAR) {
            // 顶栏模式：系统返回只走标签页内的历史，走到底就退出应用
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            return false;
        }
        if (noMultiProcess) {
            // 不使用多进程 WebView：全屏方案下「返回」优先回到页面栈里的上一个页面。
            // 上一个页面的 WebView 已经被销毁，这里会重新创建并加载（即重新加载）。
            // 之前是「先 goBack() 再 reload()」，遇到站内重定向/历史改写时会在原地打转，
            // 看起来就像按返回只是刷新当前页，永远回不去。
            if (popPage()) {
                return true;
            }
            // 已经是最早的页面时，才退回网页自身的站内历史
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            return false;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return popPage();
    }
    private void destroyCachedViews() {
        for (Page p : pages) {
            if (p.view != null && p.view != webView) {
                removeAndDestroy(p.view);
                p.view = null;
            }
        }
    }
    private void setupVirtualMouse() {
        mouseView = new VirtualMouseView(this, () -> webView);
        // 浮层只盖住网页区域：顶栏（如果有）和悬浮球都不受影响
        contentFrame.addView(mouseView, new FrameLayout.LayoutParams(
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
        boolean mouseOn = MousePointerPrefs.isEnabled(this);

        // 顶部导航栏模式：设置按钮的图标也跟着虚拟鼠标状态切换（黑指针 / 齿轮）
        if (navSettings != null) {
            navSettings.setImageResource(mouseOn
                    ? R.drawable.ic_mouse_cursor
                    : R.drawable.ic_settings);
        }

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
        if (navSettings != null && navSettings.getVisibility() == View.VISIBLE) {
            navSettings.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
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
        if (AppearancePrefs.get(this) != appearance) {
            // 外观切换：重建界面（网页会回到当前地址）
            recreate();
            return;
        }
        OrientationPrefs.applyTo(this);
        boolean noMultiProcessNow = DebugPrefs.isNoMultiProcessWebView(this);
        if (noMultiProcessNow != noMultiProcess) {
            noMultiProcess = noMultiProcessNow;
            if (noMultiProcess) {
                destroyCachedViews();
            }
        }
        String userAgent = UserAgentPrefs.resolveUserAgent(this);
        if (!userAgent.equals(appliedUserAgent)) {
            appliedUserAgent = userAgent;
            for (Page p : pages) {
                if (p.view != null) {
                    p.view.getSettings().setUserAgentString(userAgent);
                }
            }
            if (webView != null) {
                webView.reload();
            }
        }
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
