package com.xiaoyumi.biliweb;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
public class SettingsActivity extends Activity {
    private static final int COLOR_ROW_SELECTED = 0x4DFB7299;
    private static final int STEP = 5;
    private static final String STATE_PANEL = "settings_panel";
    private static final int NO_PANEL = 0;
    private static final int PANEL_ZOOM = 1;
    private static final int PANEL_ORIENTATION = 2;
    private static final int PANEL_MOUSE = 3;
    private static final int PANEL_FLOAT_BALL = 4;
    private static final int PANEL_ABOUT = 5;
    private static final int PANEL_LANGUAGE = 6;
    private static final int PANEL_DEBUG = 7;
    private static final int PANEL_APPEARANCE = 8;
    private boolean twoPane;
    private boolean detailShown;
    private String appliedLanguage;
    private int currentPanel = NO_PANEL;
    private TextView title;
    private View listPane;
    private FrameLayout detailPane;
    private TextView zoomSummary;
    private TextView orientationSummary;
    private TextView mouseSummary;
    private TextView floatBallSummary;
    private TextView aboutSummary;
    private TextView languageSummary;
    private TextView debugSummary;
    private TextView appearanceSummary;
    private EditText debugUaInput;
    private boolean uaReverting;
    private View rowZoom;
    private View rowOrientation;
    private View rowMouse;
    private View rowFloatBall;
    private View rowAbout;
    private View rowLanguage;
    private View rowDebug;
    private View rowAppearance;
    private TextView aboutStatus;
    private Button aboutCheckButton;
    private WebView preview;
    private SeekBar zoomSeek;
    private TextView zoomPercent;
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OrientationPrefs.applyTo(this);
        setContentView(R.layout.activity_settings);

        EdgeToEdge.apply(this, findViewById(R.id.settings_root));

        appliedLanguage = AppLanguage.get(this);
        twoPane = getResources().getBoolean(R.bool.two_pane);
        title = (TextView) findViewById(R.id.top_title);
        listPane = findViewById(R.id.list_pane);
        detailPane = (FrameLayout) findViewById(R.id.detail_pane);
        zoomSummary = (TextView) findViewById(R.id.zoom_summary);
        orientationSummary = (TextView) findViewById(R.id.orientation_summary);
        mouseSummary = (TextView) findViewById(R.id.mouse_summary);
        floatBallSummary = (TextView) findViewById(R.id.float_ball_summary);
        aboutSummary = (TextView) findViewById(R.id.about_summary);
        languageSummary = (TextView) findViewById(R.id.language_summary);
        debugSummary = (TextView) findViewById(R.id.debug_summary);
        appearanceSummary = (TextView) findViewById(R.id.appearance_summary);

        rowZoom = findViewById(R.id.row_zoom);
        rowOrientation = findViewById(R.id.row_orientation);
        rowMouse = findViewById(R.id.row_mouse);
        rowFloatBall = findViewById(R.id.row_float_ball);
        rowAbout = findViewById(R.id.row_about);
        rowLanguage = findViewById(R.id.row_language);
        rowDebug = findViewById(R.id.row_debug);
        rowAppearance = findViewById(R.id.row_appearance);
        findViewById(R.id.btn_back).setOnClickListener(v -> onBackArrow());
        rowZoom.setOnClickListener(v -> openPanel(PANEL_ZOOM));
        rowOrientation.setOnClickListener(v -> openPanel(PANEL_ORIENTATION));
        rowMouse.setOnClickListener(v -> openPanel(PANEL_MOUSE));
        rowFloatBall.setOnClickListener(v -> openPanel(PANEL_FLOAT_BALL));
        rowAbout.setOnClickListener(v -> openPanel(PANEL_ABOUT));
        rowLanguage.setOnClickListener(v -> openPanel(PANEL_LANGUAGE));
        rowDebug.setOnClickListener(v -> openPanel(PANEL_DEBUG));
        rowAppearance.setOnClickListener(v -> openPanel(PANEL_APPEARANCE));

        registerBackCallback();

