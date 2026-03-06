/*
 * SiYuan - 源于思考,饮水思源
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

/**
 * Application configuration constants.
 * 应用配置常量
 *
 * @author <a href="https://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.0, Jan 27, 2026
 * @since 3.5.5
 */
public final class AppConfig {

    // Network / 网络配置
    public static final int KERNEL_PORT = 6806;
    public static final int DEFAULT_ASYNC_SERVER_PORT = 6906;
    public static final String KERNEL_BASE_URL = "http://127.0.0.1:" + KERNEL_PORT;
    public static final String KERNEL_BOOT_URL = KERNEL_BASE_URL + "/appearance/boot/index.html?v=" + Utils.version;

    // Timeouts / 超时配置
    public static final long KERNEL_BOOT_CHECK_INTERVAL_MS = 10;
    public static final long KEEP_ALIVE_DURATION_MS = 45 * 1000;

    // Paths / 路径配置
    public static final String APP_ZIP = "app.zip";
    public static final String APP_DIR = "/app";
    public static final String APP_VERSION_FILE = "VERSION";
    public static final String DATA_ASSETS_DIR = "data/assets";

    // Colors / 颜色配置
    public static final String DEFAULT_DARK_COLOR = "#1e1e1e";
    public static final String DEFAULT_STATUS_BAR_COLOR = "#1e1e1e";
    public static final String DEFAULT_FALLBACK_COLOR = "#212224";

    // Request codes / 请求代码
    public static final int REQUEST_SELECT_FILE = 100;
    public static final int REQUEST_CAMERA = 101;

    // WebView / WebView 配置
    public static final int MIN_WEBVIEW_VERSION = 95;
    public static final int WEBVIEW_TEXT_ZOOM = 100;
    public static final String USER_AGENT_PREFIX = "SiYuan/";
    public static final String USER_AGENT_SUFFIX = " https://b3log.org/siyuan Android ";

    // API endpoints / API 端点
    public static final String API_WALK_DIR = "/api/walkDir";
    public static final String API_SYNC_PERFORM = KERNEL_BASE_URL + "/api/sync/performSync";

    // JavaScript bridge / JavaScript 桥接
    public static final String JS_INTERFACE_NAME = "JSAndroid";

    // Feature flags / 功能标志
    public static final String FEATURE_AI = "ai";

    private AppConfig() {
        // Prevent instantiation / 防止实例化
    }
}
