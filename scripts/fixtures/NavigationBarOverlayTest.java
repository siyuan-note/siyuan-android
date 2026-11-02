package org.b3log.siyuan;

import android.os.Build;
import android.view.View;
import android.webkit.WebView;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

public final class NavigationBarOverlayTest {
    private static void check(boolean value) { if (!value) throw new AssertionError("Unexpected navigation overlay"); }

    public static void main(String[] args) {
        check(NavigationBarOverlay.bottom(true, 36, 45, 0, false) == 45);
        check(NavigationBarOverlay.bottom(false, 36, 45, 0, false) == 0);
        check(NavigationBarOverlay.bottom(true, 28, 45, 0, false) == 0);
        check(NavigationBarOverlay.bottom(true, 36, 144, 144, false) == 0);
        check(NavigationBarOverlay.bottom(true, 36, 45, 0, true) == 0);
        check(NavigationBarOverlay.bottom(true, 36, 0, 0, false) == 0);

        Build.VERSION.SDK_INT = 36;
        final NavigationBarOverlay overlay = new NavigationBarOverlay();
        final WebView webView = new WebView();
        final View root = new View();
        final WindowInsetsCompat gesture = new WindowInsetsCompat()
                .with(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 45))
                .with(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 127, 0, 0));
        overlay.apply(root, webView, gesture, false);
        check(root.getPaddingBottom() == 45 && webView.scripts.isEmpty());
        overlay.enable(webView, root);
        final java.util.function.Consumer<String> stale = webView.callback;
        overlay.reset(root);
        stale.accept("true");
        overlay.apply(root, webView, gesture, false);
        check(root.getPaddingBottom() == 45);
        overlay.enable(webView, root);
        webView.callback.accept("false");
        overlay.apply(root, webView, gesture, false);
        check(root.getPaddingBottom() == 45);
        overlay.enable(webView, root);
        webView.callback.accept("true");
        overlay.apply(root, webView, gesture, false);
        check(root.getPaddingBottom() == 0 && root.getPaddingTop() == 127);
        check(webView.scripts.get(webView.scripts.size() - 1).contains("45*innerWidth/1080"));
        final int count = webView.scripts.size();
        overlay.apply(root, webView, gesture, false);
        check(webView.scripts.size() == count);
        webView.width = 2400;
        overlay.apply(root, webView, gesture, false);
        check(webView.scripts.size() == count + 1);

        final WindowInsetsCompat keyboard = new WindowInsetsCompat()
                .with(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 45))
                .with(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 1000));
        keyboard.visible |= WindowInsetsCompat.Type.ime();
        overlay.apply(root, webView, keyboard, false);
        check(root.getPaddingBottom() == 45);
        check(webView.scripts.get(webView.scripts.size() - 1).contains("0*innerWidth"));
        overlay.apply(root, webView, keyboard, true);
        check(root.getPaddingBottom() == 1000);
        overlay.apply(root, webView, gesture, false);
        check(root.getPaddingBottom() == 0);
        overlay.apply(root, webView, new WindowInsetsCompat()
                .with(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 144))
                .with(WindowInsetsCompat.Type.tappableElement(), Insets.of(0, 0, 0, 144)), false);
        check(root.getPaddingBottom() == 144);
        overlay.apply(root, webView, new WindowInsetsCompat()
                .with(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 45))
                .with(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, 0, 0, 60)), false);
        check(root.getPaddingBottom() == 60);
        overlay.apply(root, webView, new WindowInsetsCompat()
                .with(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 48, 0)), false);
        check(root.getPaddingBottom() == 0 && root.getPaddingRight() == 48);
        overlay.reset(root);
        overlay.apply(root, webView, gesture, false);
        check(root.getPaddingBottom() == 45);
    }
}
