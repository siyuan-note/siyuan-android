/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, ld246.com
 *
 * 本文件属于思源笔记源码的一部分，云南链滴科技有限公司版权所有。
 */
package org.b3log.siyuan;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidk.Androidk;

/**
 * 仓库同步.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.0, Feb 19, 2020
 * @since 1.0.0
 */
public final class Repo {
    private Activity activity;

    public Repo(final Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void sync() {
        try {
            Androidk.repoSync();

            final WebView webView = ((MainActivity) activity).webView;
            webView.post(webView::reload);
        } catch (final Exception e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}