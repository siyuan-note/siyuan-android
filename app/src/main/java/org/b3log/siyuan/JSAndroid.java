/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, ld246.com
 *
 * 本文件属于思源笔记源码的一部分，云南链滴科技有限公司版权所有。
 */
package org.b3log.siyuan;

import android.content.Intent;
import android.net.Uri;
import android.os.StrictMode;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;

import org.apache.commons.io.FilenameUtils;

import java.lang.reflect.Method;

/**
 * JavaScript 接口.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.1, Jul 24, 2021
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
    public void openExternal(final String url) {
        if (!url.startsWith("http://127.0.0.1")) {
            final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(browserIntent);
            return;
        }

        try {
            final Method m = StrictMode.class.getMethod("disableDeathOnFileUriExposure");
            m.invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        final MimeTypeMap map = MimeTypeMap.getSingleton();
        final String ext = FilenameUtils.getExtension(url);
        String type = map.getMimeTypeFromExtension(ext);
        if (type == null) {
            type = "application/pdf";
        }
        final Uri uri = Uri.parse(url);
        final Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
    }
}
