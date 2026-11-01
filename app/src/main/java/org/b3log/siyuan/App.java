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

import android.app.Application;
import android.app.ActivityManager;
import android.os.Build;
import android.os.Process;
import androidx.webkit.ProcessGlobalConfig;
import androidx.webkit.WebViewFeature;

import com.blankj.utilcode.util.Utils;

/**
 * SiYuan Application.
 *
 * @author <a href="https://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.0, Feb 23, 2022
 * @since 1.0.0
 */
public class App extends Application {
    private static boolean markdownWebViewReady;

    public static boolean isMarkdownWebViewReady() { return markdownWebViewReady; }

    private String currentProcessName() {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName();
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager != null && manager.getRunningAppProcesses() != null) {
            for (ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses()) {
                if (process.pid == Process.myPid()) return process.processName;
            }
        }
        return "";
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if ((getPackageName() + ":markdown").equals(currentProcessName())) {
            // Configure before any utility or Activity can initialize WebView.
            // A provider without separate data directories cannot safely run both processes.
            try {
                if (WebViewFeature.isStartupFeatureSupported(this, WebViewFeature.STARTUP_FEATURE_SET_DATA_DIRECTORY_SUFFIX)) {
                    ProcessGlobalConfig.apply(new ProcessGlobalConfig().setDataDirectorySuffix(this, "markdown"));
                    markdownWebViewReady = true;
                }
            } catch (RuntimeException ignored) { /* The lightweight Activity shows its localized load error. */ }
        }
        Utils.init(this);
    }
}
