package com.xiaoyumi.biliweb;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
final class EdgeToEdge {

    private EdgeToEdge() {
    }

    static void apply(final Activity activity, final View root) {
        if (Build.VERSION.SDK_INT < 35) {
            return;
        }
        final int padLeft = root.getPaddingLeft();
        final int padTop = root.getPaddingTop();
        final int padRight = root.getPaddingRight();
        final int padBottom = root.getPaddingBottom();

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            v.setPadding(padLeft + bars.left, padTop + bars.top,
                    padRight + bars.right, padBottom + bars.bottom);
            return WindowInsets.CONSUMED;
        });

        WindowInsetsController controller = activity.getWindow().getInsetsController();
        if (controller != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(light, light);
        }
    }
}
