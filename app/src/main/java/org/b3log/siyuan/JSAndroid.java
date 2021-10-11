/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, ld246.com
 *
 * 本文件属于思源笔记源码的一部分，云南链滴科技有限公司版权所有。
 */
package org.b3log.siyuan;

import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;

/**
 * JavaScript 接口.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.1.0, Oct 11, 2021
 * @since 1.0.0
 */
public final class JSAndroid {
    private MainActivity activity;

    public JSAndroid(final MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void returnDesktop() {
        activity.moveTaskToBack(true);
    }

    @JavascriptInterface
    public void openExternal(String url) {
        if (url.startsWith("assets/")) {
            url = "http://127.0.0.1:6806/" + url;
        }

        final Uri uri = Uri.parse(url);
        final Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
        activity.startActivity(browserIntent);
    }
}