        int panel = (savedInstanceState != null)
                ? savedInstanceState.getInt(STATE_PANEL, NO_PANEL)
                : NO_PANEL;
        if (twoPane && panel == NO_PANEL) {
            panel = PANEL_ZOOM;
        }
        openPanel(panel);
        updateTitle();
    }
    private void openPanel(int panel) {
        if (panel == currentPanel) {
            updateSummaries();
            return;
        }
        clearPanel();

        if (panel == NO_PANEL) {
            detailShown = false;
            detailPane.setVisibility(View.GONE);
            listPane.setVisibility(View.VISIBLE);
            currentPanel = NO_PANEL;
            updateTitle();
            return;
        }

        View content;
        if (panel == PANEL_ZOOM) {
            content = createZoomPanel();
        } else if (panel == PANEL_MOUSE) {
            content = createMousePanel();
        } else if (panel == PANEL_FLOAT_BALL) {
            content = createFloatBallPanel();
        } else if (panel == PANEL_ABOUT) {
            content = createAboutPanel();
        } else if (panel == PANEL_LANGUAGE) {
            content = createLanguagePanel();
        } else if (panel == PANEL_DEBUG) {
            content = createDebugPanel();
        } else if (panel == PANEL_APPEARANCE) {
            content = createAppearancePanel();
        } else {
            content = createOrientationPanel();
        }
        detailPane.removeAllViews();
        detailPane.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        detailPane.setVisibility(View.VISIBLE);
        if (!twoPane) {
            listPane.setVisibility(View.GONE);
        }
        detailShown = true;
        currentPanel = panel;
        updateTitle();
    }

    private void clearPanel() {
        if (debugUaInput != null) {
            commitUaInput();
            debugUaInput = null;
        }
        if (preview != null) {
            preview.destroy();
            preview = null;
        }
        zoomSeek = null;
        zoomPercent = null;
        aboutStatus = null;
        aboutCheckButton = null;
        if (detailPane != null) {
            detailPane.removeAllViews();
        }
    }

    private void updateTitle() {
        if (twoPane || currentPanel == NO_PANEL) {
            title.setText(R.string.settings_title);
        } else if (currentPanel == PANEL_ZOOM) {
            title.setText(R.string.settings_zoom_title);
        } else if (currentPanel == PANEL_MOUSE) {
            title.setText(R.string.settings_mouse_title);
        } else if (currentPanel == PANEL_FLOAT_BALL) {
            title.setText(R.string.settings_float_ball_title);
        } else if (currentPanel == PANEL_ABOUT) {
            title.setText(R.string.settings_about_title);
        } else if (currentPanel == PANEL_LANGUAGE) {
            title.setText(R.string.settings_language_title);
        } else if (currentPanel == PANEL_DEBUG) {
            title.setText(R.string.settings_debug_title);
        } else if (currentPanel == PANEL_APPEARANCE) {
            title.setText(R.string.settings_appearance_title);
        } else {
            title.setText(R.string.settings_orientation_title);
        }
        updateSummaries();
        updateRowHighlight();
    }

    private void updateRowHighlight() {
        if (rowZoom == null || rowOrientation == null) {
            return;
        }
        applyRowBackground(rowZoom, twoPane && currentPanel == PANEL_ZOOM);
        applyRowBackground(rowOrientation, twoPane && currentPanel == PANEL_ORIENTATION);
        applyRowBackground(rowMouse, twoPane && currentPanel == PANEL_MOUSE);
        applyRowBackground(rowFloatBall, twoPane && currentPanel == PANEL_FLOAT_BALL);
        applyRowBackground(rowAbout, twoPane && currentPanel == PANEL_ABOUT);
        applyRowBackground(rowLanguage, twoPane && currentPanel == PANEL_LANGUAGE);
        applyRowBackground(rowDebug, twoPane && currentPanel == PANEL_DEBUG);
        applyRowBackground(rowAppearance, twoPane && currentPanel == PANEL_APPEARANCE);
    }

    private void applyRowBackground(View row, boolean active) {
        if (row == null) {
            return;
        }
        int rippleId = themeRippleId();
        if (!active) {
            row.setBackgroundResource(rippleId);
            return;
        }

        Drawable pink = new ColorDrawable(COLOR_ROW_SELECTED);
        if (rippleId != 0) {
            try {
                Drawable ripple = getDrawable(rippleId);
                if (ripple != null) {
                    row.setBackground(new LayerDrawable(new Drawable[]{pink, ripple}));
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        row.setBackground(pink);
    }
    private int themeRippleId() {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }
    private void updateSummaries() {
        if (zoomSummary == null || orientationSummary == null) {
            return;
        }
        int zoom = ZoomPrefs.get(this);
        zoomSummary.setText(zoom == ZoomPrefs.DEFAULT_ZOOM
                ? getString(R.string.zoom_percent_default, zoom)
                : getString(R.string.zoom_percent, zoom));
        orientationSummary.setText(OrientationPrefs.get(this) == OrientationPrefs.MODE_LANDSCAPE
                ? R.string.orientation_landscape_short
                : R.string.orientation_default_short);
        if (mouseSummary != null) {
            mouseSummary.setText(MousePointerPrefs.isEnabled(this)
                    ? R.string.mouse_on_short
                    : R.string.mouse_off_short);
        }
        if (floatBallSummary != null) {
            floatBallSummary.setText(getString(R.string.float_ball_summary,
                    FloatBallPrefs.getSize(this), FloatBallPrefs.getOpacity(this)));
        }
        if (aboutSummary != null) {
            aboutSummary.setText(UpdateChecker.currentVersion(this));
        }
        if (languageSummary != null) {
            languageSummary.setText(AppLanguage.displayName(this));
        }
        if (debugSummary != null) {
            debugSummary.setText(uaSummaryRes());
        }
        if (appearanceSummary != null) {
            appearanceSummary.setText(AppearancePrefs.get(this) == AppearancePrefs.MODE_TOOLBAR
                    ? R.string.appearance_toolbar_short
                    : R.string.appearance_fullscreen_short);
        }
    }
    private View createAppearancePanel() {
        View panel = getLayoutInflater().inflate(R.layout.panel_appearance, detailPane, false);
        RadioButton fullscreen = (RadioButton) panel.findViewById(R.id.appearance_fullscreen);
        RadioButton toolbar = (RadioButton) panel.findViewById(R.id.appearance_toolbar);
        RadioGroup group = (RadioGroup) panel.findViewById(R.id.appearance_group);

        final int mode = AppearancePrefs.get(this);
        fullscreen.setChecked(mode == AppearancePrefs.MODE_FULLSCREEN);
        toolbar.setChecked(mode == AppearancePrefs.MODE_TOOLBAR);

        group.setOnCheckedChangeListener((checkedGroup, checkedId) -> {
            AppearancePrefs.set(this, checkedId == R.id.appearance_toolbar
                    ? AppearancePrefs.MODE_TOOLBAR
                    : AppearancePrefs.MODE_FULLSCREEN);
            updateSummaries();
        });
        return panel;
    }
    @SuppressLint("SetJavaScriptEnabled")
    private View createZoomPanel() {
        View panel = getLayoutInflater().inflate(R.layout.panel_zoom, detailPane, false);
        zoomPercent = (TextView) panel.findViewById(R.id.zoom_percent);
        zoomSeek = (SeekBar) panel.findViewById(R.id.zoom_seek);
        preview = (WebView) panel.findViewById(R.id.preview);

        final int zoom = ZoomPrefs.get(this);
        preview.getSettings().setJavaScriptEnabled(true);
        preview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                PageZoom.apply(v, ZoomPrefs.get(SettingsActivity.this));
            }
        });
        preview.loadDataWithBaseURL(null, readPreviewHtml(), "text/html", "UTF-8", null);


        zoomSeek.setMax(ZoomPrefs.MAX_ZOOM - ZoomPrefs.MIN_ZOOM);
        zoomSeek.setProgress(zoom - ZoomPrefs.MIN_ZOOM);
        zoomSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                applyZoom(ZoomPrefs.MIN_ZOOM + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        panel.findViewById(R.id.zoom_minus).setOnClickListener(v -> stepZoom(-STEP));
        panel.findViewById(R.id.zoom_plus).setOnClickListener(v -> stepZoom(STEP));

        updateZoomText(zoom);
        return panel;
    }
    private void stepZoom(int delta) {
        int next = ZoomPrefs.get(this) + delta;
        if (next < ZoomPrefs.MIN_ZOOM) {
            next = ZoomPrefs.MIN_ZOOM;
        } else if (next > ZoomPrefs.MAX_ZOOM) {
            next = ZoomPrefs.MAX_ZOOM;
        }
        zoomSeek.setProgress(next - ZoomPrefs.MIN_ZOOM);
    }
    private void applyZoom(int zoom) {
        if (zoom < ZoomPrefs.MIN_ZOOM) {
            zoom = ZoomPrefs.MIN_ZOOM;
        } else if (zoom > ZoomPrefs.MAX_ZOOM) {
            zoom = ZoomPrefs.MAX_ZOOM;
        }
        ZoomPrefs.set(this, zoom);
        PageZoom.apply(preview, zoom);
        updateZoomText(zoom);
        updateSummaries();
    }

    private void updateZoomText(int zoom) {
        if (zoomPercent == null) {
            return;
        }
        zoomPercent.setText(zoom == ZoomPrefs.DEFAULT_ZOOM
                ? getString(R.string.zoom_percent_default, zoom)
                : getString(R.string.zoom_percent, zoom));
    }
    private String readPreviewHtml() {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getResources().openRawResource(R.raw.preview), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            return "<html><body><p>" + getString(R.string.settings_preview_error) + "</p></body></html>";
        }
        return sb.toString();
    }
    private View createOrientationPanel() {
        View panel = getLayoutInflater().inflate(R.layout.panel_orientation, detailPane, false);
        RadioButton optionDefault = (RadioButton) panel.findViewById(R.id.orientation_default);
        RadioButton optionLandscape = (RadioButton) panel.findViewById(R.id.orientation_landscape);
        RadioGroup group = (RadioGroup) panel.findViewById(R.id.orientation_group);

        final int mode = OrientationPrefs.get(this);
        optionDefault.setChecked(mode == OrientationPrefs.MODE_DEFAULT);
        optionLandscape.setChecked(mode == OrientationPrefs.MODE_LANDSCAPE);

        group.setOnCheckedChangeListener((checkedGroup, checkedId) -> {
            int next = (checkedId == R.id.orientation_landscape)
                    ? OrientationPrefs.MODE_LANDSCAPE
                    : OrientationPrefs.MODE_DEFAULT;
            OrientationPrefs.set(this, next);
            OrientationPrefs.applyTo(this);
            updateSummaries();
        });
        return panel;
    }
    private View createMousePanel() {
        View panel = getLayoutInflater().inflate(R.layout.panel_mouse, detailPane, false);
        RadioButton optionOff = (RadioButton) panel.findViewById(R.id.mouse_off);
        RadioButton optionOn = (RadioButton) panel.findViewById(R.id.mouse_on);
        RadioGroup group = (RadioGroup) panel.findViewById(R.id.mouse_group);

        final boolean enabled = MousePointerPrefs.isEnabled(this);
        optionOff.setChecked(!enabled);
        optionOn.setChecked(enabled);

        group.setOnCheckedChangeListener((checkedGroup, checkedId) -> {
            MousePointerPrefs.setEnabled(this, checkedId == R.id.mouse_on);
            updateSummaries();
        });
        CheckBox quickToggle = (CheckBox) panel.findViewById(R.id.mouse_quick_toggle);
        quickToggle.setChecked(MousePointerPrefs.isQuickToggleEnabled(this));
        quickToggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                MousePointerPrefs.setQuickToggleEnabled(this, isChecked));
        final SeekBar sizeSeek = (SeekBar) panel.findViewById(R.id.mouse_size_seek);
        final TextView sizeText = (TextView) panel.findViewById(R.id.mouse_size_percent);
        int cursorScale = MousePointerPrefs.getCursorScale(this);
        sizeSeek.setMax(MousePointerPrefs.MAX_CURSOR_SCALE - MousePointerPrefs.MIN_CURSOR_SCALE);
        sizeSeek.setProgress(cursorScale - MousePointerPrefs.MIN_CURSOR_SCALE);
        updateCursorSizeText(sizeText, cursorScale);
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = MousePointerPrefs.MIN_CURSOR_SCALE + progress;
                MousePointerPrefs.setCursorScale(SettingsActivity.this, percent);
                updateCursorSizeText(sizeText, percent);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        return panel;
    }

    private void updateCursorSizeText(TextView view, int percent) {
        if (view == null) {
            return;
        }
        view.setText(percent == MousePointerPrefs.DEFAULT_CURSOR_SCALE
                ? getString(R.string.cursor_size_default, percent)
                : getString(R.string.cursor_size_percent, percent));
    }
    private View createFloatBallPanel() {
        View panel = getLayoutInflater().inflate(R.layout.panel_float_ball, detailPane, false);
        final TextView sizeText = (TextView) panel.findViewById(R.id.ball_size_percent);
        final SeekBar sizeSeek = (SeekBar) panel.findViewById(R.id.ball_size_seek);
        int size = FloatBallPrefs.getSize(this);
        sizeSeek.setMax(FloatBallPrefs.MAX_SIZE - FloatBallPrefs.MIN_SIZE);
        sizeSeek.setProgress(size - FloatBallPrefs.MIN_SIZE);
        updatePercentText(sizeText, size, FloatBallPrefs.DEFAULT_SIZE);
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = FloatBallPrefs.MIN_SIZE + progress;
                FloatBallPrefs.setSize(SettingsActivity.this, percent);
                updatePercentText(sizeText, percent, FloatBallPrefs.DEFAULT_SIZE);
                updateSummaries();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        final TextView opacityText = (TextView) panel.findViewById(R.id.ball_opacity_percent);
        final SeekBar opacitySeek = (SeekBar) panel.findViewById(R.id.ball_opacity_seek);
        int opacity = FloatBallPrefs.getOpacity(this);
        opacitySeek.setMax(FloatBallPrefs.MAX_OPACITY - FloatBallPrefs.MIN_OPACITY);
        opacitySeek.setProgress(opacity - FloatBallPrefs.MIN_OPACITY);
        updatePercentText(opacityText, opacity, FloatBallPrefs.DEFAULT_OPACITY);
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = FloatBallPrefs.MIN_OPACITY + progress;
                FloatBallPrefs.setOpacity(SettingsActivity.this, percent);
                updatePercentText(opacityText, percent, FloatBallPrefs.DEFAULT_OPACITY);
                updateSummaries();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        return panel;
    }
    private void updatePercentText(TextView view, int percent, int defaultPercent) {
        if (view == null) {
            return;
        }
        view.setText(percent == defaultPercent
                ? getString(R.string.float_ball_percent_default, percent)
                : getString(R.string.float_ball_percent, percent));
    }
    private View createLanguagePanel() {
        View panel = getLayoutInflater().inflate(R.layout.panel_language, detailPane, false);
        RadioButton optionAuto = (RadioButton) panel.findViewById(R.id.language_auto);
        RadioButton optionZh = (RadioButton) panel.findViewById(R.id.language_zh);
        RadioButton optionEn = (RadioButton) panel.findViewById(R.id.language_en);
        RadioGroup group = (RadioGroup) panel.findViewById(R.id.language_group);

        final String current = AppLanguage.get(this);
        optionAuto.setChecked(AppLanguage.AUTO.equals(current));
        optionZh.setChecked(AppLanguage.CHINESE.equals(current));
        optionEn.setChecked(AppLanguage.ENGLISH.equals(current));

        group.setOnCheckedChangeListener((checkedGroup, checkedId) -> {
            String next;
            if (checkedId == R.id.language_zh) {
                next = AppLanguage.CHINESE;
            } else if (checkedId == R.id.language_en) {
                next = AppLanguage.ENGLISH;
            } else {
                next = AppLanguage.AUTO;
            }
            if (next.equals(AppLanguage.get(this))) {
                return;
            }
            AppLanguage.set(this, next);
            recreate();
        });
        return panel;
    }
    private View createDebugPanel() {
        View panel = getLayoutInflater().inflate(R.layout.panel_debug, detailPane, false);

        CheckBox noMultiProcess = (CheckBox) panel.findViewById(R.id.debug_no_multiprocess);
        noMultiProcess.setChecked(DebugPrefs.isNoMultiProcessWebView(this));
        noMultiProcess.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DebugPrefs.setNoMultiProcessWebView(this, isChecked);
            updateSummaries();
        });

        panel.findViewById(R.id.debug_webview_test).setOnClickListener(v ->
                startActivity(new Intent(SettingsActivity.this, WebViewCheckActivity.class)));

        final RadioButton desktop = (RadioButton) panel.findViewById(R.id.debug_ua_desktop);
        final RadioButton mobile = (RadioButton) panel.findViewById(R.id.debug_ua_mobile);
        RadioGroup uaGroup = (RadioGroup) panel.findViewById(R.id.debug_ua_group);
        int mode = UserAgentPrefs.getMode(this);
        desktop.setChecked(mode != UserAgentPrefs.MODE_MOBILE);
        mobile.setChecked(mode == UserAgentPrefs.MODE_MOBILE);
        uaGroup.setOnCheckedChangeListener((group, checkedId) -> {
            UserAgentPrefs.setMode(this, checkedId == R.id.debug_ua_mobile
                    ? UserAgentPrefs.MODE_MOBILE
                    : UserAgentPrefs.MODE_DESKTOP);
            updateSummaries();
        });

        final CheckBox custom = (CheckBox) panel.findViewById(R.id.debug_ua_custom);
        final EditText input = (EditText) panel.findViewById(R.id.debug_ua_input);
        debugUaInput = input;
        boolean customOn = UserAgentPrefs.isCustomEnabled(this);
        custom.setChecked(customOn);
        input.setText(UserAgentPrefs.getCustomText(this));
        input.setEnabled(customOn);
        // 失焦 / 回车时才校验并保存：避免每敲一个字就写一次，也避免写到一半被当成最终值
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                commitUaInput();
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitUaInput();
                return true;
            }
            return false;
        });
        custom.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (uaReverting) {
                return;
            }
            if (isChecked) {
                // 打开「自定义」之前先确认输入合法，否则拒绝打开，脏配置不入库
                String text = input.getText().toString().trim();
                int code = UserAgentPrefs.checkUserAgent(text);
                if (text.length() > 0 && code != UserAgentPrefs.UA_OK) {
                    purgeInvalidStoredUa();
                    showUaError(code, input);
                    uaReverting = true;
                    custom.setChecked(false);
                    uaReverting = false;
                    return;
                }
                UserAgentPrefs.setCustomText(this, text);
                UserAgentPrefs.setCustomEnabled(this, true);
                input.setEnabled(true);
            } else {
                commitUaInput();
                UserAgentPrefs.setCustomEnabled(this, false);
                input.setEnabled(false);
            }
            updateSummaries();
        });
        return panel;
    }

    /** 校验并保存自定义 UA：不合法就不写入存储，并给出提示。 */
    private void commitUaInput() {
        if (debugUaInput == null) {
            return;
        }
        String text = debugUaInput.getText().toString().trim();
        if (text.length() == 0) {
            debugUaInput.setError(null);
            UserAgentPrefs.setCustomText(this, "");
            updateSummaries();
            return;
        }
        int code = UserAgentPrefs.checkUserAgent(text);
        if (code == UserAgentPrefs.UA_OK) {
            debugUaInput.setError(null);
            UserAgentPrefs.setCustomText(this, text);
            updateSummaries();
        } else {
            purgeInvalidStoredUa();
            showUaError(code, debugUaInput);
        }
    }

    /** 如果存储里已有的自定义 UA 本身就不合法，直接清掉，保证脏配置不残留。 */
    private void purgeInvalidStoredUa() {
        String stored = UserAgentPrefs.getCustomText(this);
        if (stored.trim().length() > 0
                && UserAgentPrefs.checkUserAgent(stored) != UserAgentPrefs.UA_OK) {
            UserAgentPrefs.setCustomText(this, "");
        }
    }

    private void showUaError(int code, EditText input) {
        String reason;
        if (code == UserAgentPrefs.UA_BAD_CHARS) {
            reason = getString(R.string.debug_ua_error_bad_chars);
        } else {
            reason = getString(R.string.debug_ua_error_not_ua);
        }
        if (input != null) {
            input.setError(reason);
        }
        Toast.makeText(this, getString(R.string.debug_ua_invalid, reason), Toast.LENGTH_LONG).show();
    }

    /** 设置列表里「调试」一行右侧显示的内容：当前生效的 UA 类型。 */
    private int uaSummaryRes() {
        if (UserAgentPrefs.isCustomEnabled(this)
                && UserAgentPrefs.checkUserAgent(UserAgentPrefs.getCustomText(this))
                        == UserAgentPrefs.UA_OK) {
            return R.string.debug_ua_custom_short;
        }
        return UserAgentPrefs.getMode(this) == UserAgentPrefs.MODE_MOBILE
                ? R.string.debug_ua_mobile_short
                : R.string.debug_ua_desktop_short;
    }
    private View createAboutPanel() {
        View panel = getLayoutInflater().inflate(R.layout.panel_about, detailPane, false);
        TextView version = (TextView) panel.findViewById(R.id.about_version);
        version.setText(getString(R.string.about_version, UpdateChecker.currentVersion(this)));
        TextView github = (TextView) panel.findViewById(R.id.about_github);
        github.setOnClickListener(v -> openInBrowser(getString(R.string.about_github_url)));

        aboutStatus = (TextView) panel.findViewById(R.id.about_update_status);
        aboutCheckButton = (Button) panel.findViewById(R.id.about_check_update);
        aboutCheckButton.setOnClickListener(v -> checkUpdate());
        return panel;
    }
    private void checkUpdate() {
        if (aboutCheckButton != null) {
            aboutCheckButton.setEnabled(false);
        }
        setAboutStatus(R.string.about_checking);

        UpdateChecker.check(this, result -> {
            if (isFinishing()) {
                return;
            }
            if (aboutCheckButton != null) {
                aboutCheckButton.setEnabled(true);
            }
            if (result.failed) {
                if (aboutStatus != null) {
                    aboutStatus.setText(getString(R.string.about_check_failed_reason,
                            result.failReason == null || result.failReason.length() == 0
                                    ? getString(R.string.about_check_failed_unknown)
                                    : result.failReason));
                }
            } else if (result.upToDate) {
                setAboutStatus(R.string.about_up_to_date);
            } else {
                setAboutStatus(0);
                showUpdateDialog(result);
            }
        });
    }

    private void setAboutStatus(int stringRes) {
        if (aboutStatus == null) {
            return;
        }
        aboutStatus.setText(stringRes == 0 ? "" : getString(stringRes));
    }
    private void showUpdateDialog(final UpdateChecker.Result info) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.update_title)
                .setMessage(getString(R.string.update_message,
                        info.latestVersion, UpdateChecker.currentVersion(this)))
                .setPositiveButton(R.string.update_download,
                        (dialog, which) -> openInBrowser(info.downloadUrl))
                .setNeutralButton(R.string.update_changelog,
                        (dialog, which) -> showChangelog(info.changelogUrl))
                .setNegativeButton(R.string.update_later, null)
                .show();
    }
    private void openInBrowser(String url) {
        if (url == null || url.length() == 0) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.about_no_browser, Toast.LENGTH_SHORT).show();
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    private void showChangelog(String url) {
        if (url == null || url.length() == 0) {
            return;
        }
        final WebView web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl(url);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.update_changelog_title)
                .setView(web)
                .setPositiveButton(R.string.about_close, null)
                .create();
        dialog.setOnDismissListener(d -> {
            if (web.getParent() instanceof ViewGroup) {
                ((ViewGroup) web.getParent()).removeView(web);
            }
            web.stopLoading();
            web.setWebViewClient(null);
            web.destroy();
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.7f);
            dialog.getWindow().setAttributes(lp);
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (!AppLanguage.get(this).equals(appliedLanguage)) {
            recreate();
        }
    }
    private void onBackArrow() {
        if (!closeDetail()) {
            finish();
        }
    }
    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::onBackInvoked);
        }
    }
    private void onBackInvoked() {
        if (!closeDetail()) {
            finish();
        }
    }
    @Override
@SuppressLint("GestureBackNavigation")
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && closeDetail()) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    private boolean closeDetail() {
        if (!twoPane && detailShown) {
            openPanel(NO_PANEL);
            return true;
        }
        return false;
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PANEL, currentPanel);
    }
    @Override
    protected void onPause() {
        commitUaInput();
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        clearPanel();
        super.onDestroy();
    }
}
