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
 * @author <a href="https://issuetracker.google.com/issues/36911528#comment100">al...@tutanota.com</a>
 * @version 1.0.0, May 26, 2022
 * @since 1.0.0
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
        final Rect rect = this.getVisibleRect();
        // logInfo();
        if (this.resize || usableHeight != this.usableHeight || rootViewHeight != this.rootViewHeight) {
            final int frameHeight = this.frameLayoutParams.height;
            final int statusBarHeight = BarUtils.getStatusBarHeight();
            final int navBarHeight = this.getNavigationBarHeight();

            if (AndroidBug5497Workaround.isInMultiWindowMode) {
                // Mult window
                this.windowMode = 100;
                if (statusBarHeight == rect.top) {
                    // Top split screen
                    this.windowMode += 10;
                    if (rect.bottom < rootViewHeight) {
                        // Keyboard on
                        this.windowMode += 1;
                        this.frameLayoutParams.height = rect.bottom;
                        // Log.d("5497-status", "Mult-window Top-split-screen Keyboard-on");
                    } else {
                        // Keyboard off
                        this.windowMode += 0;
                        this.frameLayoutParams.height = rootViewHeight;
                        // Log.d("5497-status", "Mult-window Top-split-screen Keyboard-off");
                    }
                } else if (rootViewHeight == rect.height() + statusBarHeight) {
                    // Small window
                    this.resize = !this.resize;
                    this.windowMode += 30;
                    this.frameLayoutParams.height = -1;
                    // Log.d("5497-status", "Mult-window Small-window");
                } else {
                    // Bottom split screen
                    this.windowMode += 20;
                    if (rect.height() != rootViewHeight) {
                        // Keyboard on
                        this.windowMode += 1;
                        this.frameLayoutParams.height = rect.height();
                        // Log.d("5497-status", "Mult-window Bottom-split-screen Keyboard-on");
                    } else {
                        // Keyboard off
                        this.windowMode += 0;
                        this.frameLayoutParams.height = rootViewHeight;
                        // Log.d("5497-status", "Mult-window Bottom-split-screen Keyboard-off");
                    }
                }
            } else {
                // Full window
                this.windowMode = 000;
                this.frameLayoutParams.height = -1;
                if (rect.bottom != rootViewHeight) {
                    // Keyboard on
                    this.windowMode += 1;
                    // Log.d("5497-status", "Full-window Keyboard-on");
                } else {
                    // Keyboard off
                    this.windowMode += 0;
                    // Log.d("5497-status", "Full-window Keyboard-off");
                }
            }
            this.view.requestLayout();
            this.usableHeight = usableHeight;
            this.rootViewHeight = rootViewHeight;
        }
    }

    private final void logInfo() {
        final Rect rect = this.getVisibleRect();
        Log.d("5497", "rect.top: " + rect.top + ", rect.bottom: " + rect.bottom + ", rect.height(): " + rect.height() + ", rect.width(): " + rect.width());

        Log.d("5497", "view.top: " + this.view.getTop() + ", view.bottom: " + this.view.getBottom() + ", view.height(): " + this.view.getHeight() + ", view.width(): " + this.view.getWidth());

        final int rootViewHeight = this.view.getRootView().getHeight();
        Log.d("5497", "rootViewHeight: " + rootViewHeight);

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
