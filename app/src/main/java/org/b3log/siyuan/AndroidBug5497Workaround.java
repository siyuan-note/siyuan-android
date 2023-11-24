package org.b3log.siyuan;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.blankj.utilcode.util.BarUtils;

import java.util.concurrent.Callable;

/**
 * Android small window mode soft keyboard black occlusion https://github.com/siyuan-note/siyuan-android/pull/7
 *
 * @author <a href="https://issuetracker.google.com/issues/36911528#comment100">al...@tutanota.com</a>
 * @author <a href="https://github.com/Zuoqiu-Yingyi>Yingyi</a>
 * @version 1.0.0.0, Nov 24, 2023
 * @since 2.11.0
 */
public class AndroidBug5497Workaround {

    public static boolean isInMultiWindowMode = false;

    public static void assistActivity(Activity activity) {
        new AndroidBug5497Workaround(activity);
    }

    private int windowMode = 0;
    private boolean resize = false;
    private int usableHeight = 0;
    private int rootViewHeight = 0;
    private final Activity activity;
    private final FrameLayout frameLayout;
    private final View view;
    private final FrameLayout.LayoutParams frameLayoutParams;

    private AndroidBug5497Workaround(Activity activity) {
        this.activity = activity;
        this.frameLayout = (FrameLayout) this.activity.findViewById(android.R.id.content);
        this.view = this.frameLayout.getChildAt(0);
        this.frameLayout.getViewTreeObserver().addOnGlobalLayoutListener(() -> this.possiblyResizeChildOfContent());
        this.frameLayoutParams = (FrameLayout.LayoutParams) (view.getLayoutParams());
    }

    private final void possiblyResizeChildOfContent() {
        final int usableHeight = this.computeUsableHeight();
        final int rootViewHeight = this.getRootViewHeight();
        final int rootViewWidth = this.getRootViewWidth();
        final Rect rect = this.getVisibleRect();
        // logInfo();
        if (usableHeight != this.usableHeight || rootViewHeight != this.rootViewHeight) {
            this.resize = false;
            final DisplayMetrics displayMetrics = this.getDisplayMetrics();
            final int frameHeight = this.frameLayoutParams.height;
            final int statusBarHeight = BarUtils.getStatusBarHeight();
            final int navBarHeight = this.getNavigationBarHeight();

            if (!this.activity.isInMultiWindowMode()) {
                // Full-window
                this.windowMode = 000;
                this.frameLayoutParams.height = -1;
                if (rect.bottom != rootViewHeight) {
                    // Keyboard-on
                    // Log.d("5497-status", "Full-window Keyboard-on");
                } else {
                    // Keyboard-off
                    // Log.d("5497-status", "Full-window Keyboard-off");
                }
            } else {
                // Mult-window
                this.windowMode = 100;
                if (statusBarHeight < rect.top && rootViewHeight != this.view.getHeight() && rootViewHeight == rect.height() + statusBarHeight) {
                    // Small-window
                    // Log.d("5497-status", "Mult-window Small-window");
                    this.resize = true;
                    this.windowMode += 00;
                    this.frameLayoutParams.height = -1;
                } else if (statusBarHeight == rect.top) {
                    // Split-screen-portrait-top & Split-screen-landscape
                    this.windowMode += 10;
                    if (rootViewWidth == displayMetrics.widthPixels) {
                        // Split-screen-portrait
                        // Log.d("5497-status", "Mult-window Split-screen-portrait-top");
                    } else {
                        // Split-screen-landscape
                        // Log.d("5497-status", "Mult-window Split-screen-landscape");
                    }
                    if (rect.bottom < this.view.getBottom()) {
                        this.frameLayoutParams.height = rect.bottom;
                    } else {
                        this.frameLayoutParams.height = -1;
                    }
                } else {
                    // Split-screen-portrait-bottom
                    // Log.d("5497-status", "Mult-window Split-screen-portrait-bottom");
                    this.windowMode += 20;
                    this.frameLayoutParams.height = rect.height();
                }
            }
            this.view.requestLayout();
            this.usableHeight = usableHeight;
            this.rootViewHeight = rootViewHeight;
        } else if (this.resize) {
            // Log.d("5497-status", "windowMode: " + this.windowMode);
            switch (this.windowMode) {
                case 100:
                    if (this.frameLayoutParams.height != -1) {
                        this.frameLayoutParams.height = -1;
                        this.view.requestLayout();
                    } else {
                        this.resize = false;
                    }
                    break;
            }
        }
    }

    private final void logInfo() {
        final Rect rect = this.getVisibleRect();
        Log.d("5497", "rect.top: " + rect.top + ", rect.bottom: " + rect.bottom + ", rect.height(): " + rect.height() + ", rect.width(): " + rect.width());

        Log.d("5497", "view.top: " + this.view.getTop() + ", view.bottom: " + this.view.getBottom() + ", view.height(): " + this.view.getHeight() + ", view.width(): " + this.view.getWidth());

        final int rootViewHeight = this.getRootViewHeight();
        final int rootViewWidth = this.getRootViewWidth();
        Log.d("5497", "rootViewHeight: " + rootViewHeight + ", rootViewWidth: " + rootViewWidth);

        final DisplayMetrics display = this.getDisplayMetrics();
        Log.d("5497", "display.heightPixels: " + display.heightPixels + ", display.widthPixels: " + display.widthPixels);

        Log.d("5497", "frameLayoutParams.height: " + frameLayoutParams.height);

        final int navigationBarHeight = this.getNavigationBarHeight();
        Log.d("5497", "navigationBarHeight: " + navigationBarHeight);

        Log.d("5497", "StatusBarHeight: " + BarUtils.getStatusBarHeight());
        Log.d("5497", "NavBarHeight: " + BarUtils.getNavBarHeight());
    }

    private final int computeUsableHeight() {
        final Rect rect = getVisibleRect();
        return rect.height();
    }

    private final Rect getVisibleRect() {
        final Rect rect = new Rect();
        this.view.getWindowVisibleDisplayFrame(rect);
        return rect;
    }

    private final int getRootViewHeight() {
        return this.view.getRootView().getHeight();
    }

    private final int getRootViewWidth() {
        return this.view.getRootView().getWidth();
    }

    private final DisplayMetrics getDisplayMetrics() {
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        this.activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        return displayMetrics;
    }

    @SuppressLint({"DiscouragedApi", "InternalInsetResource"})
    private final int getNavigationBarHeight() {
        final Context context = this.view.getContext();
        final boolean hasMenuKey = ViewConfiguration.get(context).hasPermanentMenuKey();
        if (!hasMenuKey) {
            final int resourceId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            return resourceId > 0
                    ? context.getResources().getDimensionPixelSize(resourceId)
                    : 0;
        } else {
            return 0;
        }
    }
}
