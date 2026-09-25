package com.xiaoyumi.biliweb;

import android.webkit.WebView;
final class PageZoom {

    private PageZoom() {
    }
    static void apply(WebView view, int zoomPercent) {
        if (view == null) {
            return;
        }
        view.evaluateJavascript(buildJs(zoomPercent), null);
    }

    static String buildJs(int zoomPercent) {
        if (zoomPercent == ZoomPrefs.DEFAULT_ZOOM) {
            return "(function(){var b=document.body;if(!b)return;"
                    + "b.style.zoom='';b.style.width='';})()";
        }
        return "(function(){var b=document.body;if(!b)return;"
                + "var z=" + zoomPercent + "/100;"
                + "b.style.zoom=z;"
                + "b.style.width=(100/z)+'%';})()";
    }
}
