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
package org.b3log.siyuan.config;

import org.b3log.siyuan.util.Utils;

/**
 * Application configuration constants.
 * 应用配置常量
 *
 * @version 1.0.0.0, Mar 6, 2026
 */
public final class AppConfig {

    // Network / 网络配置
    public static final int KERNEL_PORT = 6806;
    public static final int DEFAULT_ASYNC_SERVER_PORT = 6906;
    public static final String KERNEL_BASE_URL = "http://127.0.0.1:" + KERNEL_PORT;
    public static final String KERNEL_BOOT_URL = KERNEL_BASE_URL + "/appearance/boot/index.html?v=" + Utils.version;

    // Timeouts / 超时配置
    public static final long KEEP_ALIVE_DURATION_MS = 45 * 1000;

    // Colors / 颜色配置
    public static final String DEFAULT_STATUS_BAR_COLOR = "#1e1e1e";

    // Request codes / 请求代码
    public static final int REQUEST_SELECT_FILE = 100;
    public static final int REQUEST_CAMERA = 101;

    // API endpoints / API 端点
    public static final String API_WALK_DIR = "/api/walkDir";
    public static final String API_SYNC_PERFORM = KERNEL_BASE_URL + "/api/sync/performSync";

    // JavaScript bridge / JavaScript 桥接
    public static final String JS_INTERFACE_NAME = "JSAndroid";

    private AppConfig() {
        // Prevent instantiation / 防止实例化
    }
}
