/*
 * SiYuan - From thought to insight, with agents
 * Copyright (c) 2020-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.siyuan;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Transparent system bars with native content avoidance, including older Android versions. */
final class SystemBars {
    private SystemBars() {
    }

    @SuppressWarnings("deprecation")
    static void configure(Activity activity, boolean light) {
        final Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
            final WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        setAppearance(activity, light);
        WindowCompat.getInsetsController(window, window.getDecorView()).show(WindowInsetsCompat.Type.navigationBars());
    }

    static void setAppearance(Activity activity, boolean light) {
        final Window window = activity.getWindow();
        final WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
    }

    static Runnable hideForVideo(Activity activity) {
        final Window window = activity.getWindow();
        final WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(window.getDecorView());
        int visibleTypes = 0;
        for (int type : new int[]{WindowInsetsCompat.Type.statusBars(), WindowInsetsCompat.Type.navigationBars(),
                WindowInsetsCompat.Type.captionBar()}) {
            if (insets == null || insets.isVisible(type)) visibleTypes |= type;
        }
        final int restoreVisibleTypes = visibleTypes;
        final int behavior = controller.getSystemBarsBehavior();
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        return () -> {
            controller.setSystemBarsBehavior(behavior);
            controller.show(restoreVisibleTypes);
            controller.hide(WindowInsetsCompat.Type.systemBars() & ~restoreVisibleTypes);
        };
    }

    static void fitContent(Activity activity) {
        final ViewGroup content = activity.findViewById(android.R.id.content);
        final View root = content.getChildAt(0);
        final Insets original = Insets.of(root.getPaddingLeft(), root.getPaddingTop(),
                root.getPaddingRight(), root.getPaddingBottom());
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            applyInsets(view, insets, original, true);
            // All children are inside the safe rectangle; WebView must not apply CSS safe areas again.
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }

    static void applyInsets(View view, WindowInsetsCompat insets, Insets original, boolean includeKeyboard) {
        final int types = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                | (includeKeyboard ? WindowInsetsCompat.Type.ime() : 0);
        final Insets safe = insets.getInsets(types);
        view.setPadding(original.left + safe.left, original.top + safe.top,
                original.right + safe.right, original.bottom + safe.bottom);
    }
}
