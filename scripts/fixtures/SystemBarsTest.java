package org.b3log.siyuan;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Runs against the small window/view doubles in scripts/systemBars.test.cjs. */
public final class SystemBarsTest {
    public static void main(String[] args) {
        final int bars = WindowInsetsCompat.Type.systemBars();
        final int cutout = WindowInsetsCompat.Type.displayCutout();
        final int ime = WindowInsetsCompat.Type.ime();
        final Activity activity = new Activity();
        final View root = activity.content.getChildAt(0);
        root.setPadding(16, 16, 16, 16);
        SystemBars.fitContent(activity);
        final WindowInsetsCompat portrait = new WindowInsetsCompat()
                .with(bars, Insets.of(0, 50, 0, 24));
        check(root.listener.onApplyWindowInsets(root, portrait) == WindowInsetsCompat.CONSUMED);
        padding(root, 16, 66, 16, 40);
        root.listener.onApplyWindowInsets(root, portrait);
        padding(root, 16, 66, 16, 40); // Insets must not accumulate on repeated dispatch.

        root.listener.onApplyWindowInsets(root, new WindowInsetsCompat()
                .with(bars, Insets.of(0, 50, 0, 144)).with(ime, Insets.of(0, 0, 0, 1000)));
        padding(root, 16, 66, 16, 1016); // IME and navigation overlap; never add them.
        root.listener.onApplyWindowInsets(root, portrait);
        padding(root, 16, 66, 16, 40);
        root.listener.onApplyWindowInsets(root, new WindowInsetsCompat()
                .with(bars, Insets.of(0, 50, 0, 144)).with(ime, Insets.of(0, 0, 0, 100)));
        padding(root, 16, 66, 16, 160);
        root.listener.onApplyWindowInsets(root, new WindowInsetsCompat()
                .with(bars, Insets.of(0, 0, 48, 0)).with(cutout, Insets.of(70, 0, 0, 0)));
        padding(root, 86, 16, 64, 16);
        root.listener.onApplyWindowInsets(root, new WindowInsetsCompat()
                .with(bars, Insets.of(0, 40, 0, 0)).with(cutout, Insets.of(0, 80, 0, 0)));
        padding(root, 16, 96, 16, 16);

        // The modern WebView path resizes the child separately and only pads for system bars.
        SystemBars.applyInsets(root, new WindowInsetsCompat().with(bars, Insets.of(0, 50, 0, 144))
                .with(ime, Insets.of(0, 0, 0, 1000)), Insets.NONE, false);
        padding(root, 0, 50, 0, 144);
        SystemBars.applyInsets(root, new WindowInsetsCompat(), Insets.NONE, false);
        padding(root, 0, 0, 0, 0);

        for (int sdk : new int[]{26, 28, 29, 35, 36}) {
            Build.VERSION.SDK_INT = sdk;
            SystemBars.configure(activity, false);
            check(!activity.window.fitsSystemWindows && activity.window.navigationColor == 0
                    && activity.window.statusColor == 0);
            check((activity.window.controller.visible & WindowInsetsCompat.Type.navigationBars()) != 0);
            if (sdk >= 29) check(!activity.window.navigationContrast && !activity.window.statusContrast);
            SystemBars.setAppearance(activity, true);
            check(activity.window.controller.lightStatus && activity.window.controller.lightNavigation);
            SystemBars.setAppearance(activity, false);
            check(!activity.window.controller.lightStatus && !activity.window.controller.lightNavigation);
        }

        final int status = WindowInsetsCompat.Type.statusBars();
        final int navigation = WindowInsetsCompat.Type.navigationBars();
        final int caption = WindowInsetsCompat.Type.captionBar();
        for (boolean statusVisible : new boolean[]{true, false}) {
            for (boolean captionVisible : new boolean[]{true, false}) {
                for (boolean navigationVisible : new boolean[]{true, false}) {
                    activity.window.decor.insets = new WindowInsetsCompat();
                    final int visibleTypes = (navigationVisible ? navigation : 0)
                            | (statusVisible ? status : 0) | (captionVisible ? caption : 0);
                    activity.window.decor.insets.visible = visibleTypes;
                    activity.window.controller.visible = visibleTypes;
                    activity.window.controller.behavior = 1;
                    final Runnable restore = SystemBars.hideForVideo(activity);
                    check((activity.window.controller.visible & bars) == 0);
                    SystemBars.setAppearance(activity, true); // Theme changes cannot exit full screen.
                    check((activity.window.controller.visible & bars) == 0);
                    restore.run();
                    check(activity.window.controller.visible == visibleTypes);
                    check(activity.window.controller.behavior == 1);
                }
            }
        }
        check(ViewCompat.requests == 1);
        System.out.println("System bar layout, appearance and fullscreen regression cases passed");
    }

    private static void padding(View view, int left, int top, int right, int bottom) {
        check(view.getPaddingLeft() == left && view.getPaddingTop() == top
                && view.getPaddingRight() == right && view.getPaddingBottom() == bottom);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("System bar regression");
    }
}
