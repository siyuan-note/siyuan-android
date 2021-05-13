/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, ld246.com
 *
 * 本文件属于思源笔记源码的一部分，云南链滴科技有限公司版权所有。
 */
package org.b3log.siyuan;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.Display;
import android.view.View;
import android.widget.FrameLayout;

/**
 * 解决输入法弹起时遮挡 WebView 底部.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.0, Feb 19, 2021
 * @since 1.0.0
 */
public final class AndroidBug5497Workaround {
    public static void assistActivity(final Activity activity) {
        new AndroidBug5497Workaround(activity);
    }

    private View mChildOfContent;
    private int usableHeightPrevious;
    private FrameLayout.LayoutParams frameLayoutParams;
    private Activity activity;

    private AndroidBug5497Workaround(final Activity activity) {
        final FrameLayout content = activity.findViewById(android.R.id.content);
        mChildOfContent = content.getChildAt(0);
        mChildOfContent.getViewTreeObserver().addOnGlobalLayoutListener(() -> possiblyResizeChildOfContent());
        frameLayoutParams = (FrameLayout.LayoutParams) mChildOfContent.getLayoutParams();
        this.activity = activity;
    }

    private void possiblyResizeChildOfContent() {
        int usableHeightNow = computeUsableHeight();
        if (usableHeightNow != usableHeightPrevious) {
            int usableHeightSansKeyboard = mChildOfContent.getRootView().getHeight();
            int heightDifference = usableHeightSansKeyboard - usableHeightNow;
            if (heightDifference > (usableHeightSansKeyboard / 4)) {
                frameLayoutParams.height = usableHeightSansKeyboard - heightDifference;
            } else {
                frameLayoutParams.height = usableHeightSansKeyboard;
            }

//            final int height = getNavigationBarHeight();
//            frameLayoutParams.height -= height;
            mChildOfContent.requestLayout();
            usableHeightPrevious = usableHeightNow;
        }
    }

    private int computeUsableHeight() {
        final Rect r = new Rect();
        mChildOfContent.getWindowVisibleDisplayFrame(r);
        return (r.bottom - r.top);
    }

    private int getNavigationBarHeight() {
        if (!isNavigationBarShow()) {
            return 0;
        }
        final Resources resources = activity.getResources();
        final int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        final int height = resources.getDimensionPixelSize(resourceId);
        return height;
    }

    private boolean isNavigationBarShow() {
        final Display display = activity.getWindowManager().getDefaultDisplay();
        final Point size = new Point();
        final Point realSize = new Point();
        display.getSize(size);
        display.getRealSize(realSize);
        return realSize.y != size.y;
    }
}