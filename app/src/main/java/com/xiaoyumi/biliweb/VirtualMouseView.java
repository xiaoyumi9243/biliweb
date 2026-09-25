package com.xiaoyumi.biliweb;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebView;
class VirtualMouseView extends View {
    private static final float SENSITIVITY = 1.0f;
    private static final float SCROLL_SCALE = 0.01f;
    interface Target {
        WebView current();
    }

    private final Target target;
    private final Bitmap cursor;
    private final int slop;

    private float cursorX;
    private float cursorY;
    private boolean positioned;
    private int hotspotX;
    private int hotspotY;
    private float cursorScale = 1f;
    private final RectF cursorDst = new RectF();
    private final Paint cursorPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private float midX;
    private float midY;
    private boolean dragged;
    private boolean multiTouch;
    private boolean multiMoved;
    private WebView touchWeb;
    private boolean touchPending;


    VirtualMouseView(Context context, Target target) {
        super(context);
        this.target = target;
        this.slop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.cursor = BitmapFactory.decodeResource(getResources(), R.drawable.ic_mouse_cursor);
        findHotspot();
        setClickable(false);
        setFocusable(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!positioned && w > 0 && h > 0) {
            cursorX = w / 2f;
            cursorY = h / 2f;
            positioned = true;
        }
        clampCursor();
    }
    void setCursorScalePercent(int percent) {
        float scale = percent / 100f;
        if (scale < 0.1f) {
            scale = 0.1f;
        }
        if (Math.abs(scale - cursorScale) < 0.001f) {
            return;
        }
        cursorScale = scale;
        clampCursor();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cursor != null) {
            float left = cursorX - hotspotX * cursorScale;
            float top = cursorY - hotspotY * cursorScale;
            cursorDst.set(left, top,
                    left + cursor.getWidth() * cursorScale,
                    top + cursor.getHeight() * cursorScale);
            canvas.drawBitmap(cursor, null, cursorDst, cursorPaint);
        }
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                dragged = false;
                multiTouch = false;
                multiMoved = false;
                touchPending = sendTouchDown();
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                multiTouch = true;
                midX = pointerMidX(event);
                midY = pointerMidY(event);
                cancelPendingTouch();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() >= 2) {
                    multiTouch = true;
                    float mx = pointerMidX(event);
                    float my = pointerMidY(event);
                    float dx = mx - midX;
                    float dy = my - midY;
                    midX = mx;
                    midY = my;
                    if (Math.abs(dx) > slop / 2f || Math.abs(dy) > slop / 2f) {
                        multiMoved = true;
                    }
                    scroll(dx, dy);
                } else {
                    float x = event.getX();
                    float y = event.getY();
                    if (!dragged && (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop)) {
                        dragged = true;
                        cancelPendingTouch();
                    }
                    if (dragged) {
                        cursorX += (x - lastX) * SENSITIVITY;
                        cursorY += (y - lastY) * SENSITIVITY;
                        clampCursor();
                        invalidate();
                        hover();
                    }
                    lastX = x;
                    lastY = y;
                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                multiTouch = true;
                int remain = (event.getActionIndex() == 0) ? 1 : 0;
                if (event.getPointerCount() > remain) {
                    lastX = event.getX(remain);
                    lastY = event.getY(remain);
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!dragged && touchPending) {
                    sendTouchUp();
                }
                touchPending = false;
                touchWeb = null;
                return true;

            case MotionEvent.ACTION_CANCEL:
                cancelPendingTouch();
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }
    private void clampCursor() {
        float maxX = Math.max(0f, getWidth() - 1f);
        float maxY = Math.max(0f, getHeight() - 1f);
        cursorX = Math.max(0f, Math.min(cursorX, maxX));
        cursorY = Math.max(0f, Math.min(cursorY, maxY));
    }

    private void findHotspot() {
        if (cursor == null) {
            return;
        }
        for (int y = 0; y < cursor.getHeight(); y++) {
            for (int x = 0; x < cursor.getWidth(); x++) {
                if (Color.alpha(cursor.getPixel(x, y)) > 0) {
                    hotspotX = x;
                    hotspotY = y;
                    return;
                }
            }
        }
    }

    private float pointerMidX(MotionEvent event) {
        return (event.getX(0) + event.getX(1)) / 2f;
    }

    private float pointerMidY(MotionEvent event) {
        return (event.getY(0) + event.getY(1)) / 2f;
    }
    private boolean sendTouchDown() {
        WebView web = currentWeb();
        if (web == null) {
            return false;
        }
        touchWeb = web;
        sendTouch(web, MotionEvent.ACTION_DOWN, pointerInWeb(web));
        return true;
    }

    private void sendTouchUp() {
        if (touchWeb != null) {
            sendTouch(touchWeb, MotionEvent.ACTION_UP, pointerInWeb(touchWeb));
        }
    }
    private void cancelPendingTouch() {
        if (touchPending && touchWeb != null) {
            sendTouch(touchWeb, MotionEvent.ACTION_CANCEL, pointerInWeb(touchWeb));
        }
        touchPending = false;
        touchWeb = null;
    }

    private void sendTouch(WebView web, int action, float[] point) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, point[0], point[1], 0);
        try {
            web.dispatchTouchEvent(event);
        } catch (Exception ignored) {
        } finally {
            event.recycle();
        }
    }
    private static boolean canUseMouseEvents() {
        return Build.VERSION.SDK_INT >= 29;
    }
    private void scroll(float dx, float dy) {
        WebView web = currentWeb();
        if (web == null) {
            return;
        }
        if (canUseMouseEvents()) {
            sendMouse(web, MotionEvent.ACTION_SCROLL, pointerInWeb(web), true,
                    -dy * SCROLL_SCALE, -dx * SCROLL_SCALE);
        } else {
            web.scrollBy(Math.round(-dx), Math.round(-dy));
        }
    }
    private void hover() {
        WebView web = canUseMouseEvents() ? currentWeb() : null;
        if (web != null) {
            sendMouse(web, MotionEvent.ACTION_HOVER_MOVE, pointerInWeb(web), true, 0f, 0f);
        }
    }

    private WebView currentWeb() {
        if (target == null || !isAttachedToWindow() || getWidth() == 0) {
            return null;
        }
        return target.current();
    }
    private float[] pointerInWeb(WebView web) {
        int[] mine = new int[2];
        getLocationOnScreen(mine);
        int[] webPos = new int[2];
        web.getLocationOnScreen(webPos);
        return new float[]{mine[0] + cursorX - webPos[0], mine[1] + cursorY - webPos[1]};
    }
    private void sendMouse(WebView web, int action, float[] point,
                           boolean generic, float scrollV, float scrollH) {
        if (Build.VERSION.SDK_INT < 29) {
            return;
        }

        MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
        props.id = 0;
        props.toolType = MotionEvent.TOOL_TYPE_MOUSE;

        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = point[0];
        coords.y = point[1];
        coords.pressure = 1f;
        coords.size = 1f;
        if (scrollV != 0f || scrollH != 0f) {
            coords.setAxisValue(MotionEvent.AXIS_VSCROLL, scrollV);
            coords.setAxisValue(MotionEvent.AXIS_HSCROLL, scrollH);
        }

        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, 1,
                new MotionEvent.PointerProperties[]{props},
                new MotionEvent.PointerCoords[]{coords},
                0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_MOUSE, Display.DEFAULT_DISPLAY);
        try {
            if (generic) {
                web.dispatchGenericMotionEvent(event);
            } else {
                web.dispatchTouchEvent(event);
            }
        } catch (Exception ignored) {
        } finally {
            event.recycle();
        }
    }
}
