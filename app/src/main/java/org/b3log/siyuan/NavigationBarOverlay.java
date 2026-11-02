package org.b3log.siyuan;

import android.os.Build;
import android.view.View;
import android.webkit.WebView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** 手势导航区域由支持安全区样式的页面绘制，其他页面使用原生避让。 */
final class NavigationBarOverlay {
    private boolean supported;
    private int generation;
    private int lastBottom = -1;
    private int lastWidth = -1;

    void reset(View root) {
        generation++;
        supported = false;
        lastBottom = lastWidth = -1;
        ViewCompat.requestApplyInsets(root);
    }

    void enable(WebView webView, View root) {
        final int current = generation;
        webView.evaluateJavascript("getComputedStyle(document.body).getPropertyValue('--android-navigation-overlay')"
                + ".trim()==='1'", result -> {
            if (generation != current || !"true".equals(result) || supported) return;
            supported = true;
            ViewCompat.requestApplyInsets(root);
        });
    }

    static int bottom(boolean supported, int sdk, int navigationBottom, int tappableBottom, boolean keyboard) {
        return supported && sdk >= 29 && tappableBottom == 0 && !keyboard ? navigationBottom : 0;
    }

    void apply(View root, WebView webView, WindowInsetsCompat insets, boolean includeKeyboard) {
        SystemBars.applyInsets(root, insets, Insets.NONE, includeKeyboard);
        final boolean canOverlay = supported
                && insets.getInsets(WindowInsetsCompat.Type.displayCutout()).bottom == 0;
        final int bottom = bottom(canOverlay, Build.VERSION.SDK_INT,
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
                insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom,
                insets.isVisible(WindowInsetsCompat.Type.ime()));
        if (bottom > 0) {
            root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(),
                    Math.max(0, root.getPaddingBottom() - bottom));
        }
        if (supported && webView.getWidth() > 0 && (bottom != lastBottom || webView.getWidth() != lastWidth)) {
            lastBottom = bottom;
            lastWidth = webView.getWidth();
            // 使用页面视口换算 CSS 像素，兼容屏幕密度、缩放和横竖屏切换。
            webView.evaluateJavascript("document.documentElement.style.setProperty('--android-navigation-bar-height',"
                    + "(" + bottom + "*innerWidth/" + lastWidth + ")+ 'px')", null);
        }
    }
}
