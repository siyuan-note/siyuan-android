package org.b3log.siyuan;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import com.blankj.utilcode.util.BarUtils;

/**
 * Android small window mode soft keyboard black occlusion <a href="https://github.com/siyuan-note/siyuan-android/pull/7">siyuan-note/siyuan-android#7</a>
 *
 * @author <a href="https://issuetracker.google.com/issues/36911528#comment100">al...@tutanota.com</a>
 * @author <a href="https://github.com/Zuoqiu-Yingyi">Yingyi</a>
 * @author <a href="https://88250.b3log.org">Liang Ding</a>
 * @version 1.0.1.0, Aug 27, 2025
 * @since 2.11.0
 */
public class AndroidBug5497Workaround {

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
        this.frameLayout = this.activity.findViewById(android.R.id.content);
        this.view = this.frameLayout.getChildAt(0);
        this.frameLayoutParams = (FrameLayout.LayoutParams) (this.view.getLayoutParams());

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S_V2) {
            registerKeyboardInsets();
        } else {
            this.view.setOnApplyWindowInsetsListener((v, insets) -> {
                int imeHeight = 0;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    imeHeight = insets.getInsets(WindowInsets.Type.ime()).bottom;
                }
                frameLayoutParams.height = imeHeight > 0 ? frameLayout.getHeight() - imeHeight : -1;
                view.requestLayout();
                return v.onApplyWindowInsets(insets);
            });
        }

        // 兼容旧逻辑
        this.frameLayout.getViewTreeObserver().addOnGlobalLayoutListener(this::possiblyResizeChildOfContent);
    }

    @TargetApi(android.os.Build.VERSION_CODES.S_V2)
    private void registerKeyboardInsets() {
        final FrameLayout container = (FrameLayout) this.view;
        // 高度由窗口边衬统一处理，关闭沉浸式状态栏库对父容器的重复缩放。
        com.zackratos.kblistener.kblistener.ViewKt.onKeyboardOpen(container, null);
        com.zackratos.kblistener.kblistener.ViewKt.onKeyboardClose(container, null);
        final WindowInsets[] currentInsets = new WindowInsets[1];
        this.view.setOnApplyWindowInsetsListener((v, insets) -> {
            // 在键盘动画开始前按目标高度布局，确保退场时露出的是已恢复的页面。
            currentInsets[0] = insets;
            resizeForKeyboard(container, insets);
            final View webView = activity.findViewById(R.id.webView);
            if (webView instanceof android.webkit.WebView) {
                Utils.onKeyboardVisibilityChanged(activity, (android.webkit.WebView) webView,
                        insets.isVisible(WindowInsets.Type.ime()));
            }
            return v.onApplyWindowInsets(insets);
        });
        container.addOnLayoutChangeListener((v, left, top, right, bottom,
                                             oldLeft, oldTop, oldRight, oldBottom) -> {
            if (currentInsets[0] != null) {
                resizeForKeyboard(container, currentInsets[0]);
            }
        });
    }

    @TargetApi(android.os.Build.VERSION_CODES.S_V2)
    private void resizeForKeyboard(FrameLayout container, WindowInsets insets) {
        // 页面加载失败时可能替换 WebView，每次布局都获取当前实例。
        final View webView = activity.findViewById(R.id.webView);
        if (webView == null || webView.getParent() != container) {
            return;
        }
        final int contentHeight = container.getHeight() - container.getPaddingTop() - container.getPaddingBottom();
        if (contentHeight <= 0) {
            return;
        }
        final int[] location = new int[2];
        container.getLocationInWindow(location);
        // 背景容器始终铺满窗口，仅调整 WebView，并扣除状态栏占位和键盘实际重叠区域。
        final int height = KeyboardContentHeight.calculate(contentHeight, location[1] + container.getPaddingTop(),
                container.getRootView().getHeight(), insets.getInsets(WindowInsets.Type.ime()).bottom);
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) webView.getLayoutParams();
        if (params.height != height) {
            params.height = height;
            webView.requestLayout();
        }
    }

    private void possiblyResizeChildOfContent() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S_V2) {
            // Android 12L（32）及以上用 WindowInsets 处理。
            return;
        }

        final int usableHeight = this.computeUsableHeight();
        final int rootViewHeight = this.getRootViewHeight();
        // logInfo();
        if (usableHeight != this.usableHeight || rootViewHeight != this.rootViewHeight) {
            this.resize = false;

            if (this.activity.isInMultiWindowMode()) {
                // Mult-window
                this.resize = true;
                this.windowMode = 100;
                this.frameLayoutParams.height = -1;
            } else {
                // Full-window
                this.windowMode = 000;
                this.frameLayoutParams.height = -1;
            }

            this.view.requestLayout();
            this.usableHeight = usableHeight;
            this.rootViewHeight = rootViewHeight;
        } else if (this.resize) {
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

    private void logInfo() {
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

    private int computeUsableHeight() {
        final Rect rect = getVisibleRect();
        return rect.height();
    }

    private Rect getVisibleRect() {
        final Rect rect = new Rect();
        this.view.getWindowVisibleDisplayFrame(rect);
        return rect;
    }

    private int getRootViewHeight() {
        return this.view.getRootView().getHeight();
    }

    private int getRootViewWidth() {
        return this.view.getRootView().getWidth();
    }

    private DisplayMetrics getDisplayMetrics() {
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        this.activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        return displayMetrics;
    }

    @SuppressLint({"DiscouragedApi", "InternalInsetResource"})
    private int getNavigationBarHeight() {
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
